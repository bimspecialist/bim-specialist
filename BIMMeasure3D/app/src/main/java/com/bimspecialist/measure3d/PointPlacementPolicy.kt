package com.bimspecialist.measure3d

/**
 * Predictable UI policy for AR point placement, separate from the ARCore session.
 * Coordinates are normalized within the actual camera viewport.
 */
internal object PointPlacementPolicy {
    fun maximumPoints(mode: ToolMode): Int = when (mode) {
        ToolMode.LENGTH -> 2
        ToolMode.AREA -> 24
        ToolMode.SCAN -> 0
    }

    fun canPlace(mode: ToolMode, count: Int, tracking: Boolean): Boolean =
        tracking && count >= 0 && count < maximumPoints(mode)

    fun targetXFraction(widthDp: Float, heightDp: Float, panelWidthDp: Int): Float {
        require(widthDp > 0f && heightDp > 0f && panelWidthDp >= 0)
        val usableWidth = if (widthDp > heightDp) {
            (widthDp - panelWidthDp).coerceAtLeast(170f).coerceAtMost(widthDp)
        } else widthDp
        return ((usableWidth / 2f) / widthDp).coerceIn(0f, 1f)
    }

    fun nextPointIndex(mode: ToolMode, count: Int): Int? =
        if (count in 0 until maximumPoints(mode)) count + 1 else null

    fun isAcceptableHit(
        kind: String, tracking: Boolean, insidePlane: Boolean = false,
        estimatedNormal: Boolean = false
    ): Boolean = tracking && when (kind) {
        "PLANE" -> insidePlane
        // A properly oriented feature point is a fallback if no tracked plane is found.
        // Approximate geometry still needs external validation.
        "FEATURE" -> estimatedNormal
        "DEPTH" -> true
        else -> false
    }
}
