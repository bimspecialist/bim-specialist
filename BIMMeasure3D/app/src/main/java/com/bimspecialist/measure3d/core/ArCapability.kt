package com.bimspecialist.measure3d.core

/**
 * ARCore is an optional enhancement. The project browser and hand-entered
 * measurements work without AR services or camera permissions.
 */
enum class ArCapability {
    CHECKING, READY, SERVICE_REQUIRED, UNSUPPORTED, UNKNOWN, SESSION_FAILED
}

object ArCapabilityPolicy {
    /** Pure classifier to keep device status handling JVM-testable. */
    fun fromGoogleAvailability(name: String): ArCapability = when (name) {
        "SUPPORTED_INSTALLED" -> ArCapability.READY
        "SUPPORTED_NOT_INSTALLED", "SUPPORTED_APK_TOO_OLD" -> ArCapability.SERVICE_REQUIRED
        "UNSUPPORTED_DEVICE_NOT_CAPABLE" -> ArCapability.UNSUPPORTED
        "UNKNOWN_CHECKING" -> ArCapability.CHECKING
        else -> ArCapability.UNKNOWN
    }

    fun mayStartCamera(status: ArCapability, cameraPermissionGranted: Boolean): Boolean =
        status == ArCapability.READY && cameraPermissionGranted
}
