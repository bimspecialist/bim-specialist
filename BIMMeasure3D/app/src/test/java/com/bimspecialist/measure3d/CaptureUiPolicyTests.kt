package com.bimspecialist.measure3d

import org.junit.Assert.*
import org.junit.Test

class CaptureUiPolicyTests {
    @Test fun neverSavePartialOrUnstableMeasurements() {
        assertFalse(CaptureUiPolicy.canSaveMeasurement(ToolMode.LENGTH, false, true, true, true))
        assertFalse(CaptureUiPolicy.canSaveMeasurement(ToolMode.LENGTH, true, false, true, true))
        assertFalse(CaptureUiPolicy.canSaveMeasurement(ToolMode.LENGTH, true, true, false, true))
        assertFalse(CaptureUiPolicy.canSaveMeasurement(ToolMode.AREA, true, true, true, false))
        assertTrue(CaptureUiPolicy.canSaveMeasurement(ToolMode.LENGTH, true, true, true, true))
        assertTrue(CaptureUiPolicy.canSaveMeasurement(ToolMode.AREA, true, true, true, true))
        assertFalse(CaptureUiPolicy.canSaveMeasurement(ToolMode.SCAN, true, true, true, true))
    }

    @Test fun scanningExportRequiresActualStoppedCloud() {
        assertFalse(CaptureUiPolicy.canExport(ToolMode.SCAN, false, true, false, false, 0, false))
        assertFalse(CaptureUiPolicy.canExport(ToolMode.SCAN, false, true, false, false, 100, true))
        assertTrue(CaptureUiPolicy.canExport(ToolMode.SCAN, false, false, false, false, 100, false))
    }

    @Test fun leavingUnsavedCaptureRequiresConfirmation() {
        assertFalse(CaptureUiPolicy.requiresDiscardConfirmation(0, 0))
        assertTrue(CaptureUiPolicy.requiresDiscardConfirmation(1, 0))
        assertTrue(CaptureUiPolicy.requiresDiscardConfirmation(0, 250))
    }
}
