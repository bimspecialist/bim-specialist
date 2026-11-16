package com.bimspecialist.measure3d.updates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CachedUpdateTests {
    @Test fun cachedApkIsRevalidatedAndTamperingRejected() = runBlocking {
        val data = ByteArray(130_000) { (it % 251).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        val info = AppUpdate(6, "0.6.0-beta",
            "https://github.com/bimspecialist/bim-specialist/releases/download/bim-measure-3d-v0.6.0-beta/BIMMeasure3D-v0.6.0-beta.apk",
            digest, data.size.toLong())
        val file = File.createTempFile("measure-apk-test", ".apk")
        try {
            file.writeBytes(data)
            assertTrue(AppUpdater.verifyCached(file, info))
            file.writeBytes(data.apply { this[0] = 42 })
            assertFalse(AppUpdater.verifyCached(file, info))
            assertFalse(AppUpdater.verifyCached(file, info.copy(fileSize = info.fileSize + 1)))
        } finally { file.delete() }
    }
}
