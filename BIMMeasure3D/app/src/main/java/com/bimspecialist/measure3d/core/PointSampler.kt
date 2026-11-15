package com.bimspecialist.measure3d.core

import kotlin.math.floor

class PointSampler(private val voxelMeters: Double = 0.02, private val maxPoints: Int = 25000) {
    init {
        require(voxelMeters > 0 && voxelMeters.isFinite())
        require(maxPoints in 1..500000)
    }

    private data class Voxel(val x: Int, val y: Int, val z: Int)
    private val points = LinkedHashMap<Voxel, Vec3>()

    @Synchronized fun add(p: Vec3, confidence: Float) {
        if (confidence < 0.6f || !p.isFinite()) return
        val key = Voxel(
            floor(p.x / voxelMeters).toInt(),
            floor(p.y / voxelMeters).toInt(),
            floor(p.z / voxelMeters).toInt()
        )
        if (points.size < maxPoints) points.putIfAbsent(key, p)
    }

    @Synchronized fun snapshot(): List<Vec3> = points.values.toList()
    @Synchronized fun count(): Int = points.size
    @Synchronized fun isFull(): Boolean = points.size >= maxPoints
    @Synchronized fun clear() { points.clear() }
}
