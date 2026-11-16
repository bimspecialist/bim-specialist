package com.bimspecialist.measure3d.core

/** Explicit choice; never silently reinterpret an older exported DXF file. */
enum class DxfCoordinateSystem(val displayLabel: String) {
    ARCORE_RAW("ARCore raw (Y up)"),
    CAD_Z_UP("CAD (Z up)")
}

object CadAxes {
    /**
     * ARCore: Y is gravity-up. CAD: Z is up.
     * A proper right-handed rotation: (x, y, z) -> (x, -z, y).
     * The origin stays local to this AR tracking session, NOT survey-controlled.
     */
    fun fromArCore(v: Vec3, system: DxfCoordinateSystem): Vec3 =
        when (system) {
            DxfCoordinateSystem.ARCORE_RAW -> v
            DxfCoordinateSystem.CAD_Z_UP -> Vec3(v.x, -v.z, v.y)
        }

    fun toArCore(v: Vec3, system: DxfCoordinateSystem): Vec3 =
        when (system) {
            DxfCoordinateSystem.ARCORE_RAW -> v
            DxfCoordinateSystem.CAD_Z_UP -> Vec3(v.x, v.z, -v.y)
        }
}

/** Pure, reproducible stability check for tracked anchor positions. */
class ArStabilityMonitor(
    private val requiredFrames: Int = 6,
    private val toleranceMetres: Double = 0.025
) {
    init {
        require(requiredFrames >= 2)
        require(toleranceMetres > 0.0 && toleranceMetres.isFinite())
    }
    private val samples = ArrayDeque<List<Vec3>>()

    fun reset() = samples.clear()

    fun update(anchors: List<Vec3>, tracking: Boolean): Boolean {
        if (!tracking || anchors.isEmpty() || anchors.any { !it.isFinite() }) {
            reset()
            return false
        }
        if (samples.firstOrNull()?.size != anchors.size) reset()
        samples.addLast(anchors.toList())
        while (samples.size > requiredFrames) samples.removeFirst()
        if (samples.size < requiredFrames) return false
        val first = samples.first()
        return samples.all { frame ->
            frame.indices.all { i -> frame[i].distanceTo(first[i]) <= toleranceMetres }
        }
    }
}
