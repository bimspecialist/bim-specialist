package com.bimspecialist.measure3d.core

data class CloudStats(val count: Int, val spanX: Double, val spanY: Double, val spanZ: Double)

object CloudAnalysis {
    fun compute(points: List<Vec3>): CloudStats? {
        if (points.isEmpty()) return null
        require(points.all { it.isFinite() })
        return CloudStats(
            points.size,
            points.maxOf { it.x } - points.minOf { it.x },
            points.maxOf { it.y } - points.minOf { it.y },
            points.maxOf { it.z } - points.minOf { it.z }
        )
    }
}
