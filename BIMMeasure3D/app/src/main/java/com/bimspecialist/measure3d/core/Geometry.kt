package com.bimspecialist.measure3d.core

import kotlin.math.abs
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    fun dot(p: Vec3) = x * p.x + y * p.y + z * p.z
    fun cross(p: Vec3) = Vec3(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x)
    fun norm() = sqrt(dot(this))
    fun distanceTo(p: Vec3) = (this - p).norm()
    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
}

object Geometry {
    fun length(a: Vec3, b: Vec3): Double {
        require(a.isFinite() && b.isFinite()) { "Invalid coordinates" }
        return a.distanceTo(b)
    }

    fun perimeter(points: List<Vec3>, closed: Boolean): Double {
        require(points.size >= 2 && points.all { it.isFinite() })
        return (0 until (if (closed) points.size else points.size - 1)).sumOf { i ->
            length(points[i], points[(i + 1) % points.size])
        }
    }

    fun planarArea(points: List<Vec3>, tolerance: Double = 0.04): Double {
        require(points.size in 3..64) { "3 to 64 points required" }
        require(tolerance >= 0.0 && tolerance.isFinite())
        require(points.all { it.isFinite() }) { "Invalid coordinates" }
        require(points.indices.none { i ->
            points[i].distanceTo(points[(i + 1) % points.size]) < 0.001
        }) { "Overlapping points" }

        val origin = points[0]
        val vectors = points.map { it - origin }
        val normal = (1 until points.size - 1).map { vectors[it].cross(vectors[it + 1]) }
            .firstOrNull { it.norm() > 1e-8 }
            ?: throw IllegalArgumentException("Collinear points")
        val n = Vec3(normal.x / normal.norm(), normal.y / normal.norm(), normal.z / normal.norm())
        require(vectors.all { abs(it.dot(n)) <= tolerance }) { "Nonplanar polygon" }

        val projected = points.map { p ->
            when {
                abs(n.x) >= abs(n.y) && abs(n.x) >= abs(n.z) -> Pair(p.y, p.z)
                abs(n.y) >= abs(n.z) -> Pair(p.x, p.z)
                else -> Pair(p.x, p.y)
            }
        }
        fun orient(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double =
            (b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)
        fun on(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) =
            b.first >= minOf(a.first, c.first) - 1e-9 && b.first <= maxOf(a.first, c.first) + 1e-9 &&
            b.second >= minOf(a.second, c.second) - 1e-9 && b.second <= maxOf(a.second, c.second) + 1e-9
        fun intersects(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>): Boolean {
            val u = orient(a, b, c); val v = orient(a, b, d)
            val w = orient(c, d, a); val z = orient(c, d, b)
            return (u * v < -1e-9 && w * z < -1e-9) ||
                (abs(u) < 1e-9 && on(a, c, b)) ||
                (abs(v) < 1e-9 && on(a, d, b)) ||
                (abs(w) < 1e-9 && on(c, a, d)) ||
                (abs(z) < 1e-9 && on(c, b, d))
        }
        for (i in projected.indices) for (j in i + 1 until projected.size) {
            if (j == i + 1 || (i == 0 && j == projected.lastIndex)) continue
            require(!intersects(projected[i], projected[(i + 1) % projected.size],
                projected[j], projected[(j + 1) % projected.size])) { "Self-intersecting polygon" }
        }

        var sum = Vec3(0.0, 0.0, 0.0)
        for (i in 1 until vectors.size - 1) sum += vectors[i].cross(vectors[i + 1])
        val area = abs(sum.dot(n)) * 0.5
        require(area > 1e-8) { "Zero area" }
        return area
    }
}
