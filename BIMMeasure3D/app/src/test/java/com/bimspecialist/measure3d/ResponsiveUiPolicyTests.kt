package com.bimspecialist.measure3d

import org.junit.Assert.*
import org.junit.Test

/** Design-space tests: these do not substitute for a screenshot on the physical phone. */
class ResponsiveUiPolicyTests {
    @Test fun gt20ProTallPortraitKeepsCameraVisible() {
        // 1080x2436 hardware pixels; Android logical display is variable with display size.
        val layout = ResponsiveUiPolicy.forBounds(widthDp = 393, heightDp = 880, fontScale = 1.0f)
        assertFalse(layout.landscape)
        assertFalse(layout.compact)
        assertTrue(layout.panelMaxHeightDp <= 360)
        assertTrue(880 - layout.panelMaxHeightDp >= 500)
        assertTrue(layout.primaryButtonHeightDp >= 48)
        assertTrue(layout.cardMinHeightDp >= 96)
        assertTrue(layout.showHomeTagline)
    }

    @Test fun increasedFontScaleUsesCompactCardsAndScrollablePanel() {
        val layout = ResponsiveUiPolicy.forBounds(393, 880, 1.5f)
        assertTrue(layout.compact)
        assertEquals(54, layout.cardIconDp)
        assertTrue(layout.panelMaxHeightDp <= 410)
        assertTrue(layout.panelMaxHeightDp > 300)
    }

    @Test fun smallerScreensRemainUsable() {
        val layout = ResponsiveUiPolicy.forBounds(320, 590, 1.0f)
        assertTrue(layout.compact)
        assertFalse(layout.landscape)
        assertEquals(12, layout.horizontalPaddingDp)
        assertTrue(layout.panelMaxHeightDp < 300)
        assertTrue(590 - layout.panelMaxHeightDp >= 250)
    }

    @Test fun landscapeUsesSidePanelInsteadOfCoveringCamera() {
        val layout = ResponsiveUiPolicy.forBounds(880, 393, 1f)
        assertTrue(layout.landscape)
        assertTrue(layout.sidePanelWidthDp in 260..345)
        assertTrue(880 - layout.sidePanelWidthDp >= 500)
        assertFalse(layout.showHomeTagline)
    }

    @Test fun invalidLayoutInputsAreRejected() {
        for (bad in listOf(0, -1)) {
            try {
                ResponsiveUiPolicy.forBounds(bad, 800, 1f)
                fail("Invalid width must not be accepted")
            } catch (_: IllegalArgumentException) {}
            try {
                ResponsiveUiPolicy.forBounds(390, bad, 1f)
                fail("Invalid height must not be accepted")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            ResponsiveUiPolicy.forBounds(393, 880, Float.NaN)
            fail("Invalid font scale")
        } catch (_: IllegalArgumentException) {}
    }
}
