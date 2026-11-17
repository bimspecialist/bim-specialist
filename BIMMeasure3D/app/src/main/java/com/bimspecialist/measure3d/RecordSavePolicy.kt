package com.bimspecialist.measure3d

import com.bimspecialist.measure3d.core.ManualMeasurement

/**
 * Prevent accidental double-submit while keeping separate projects independent.
 * A revision changes whenever the in-progress AR point set changes.
 */
internal object RecordSavePolicy {
    fun captureKey(projectId: String, mode: ToolMode, revision: Int): String {
        require(projectId.isNotBlank() && revision >= 0)
        return "$projectId:${mode.name}:$revision"
    }

    fun canSaveCapture(
        mode: ToolMode, hasResult: Boolean, tracking: Boolean,
        stable: Boolean, storageReady: Boolean, saving: Boolean,
        currentKey: String, lastSavedKey: String?
    ): Boolean = CaptureUiPolicy.canSaveMeasurement(
        mode, hasResult, tracking, stable, storageReady
    ) && !saving && currentKey != lastSavedKey

    fun manualAlreadySaved(
        result: ManualMeasurement?, projectId: String,
        lastSaved: ManualMeasurement?, lastSavedProjectId: String?
    ): Boolean = result != null && result == lastSaved &&
        projectId == lastSavedProjectId
}
