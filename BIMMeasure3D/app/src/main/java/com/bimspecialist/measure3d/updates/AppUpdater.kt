package com.bimspecialist.measure3d.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bimspecialist.measure3d.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val fileSize: Long
)

/**
 * For GitHub-hosted, self-distributed APKs. This is never a silent installer.
 * Android independently checks the APK signing certificate against the installed app.
 */
object AppUpdater {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/bimspecialist/bim-specialist/bim-measure-3d-android-build/BIMMeasure3D/updates/latest.json"
    private const val APK_PREFIX =
        "https://github.com/bimspecialist/bim-specialist/releases/download/bim-measure-3d-v"
    private const val MAX_APK_BYTES = 160L * 1024 * 1024

    fun validate(info: AppUpdate, installedVersion: Int): Boolean {
        if (info.versionCode <= installedVersion || info.versionCode < 1) return false
        if (info.versionName.isBlank() || info.versionName.length > 40) return false
        if (info.fileSize !in 100_000..MAX_APK_BYTES) return false
        if (!info.sha256.matches(Regex("[a-fA-F0-9]{64}"))) return false
        if (!info.apkUrl.startsWith(APK_PREFIX)) return false
        val uri = runCatching { java.net.URI(info.apkUrl) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null ||
            uri.query != null || uri.fragment != null) return false
        if (!uri.path.endsWith(".apk") || uri.path.contains("..")) return false
        return true
    }

    suspend fun checkAvailable(): AppUpdate? = withContext(Dispatchers.IO) {
        val raw = readHttps(URL(MANIFEST_URL), maxBytes = 16_384)
            .toString(Charsets.UTF_8)
        val json = JSONObject(raw)
        if (json.optString("packageName") != BuildConfig.APPLICATION_ID) return@withContext null
        val versionCode = json.optInt("versionCode")
        val info = AppUpdate(
            versionCode = versionCode,
            versionName = json.optString("versionName"),
            apkUrl = json.optString("apkUrl"),
            sha256 = json.optString("sha256"),
            fileSize = json.optLong("sizeBytes")
        )
        if (validate(info, BuildConfig.VERSION_CODE)) info else null
    }

    private fun openHttps(url: URL): HttpsURLConnection {
        require(url.protocol == "https") { "HTTPS required" }
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "BIMMeasure3D-Android-Updater")
        return connection
    }

    private fun readHttps(url: URL, maxBytes: Int): ByteArray {
        val connection = openHttps(url)
        try {
            require(connection.responseCode == 200) { "Update server unavailable" }
            return connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= maxBytes) { "Update metadata too large" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    suspend fun downloadVerified(context: Context, info: AppUpdate): File = withContext(Dispatchers.IO) {
        require(validate(info, BuildConfig.VERSION_CODE))
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("App downloads directory unavailable")
        val directory = File(root, "bim-updates")
        require(directory.isDirectory || directory.mkdirs())
        val finalFile = File(directory, "BIMMeasure3D-v${info.versionCode}.apk")
        val partial = File(directory, "BIMMeasure3D-v${info.versionCode}.apk.partial")
        partial.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val connection = openHttps(URL(info.apkUrl))
        try {
            require(connection.responseCode == 200) { "APK could not be downloaded" }
            // Follow only the official GitHub release CDN after the initial approved URL.
            val actualHost = connection.url.host.lowercase()
            require(
                actualHost == "github.com" ||
                actualHost == "release-assets.githubusercontent.com" ||
                actualHost == "objects.githubusercontent.com"
            ) { "Unexpected download host" }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_APK_BYTES && total <= info.fileSize) { "Oversized update" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(total == info.fileSize && actualHash.equals(info.sha256, ignoreCase = true)) {
                "Downloaded update failed checksum verification"
            }
            if (finalFile.exists()) require(finalFile.delete()) { "Cannot replace previous update file" }
            require(partial.renameTo(finalFile)) { "Cannot finalize downloaded update" }
            return@withContext finalFile
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally { connection.disconnect() }
    }

    /** Returns false if the user first needs to grant the app install permission in Settings. */
    fun requestInstall(activity: Activity, apk: File): Boolean {
        require(apk.isFile && apk.length() >= 100_000)
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", apk
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }
}
