package com.bimspecialist.measure3d

/** Pure UI business rules shared by the compact capture screen and unit tests. */
internal object CaptureUiPolicy {
    fun canSaveMeasurement(
        mode: ToolMode, measured: Boolean, tracking: Boolean,
        stableAnchors: Boolean, storageReady: Boolean
    ): Boolean =
        mode != ToolMode.SCAN && measured && tracking && stableAnchors && storageReady

    fun canExport(
        mode: ToolMode, measured: Boolean, tracking: Boolean,
        stableAnchors: Boolean, storageReady: Boolean,
        cloudPoints: Int, scanning: Boolean
    ): Boolean = when (mode) {
        ToolMode.SCAN -> cloudPoints > 0 && !scanning
        ToolMode.LENGTH, ToolMode.AREA ->
            canSaveMeasurement(mode, measured, tracking, stableAnchors, storageReady)
    }

    fun requiresDiscardConfirmation(anchorCount: Int, cloudPoints: Int): Boolean =
        anchorCount > 0 || cloudPoints > 0
}
