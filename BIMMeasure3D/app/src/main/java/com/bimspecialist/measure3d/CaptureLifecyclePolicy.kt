package com.bimspecialist.measure3d

/** Conditions under which an in-progress AR capture may not be reused safely. */
internal object CaptureLifecyclePolicy {
    fun shouldWarnAfterRecreation(
        savedUnsavedDraft: Boolean, anchoredPoints: Int, scanPoints: Int
    ): Boolean = savedUnsavedDraft && anchoredPoints == 0 && scanPoints == 0

    fun shouldDiscardForNewSession(
        sessionChanged: Boolean, anchoredPoints: Int, scanPoints: Int
    ): Boolean = sessionChanged && (anchoredPoints > 0 || scanPoints > 0)
}
