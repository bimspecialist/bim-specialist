package com.bimspecialist.measure3d.core

import kotlin.math.abs

data class TriangleFace(val a: Int, val b: Int, val c: Int)

data class SolidMesh(
    val vertices: List<Vec3>,
    val faces: List<TriangleFace>,
    val footprintAreaSquareMetres: Double,
    val extrudedMetres: Double
) {
    fun signedVolumeCubicMetres(): Double = faces.sumOf { f ->
        vertices[f.a].dot(vertices[f.b].cross(vertices[f.c])) / 6.0
    }
}

object Extrusion {
    // A manufactured solid from a measured outline. This is NOT reconstruction of a scanned object.
    fun fromPolygon(outline: List<Vec3>, heightMetres: Double, reverse: Boolean = false): SolidMesh {
        require(heightMetres.isFinite() && heightMetres in 0.001..30.0) {
            "Extrusion height must be between 1 mm and 30 m"
        }
        val area = Geometry.planarArea(outline, tolerance = 0.01)
        val count = outline.size
        val origin = outline[0]
        val normalRaw = (1 until count - 1)
            .map { (outline[it] - origin).cross(outline[it + 1] - origin) }
            .first { it.norm() > 1e-8 }
        val normal = normalRaw * (1.0 / normalRaw.norm())
        val displacement = normal * (if (reverse) -heightMetres else heightMetres)
        val vertices = outline + outline.map { it + displacement }

        val coords = outline.map { p ->
            when {
                abs(normal.x) >= abs(normal.y) && abs(normal.x) >= abs(normal.z) -> Pair(p.y, p.z)
                abs(normal.y) >= abs(normal.z) -> Pair(p.x, p.z)
                else -> Pair(p.x, p.y)
            }
        }
        fun cross2(a: Int, b: Int, c: Int): Double {
            val p = coords[a]; val q = coords[b]; val r = coords[c]
            return (q.first - p.first) * (r.second - p.second) -
                (q.second - p.second) * (r.first - p.first)
        }
        val signedArea2 = coords.indices.sumOf { i ->
            val a = coords[i]; val b = coords[(i + 1) % count]
            a.first * b.second - b.first * a.second
        }
        require(abs(signedArea2) > 1e-9) { "Degenerate polygon projection" }
        val orientation = if (signedArea2 > 0) 1 else -1

        fun pointInTriangle(p: Int, a: Int, b: Int, c: Int): Boolean {
            val s1 = cross2(a, b, p) * orientation
            val s2 = cross2(b, c, p) * orientation
            val s3 = cross2(c, a, p) * orientation
            return s1 >= -1e-10 && s2 >= -1e-10 && s3 >= -1e-10
        }

        val remaining = (0 until count).toMutableList()
        val ears = mutableListOf<TriangleFace>()
        while (remaining.size > 3) {
            var found = false
            for (i in remaining.indices) {
                val a = remaining[(i + remaining.size - 1) % remaining.size]
                val b = remaining[i]
                val c = remaining[(i + 1) % remaining.size]
                if (cross2(a, b, c) * orientation <= 1e-10) continue
                if (remaining.any { it != a && it != b && it != c && pointInTriangle(it, a, b, c) }) continue
                ears += TriangleFace(a, b, c)
                remaining.removeAt(i)
                found = true
                break
            }
            require(found) { "Unable to triangulate polygon; remove near-collinear vertices" }
        }
        ears += TriangleFace(remaining[0], remaining[1], remaining[2])

        val faces = ArrayList<TriangleFace>(4 * count - 4)
        for (ear in ears) {
            // Ear clipping is oriented in the 2D projection. Restore the input's 3D winding.
            val a = ear.a; var b = ear.b; var c = ear.c
            if ((outline[b] - outline[a]).cross(outline[c] - outline[a]).dot(normal) < 0) {
                val old = b; b = c; c = old
            }
            if (!reverse) {
                faces += TriangleFace(c, b, a)
                faces += TriangleFace(a + count, b + count, c + count)
            } else {
                faces += TriangleFace(a, b, c)
                faces += TriangleFace(c + count, b + count, a + count)
            }
        }
        for (i in 0 until count) {
            val next = (i + 1) % count
            if (!reverse) {
                faces += TriangleFace(i, next, next + count)
                faces += TriangleFace(i, next + count, i + count)
            } else {
                faces += TriangleFace(i, next + count, next)
                faces += TriangleFace(i, i + count, next + count)
            }
        }
        return SolidMesh(vertices, faces, area, heightMetres)
    }

    private operator fun Vec3.times(scalar: Double) = Vec3(x * scalar, y * scalar, z * scalar)
}
