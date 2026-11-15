package com.bimspecialist.measure3d.core

data class ScreenPoint(val x: Float, val y: Float)

object ArProjection {
    // Column-major 4x4 matrices as defined by ARCore/OpenGL.
    private fun multiply(m: FloatArray, v: FloatArray): FloatArray {
        return FloatArray(4) { row ->
            m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3]
        }
    }

    fun worldToScreen(
        world: Vec3, view: FloatArray, projection: FloatArray,
        viewportWidth: Int, viewportHeight: Int
    ): ScreenPoint? {
        if (viewportWidth <= 0 || viewportHeight <= 0 || view.size < 16 || projection.size < 16) return null
        if (!world.x.isFinite() || !world.y.isFinite() || !world.z.isFinite()) return null
        val eye = multiply(view, floatArrayOf(world.x.toFloat(), world.y.toFloat(), world.z.toFloat(), 1f))
        val clip = multiply(projection, eye)
        if (!clip[3].isFinite() || clip[3] <= 0.0001f) return null
        val nx = clip[0] / clip[3]
        val ny = clip[1] / clip[3]
        val nz = clip[2] / clip[3]
        if (!nx.isFinite() || !ny.isFinite() || !nz.isFinite()) return null
        if (nx !in -1f..1f || ny !in -1f..1f || nz !in -1f..1f) return null
        return ScreenPoint((nx + 1f) * viewportWidth * 0.5f, (1f - ny) * viewportHeight * 0.5f)
    }
}
