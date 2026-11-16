package com.bimspecialist.measure3d

import org.junit.Assert.*
import org.junit.Test

/** Pure capture-state tests; physical ARCore tracking still requires a device. */
class PointPlacementPolicyTests {
    @Test fun lengthRequiresExactlyTwoAnchoredPoints() {
        assertEquals(2, PointPlacementPolicy.maximumPoints(ToolMode.LENGTH))
        assertTrue(PointPlacementPolicy.canPlace(ToolMode.LENGTH, 0, true))
        assertTrue(PointPlacementPolicy.canPlace(ToolMode.LENGTH, 1, true))
        assertFalse(PointPlacementPolicy.canPlace(ToolMode.LENGTH, 2, true))
        assertFalse(PointPlacementPolicy.canPlace(ToolMode.LENGTH, 3, true))
        assertFalse(PointPlacementPolicy.canPlace(ToolMode.LENGTH, 0, false))
        assertEquals(1, PointPlacementPolicy.nextPointIndex(ToolMode.LENGTH, 0))
        assertEquals(2, PointPlacementPolicy.nextPointIndex(ToolMode.LENGTH, 1))
        assertNull(PointPlacementPolicy.nextPointIndex(ToolMode.LENGTH, 2))
    }

    @Test fun areaCanPlaceManyPointsButScanCannotPlaceMeasurementAnchors() {
        assertEquals(24, PointPlacementPolicy.maximumPoints(ToolMode.AREA))
        assertTrue(PointPlacementPolicy.canPlace(ToolMode.AREA, 23, true))
        assertFalse(PointPlacementPolicy.canPlace(ToolMode.AREA, 24, true))
        assertFalse(PointPlacementPolicy.canPlace(ToolMode.SCAN, 0, true))
        assertNull(PointPlacementPolicy.nextPointIndex(ToolMode.SCAN, 0))
    }

    @Test fun portraitAimUsesCenterOfCameraViewport() {
        assertEquals(0.5f, PointPlacementPolicy.targetXFraction(393f, 880f, 345), 0.00001f)
    }

    @Test fun landscapeAimAvoidsOverlaySidePanel() {
        val result = PointPlacementPolicy.targetXFraction(880f, 393f, 345)
        assertEquals(267.5f / 880f, result, 0.00001f)
        assertTrue(result in 0.25f..0.35f)
    }

    @Test fun acceptTrackedPlaneAndOrientedFeatureOnly() {
        assertTrue(PointPlacementPolicy.isAcceptableHit("PLANE", true, insidePlane = true))
        assertFalse(PointPlacementPolicy.isAcceptableHit("PLANE", true, insidePlane = false))
        assertFalse(PointPlacementPolicy.isAcceptableHit("PLANE", false, insidePlane = true))
        assertTrue(PointPlacementPolicy.isAcceptableHit("FEATURE", true, estimatedNormal = true))
        assertFalse(PointPlacementPolicy.isAcceptableHit("FEATURE", true, estimatedNormal = false))
        assertFalse(PointPlacementPolicy.isAcceptableHit("DEPTH", true))
    }

    @Test fun rejectsInvalidViewportAndNegativePanel() {
        for (w in listOf(0f, -1f)) {
            try {
                PointPlacementPolicy.targetXFraction(w, 800f, 300)
                fail("Invalid width should fail")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            PointPlacementPolicy.targetXFraction(400f, 800f, -1)
            fail("Invalid panel should fail")
        } catch (_: IllegalArgumentException) {}
    }
}
