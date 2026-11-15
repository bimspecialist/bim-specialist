package com.bimspecialist.measure3d.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateRulesTests {
    private val valid = AppUpdate(
        versionCode = 5,
        versionName = "0.5.0-beta",
        apkUrl = "https://github.com/bimspecialist/bim-specialist/releases/download/bim-measure-3d-v0.5.0-beta/BIMMeasure3D-v0.5.0-beta.apk",
        sha256 = "a".repeat(64),
        fileSize = 40_000_000
    )

    @Test fun acceptsNewerBimSignedReleaseMetadata() {
        assertTrue(AppUpdater.validate(valid, installedVersion = 4))
    }

    @Test fun rejectsSameOrOlderVersion() {
        assertFalse(AppUpdater.validate(valid, installedVersion = 5))
        assertFalse(AppUpdater.validate(valid.copy(versionCode = 3), installedVersion = 4))
    }

    @Test fun rejectsUntrustedHostsAndInsecureSchemes() {
        assertFalse(AppUpdater.validate(valid.copy(apkUrl = "http://github.com/bimspecialist/bim-specialist/releases/download/bim-measure-3d-v0.5.0-beta/app.apk"), 4))
        assertFalse(AppUpdater.validate(valid.copy(apkUrl = "https://github.com.evil.test/bimspecialist/bim-specialist/releases/download/bim-measure-3d-v0.5.0-beta/app.apk"), 4))
        assertFalse(AppUpdater.validate(valid.copy(apkUrl = "https://github.com/bimspecialist/bim-specialist/releases/download/other-product/app.apk"), 4))
    }

    @Test fun rejectsInvalidChecksumAndUnexpectedFileSize() {
        assertFalse(AppUpdater.validate(valid.copy(sha256 = "short"), 4))
        assertFalse(AppUpdater.validate(valid.copy(sha256 = "q".repeat(64)), 4))
        assertFalse(AppUpdater.validate(valid.copy(fileSize = 0), 4))
        assertFalse(AppUpdater.validate(valid.copy(fileSize = 200_000_000), 4))
    }

    @Test fun rejectsSuspiciousPathsAndExcessivelyLongVersionNames() {
        assertFalse(AppUpdater.validate(valid.copy(apkUrl = valid.apkUrl + "?x=1"), 4))
        assertFalse(AppUpdater.validate(valid.copy(apkUrl = valid.apkUrl.replace("BIMMeasure3D-", "../BIMMeasure3D-")), 4))
        assertFalse(AppUpdater.validate(valid.copy(versionName = "x".repeat(41)), 4))
    }
}
