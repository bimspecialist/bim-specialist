package com.bimspecialist.measure3d.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProfessionalEngineeringTests {
    private val rectangle = listOf(
        Vec3(0.0, 0.0, 0.0),
        Vec3(3.0, 0.0, 0.0),
        Vec3(3.0, 2.0, 0.0),
        Vec3(0.0, 2.0, 0.0)
    )

    private fun isWatertight(mesh: SolidMesh): Boolean {
        val edges = mutableMapOf<Pair<Int, Int>, Int>()
        for (f in mesh.faces) {
            val triples = listOf(f.a to f.b, f.b to f.c, f.c to f.a)
            for ((a, b) in triples) {
                if (a == b) return false
                val key = minOf(a, b) to maxOf(a, b)
                edges[key] = (edges[key] ?: 0) + 1
            }
        }
        return edges.isNotEmpty() && edges.values.all { it == 2 }
    }

    @Test fun extrudesRectangleToClosedTwelveTriangleSolid() {
        val mesh = Extrusion.fromPolygon(rectangle, 0.25)
        assertEquals(8, mesh.vertices.size)
        assertEquals(12, mesh.faces.size)
        assertTrue(isWatertight(mesh))
        assertEquals(1.5, mesh.signedVolumeCubicMetres(), 1e-8)
        assertEquals(6.0, mesh.footprintAreaSquareMetres, 1e-8)
    }

    @Test fun reverseExtrusionHasPositiveOrientedVolume() {
        val mesh = Extrusion.fromPolygon(rectangle, 0.25, reverse = true)
        assertTrue(isWatertight(mesh))
        assertEquals(1.5, mesh.signedVolumeCubicMetres(), 1e-8)
    }

    @Test fun concaveOutlineExtrudesWithoutFakeConvexCap() {
        val shape = listOf(
            Vec3(0.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 1.0, 0.0), Vec3(1.0, 1.0, 0.0),
            Vec3(1.0, 3.0, 0.0), Vec3(0.0, 3.0, 0.0)
        )
        val mesh = Extrusion.fromPolygon(shape, 1.5)
        assertEquals(20, mesh.faces.size)
        assertTrue(isWatertight(mesh))
        assertEquals(5.0 * 1.5, mesh.signedVolumeCubicMetres(), 1e-8)
    }

    @Test fun verticalWallMayBeExtruded() {
        val wall = listOf(
            Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 4.0),
            Vec3(0.0, 2.0, 4.0), Vec3(0.0, 2.0, 0.0)
        )
        val mesh = Extrusion.fromPolygon(wall, 0.15)
        assertTrue(isWatertight(mesh))
        assertEquals(1.2, mesh.signedVolumeCubicMetres(), 1e-8)
    }

    @Test fun invalidExtrusionHeightRejected() {
        for (height in listOf(0.0, -1.0, 31.0, Double.NaN)) {
            try {
                Extrusion.fromPolygon(rectangle, height)
                fail("height should not pass: " + height)
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun selfIntersectingExtrusionRejected() {
        val crossing = listOf(
            Vec3(0.0,0.0,0.0), Vec3(2.0,2.0,0.0),
            Vec3(0.0,2.0,0.0), Vec3(2.0,0.0,0.0)
        )
        try {
            Extrusion.fromPolygon(crossing, 0.1)
            fail("Expected polygon validation")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun stlBinaryUsesMillimetresAndCorrectTriangleCount() {
        val mesh = Extrusion.fromPolygon(rectangle, 0.25)
        val stl = MeshExports.stlMillimetres(mesh)
        assertEquals(84 + 50 * mesh.faces.size, stl.size)
        val bb = ByteBuffer.wrap(stl).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12, bb.getInt(80))
        // All vertices should be in millimetres, with max footprint dimensions 3000 x 2000.
        var maxX = Float.NEGATIVE_INFINITY
        for (i in 0 until mesh.faces.size) {
            val offset = 84 + i * 50
            for (j in 0..2) {
                val x = bb.getFloat(offset + 12 + j * 12)
                maxX = maxOf(maxX, x)
            }
        }
        assertEquals(3000.0f, maxX, 0.001f)
    }

    @Test fun objFileContainsMillimetreUnitsAndTriangleFaces() {
        val mesh = Extrusion.fromPolygon(rectangle, 0.5)
        val obj = MeshExports.objMillimetres(mesh).toString(Charsets.US_ASCII)
        assertTrue(obj.contains("# Coordinate unit: millimetres"))
        assertEquals(8, obj.lineSequence().count { it.startsWith("v ") })
        assertEquals(12, obj.lineSequence().count { it.startsWith("f ") })
        assertTrue(obj.contains("3000.000000"))
    }

    @Test fun referenceValidationReportsRelativeErrorWithoutCalibration() {
        val comparison = ReferenceCheck.compare(1.02, 1.0)
        assertEquals(0.02, comparison.errorMetres, 1e-10)
        assertEquals(2.0, comparison.errorPercent, 1e-8)
    }

    @Test fun zeroTapeLengthRejected() {
        try {
            ReferenceCheck.compare(0.9, 0.0)
            fail("Reference must be nonzero")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun pointCloudStatistics() {
        val stats = CloudAnalysis.compute(listOf(
            Vec3(-1.0, 2.0, 0.0),
            Vec3(3.0, 5.5, 2.0)
        ))!!
        assertEquals(2, stats.count)
        assertEquals(4.0, stats.spanX, 1e-10)
        assertEquals(3.5, stats.spanY, 1e-10)
        assertEquals(2.0, stats.spanZ, 1e-10)
        assertNull(CloudAnalysis.compute(emptyList()))
    }
}
