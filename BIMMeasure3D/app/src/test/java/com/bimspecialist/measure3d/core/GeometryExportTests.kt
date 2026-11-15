package com.bimspecialist.measure3d.core

import org.junit.Assert.*
import org.junit.Test

class GeometryExportTests {
    private val rectangle = listOf(
        Vec3(0.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0),
        Vec3(3.0, 2.0, 0.0), Vec3(0.0, 2.0, 0.0)
    )

    @Test fun lengthIsThreeFourFive() {
        assertEquals(5.0, Geometry.length(Vec3(0.0, 0.0, 0.0), Vec3(3.0, 4.0, 0.0)), 1e-9)
    }

    @Test fun rectangularFloorArea() {
        assertEquals(6.0, Geometry.planarArea(rectangle), 1e-9)
    }

    @Test fun rectangularPerimeter() {
        assertEquals(10.0, Geometry.perimeter(rectangle, closed = true), 1e-9)
    }

    @Test fun openPolylineLength() {
        assertEquals(5.0, Geometry.perimeter(rectangle.take(2) + Vec3(3.0, 2.0, 0.0), closed = false), 1e-9)
    }

    @Test fun verticalWallArea() {
        val wall = listOf(
            Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 4.0),
            Vec3(0.0, 2.0, 4.0), Vec3(0.0, 2.0, 0.0)
        )
        assertEquals(8.0, Geometry.planarArea(wall), 1e-9)
    }

    @Test fun selfCrossingAreaRejected() {
        val crossing = listOf(
            Vec3(0.0, 0.0, 0.0), Vec3(2.0, 2.0, 0.0),
            Vec3(0.0, 2.0, 0.0), Vec3(2.0, 0.0, 0.0)
        )
        try {
            Geometry.planarArea(crossing)
            fail("Expected self-intersection rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun nonplanarAreaRejected() {
        val warped = listOf(
            Vec3(0.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0),
            Vec3(2.0, 2.0, 0.2), Vec3(0.0, 2.0, 0.0)
        )
        try {
            Geometry.planarArea(warped)
            fail("Expected plane check rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun collinearAreaRejected() {
        try {
            Geometry.planarArea(listOf(
                Vec3(0.0,0.0,0.0), Vec3(1.0,0.0,0.0), Vec3(2.0,0.0,0.0)
            ))
            fail("Expected collinear points to be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun unitsConversion() {
        assertEquals("150.0 cm", MeasureFormat.distance(1.5, DisplayUnit.CENTIMETRE))
        assertEquals("20000.0 cm²", MeasureFormat.area(2.0, DisplayUnit.CENTIMETRE))
        assertEquals("1250.0 mm", MeasureFormat.distance(1.25, DisplayUnit.MILLIMETRE))
    }

    @Test fun dxfLineCountsAndUnits() {
        val open = Exports.dxf(rectangle.take(2), closed = false).toString(Charsets.US_ASCII)
        assertEquals(1, "0\\nLINE\\n".toRegex().findAll(open).count())
        assertTrue(open.contains("\u0024INSUNITS\n70\n4"))
        assertTrue(open.contains("3000.000000"))
        assertTrue(open.endsWith("0\nEOF\n"))
        val closed = Exports.dxf(rectangle, closed = true).toString(Charsets.US_ASCII)
        assertEquals(4, "0\\nLINE\\n".toRegex().findAll(closed).count())
    }

    @Test fun plyHasCountAndCoordinates() {
        val ply = Exports.ply(rectangle).toString(Charsets.US_ASCII)
        assertTrue(ply.startsWith("ply\nformat ascii 1.0\n"))
        assertTrue(ply.contains("element vertex 4"))
        assertTrue(ply.contains("3.000000 2.000000 0.000000"))
    }

    @Test fun xyzContainsNoHeader() {
        val xyz = Exports.xyz(listOf(Vec3(1.0, 2.0, 3.0))).toString(Charsets.US_ASCII)
        assertEquals("1.000000 2.000000 3.000000\n", xyz)
    }

    @Test fun csvKeepsMetricFieldsSeparate() {
        val csv = Exports.measurementCsv("AREA", rectangle, 6.0, 10.0).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("kind,length_m,area_m2,perimeter_m"))
        assertTrue(csv.contains("AREA,,6.000000,10.000000,1,"))
    }

    @Test fun pointSamplerIgnoresWeakAndDuplicatePoints() {
        val s = PointSampler(voxelMeters = 0.02, maxPoints = 2)
        s.add(Vec3(0.0, 0.0, 0.0), 0.4f)
        assertEquals(0, s.count())
        s.add(Vec3(0.001, 0.0, 0.0), 0.9f)
        s.add(Vec3(0.015, 0.0, 0.0), 0.9f)
        assertEquals(1, s.count())
        s.add(Vec3(0.1, 0.0, 0.0), 0.95f)
        assertTrue(s.isFull())
        s.add(Vec3(0.4, 0.0, 0.0), 0.95f)
        assertEquals(2, s.count())
        s.clear()
        assertEquals(0, s.count())
    }

    @Test fun arProjectionWithIdentityMatrices() {
        val identity = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        assertEquals(ScreenPoint(50f, 40f),
            ArProjection.worldToScreen(Vec3(0.0,0.0,0.0), identity, identity, 100, 80))
        assertNull(ArProjection.worldToScreen(Vec3(2.0,0.0,0.0), identity, identity, 100, 80))
        assertNull(ArProjection.worldToScreen(Vec3(0.0,0.0,0.0), identity, identity, 0, 80))
    }
}
