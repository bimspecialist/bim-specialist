package com.bimspecialist.measure3d

import com.bimspecialist.measure3d.core.*
import org.junit.Assert.*
import org.junit.Test

class RecordSaveAndLifecycleTests {
    private val firstManual = ManualMeasurementCalculator.calculate(
        ManualMeasureMode.LENGTH, "125", "", DisplayUnit.CENTIMETRE
    )

    @Test fun rapidDuplicateSaveIsRejectedUntilCurrentSubmissionCompletes() {
        val key = RecordSavePolicy.captureKey("general", ToolMode.LENGTH, 3)
        assertTrue(RecordSavePolicy.canSaveCapture(
            ToolMode.LENGTH, true, true, true, true, false, key, null))
        assertFalse(RecordSavePolicy.canSaveCapture(
            ToolMode.LENGTH, true, true, true, true, true, key, null))
        assertFalse(RecordSavePolicy.canSaveCapture(
            ToolMode.LENGTH, true, true, true, true, false, key, key))
        assertTrue(RecordSavePolicy.canSaveCapture(
            ToolMode.LENGTH, true, true, true, true, false,
            RecordSavePolicy.captureKey("general", ToolMode.LENGTH, 4), key))
        assertFalse(RecordSavePolicy.canSaveCapture(
            ToolMode.LENGTH, true, false, true, true, false, key, null))
    }

    @Test fun manualMeasurementsWithSameValueInDifferentProjectsAreIndependent() {
        assertTrue(RecordSavePolicy.manualAlreadySaved(
            firstManual, "site-A", firstManual, "site-A"))
        assertFalse(RecordSavePolicy.manualAlreadySaved(
            firstManual, "site-B", firstManual, "site-A"))
        assertFalse(RecordSavePolicy.manualAlreadySaved(
            firstManual.copy(lengthMetres=2.0), "site-A", firstManual, "site-A"))
        assertFalse(RecordSavePolicy.manualAlreadySaved(
            null, "site-A", firstManual, "site-A"))
    }

    @Test fun captureKeyIncludesProjectToolAndRevision() {
        val start = RecordSavePolicy.captureKey("general", ToolMode.LENGTH, 1)
        assertNotEquals(start, RecordSavePolicy.captureKey("site", ToolMode.LENGTH, 1))
        assertNotEquals(start, RecordSavePolicy.captureKey("general", ToolMode.AREA, 1))
        assertNotEquals(start, RecordSavePolicy.captureKey("general", ToolMode.LENGTH, 2))
    }

    @Test fun rotationOfUnsavedDraftRequiresExplicitWarning() {
        assertTrue(CaptureLifecyclePolicy.shouldWarnAfterRecreation(true, 0, 0))
        assertFalse(CaptureLifecyclePolicy.shouldWarnAfterRecreation(false, 0, 0))
        assertFalse(CaptureLifecyclePolicy.shouldWarnAfterRecreation(true, 2, 0))
        assertFalse(CaptureLifecyclePolicy.shouldWarnAfterRecreation(true, 0, 20))
    }

    @Test fun changedSessionInvalidatesOldAnchorsButNormalResumeDoesNot() {
        assertTrue(CaptureLifecyclePolicy.shouldDiscardForNewSession(true, 2, 0))
        assertTrue(CaptureLifecyclePolicy.shouldDiscardForNewSession(true, 0, 50))
        assertFalse(CaptureLifecyclePolicy.shouldDiscardForNewSession(false, 2, 0))
        assertFalse(CaptureLifecyclePolicy.shouldDiscardForNewSession(true, 0, 0))
    }
}
