package com.novelarrow.reader.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val tagName: String,
    val title: String,
    val body: String,
    val apkName: String,
    val downloadUrl: String,
    val apkSize: Long,
    val htmlUrl: String
) {
    val formattedSize: String
        get() {
            if (apkSize <= 0) return ""
            val mb = apkSize.toDouble() / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }
}

sealed interface CheckUpdateResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : CheckUpdateResult
    data class UpToDate(val currentVersion: String) : CheckUpdateResult
    data class Error(val message: String, val throwable: Throwable? = null) : CheckUpdateResult
}

sealed interface SignatureCompatibility {
    object Compatible : SignatureCompatibility
    data class Mismatch(
        val installedFingerprint: String,
        val archiveFingerprint: String
    ) : SignatureCompatibility
    data class Unknown(val reason: String) : SignatureCompatibility
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    const val DEFAULT_REPO_OWNER = "faycalrafa"
    const val DEFAULT_REPO_NAME = "free-novel-reader-tts-android"

    /**
     * Checks the GitHub Releases API for a newer version than currentVersion.
     */
    suspend fun checkForUpdate(
        currentVersion: String,
        repoOwner: String = DEFAULT_REPO_OWNER,
        repoName: String = DEFAULT_REPO_NAME
    ): CheckUpdateResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases"
            Log.d(TAG, "Checking update at $endpoint (current: $currentVersion)")

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "NovelArrowReaderPro-Android")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "GitHub API response code: $responseCode")

            if (responseCode !in 200..299) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                Log.w(TAG, "GitHub error stream: $errorBody")

                val errorMessage = when {
                    responseCode == HttpURLConnection.HTTP_NOT_FOUND ->
                        "No releases found for this repository."
                    responseCode == 403 && (errorBody.contains("rate limit", ignoreCase = true) || errorBody.contains("API rate limit", ignoreCase = true)) ->
                        "GitHub API rate limit exceeded. Please try again later."
                    else ->
                        "GitHub API error ($responseCode): ${connection.responseMessage}"
                }
                return@withContext CheckUpdateResult.Error(errorMessage)
            }

            val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
            val release = parseReleaseJson(responseJson, repoOwner, repoName)
                ?: return@withContext CheckUpdateResult.Error("Could not parse release data from GitHub.")

            Log.d(TAG, "Found latest release: ${release.tagName}, APK: ${release.apkName}")

            val isNewer = isNewerVersion(currentVersion, release.tagName)
            if (!isNewer) {
                Log.d(TAG, "Current version ($currentVersion) is up-to-date with remote (${release.tagName})")
                return@withContext CheckUpdateResult.UpToDate(currentVersion)
            }

            if (release.downloadUrl.isBlank()) {
                return@withContext CheckUpdateResult.Error("Latest release (${release.tagName}) does not contain an APK asset.")
            }

            CheckUpdateResult.UpdateAvailable(release)
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "UnknownHostException checking update: ${e.message}")
            CheckUpdateResult.Error("Cannot reach GitHub. Check your internet connection or verify data access is allowed in device settings.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking update: [${e::class.java.name}] ${e.message}")
            CheckUpdateResult.Error(e.message ?: "Failed to connect to GitHub releases.", e)
        }
    }

    /**
     * Parses GitHub release JSON safely into UpdateInfo using org.json.JSONObject.
     */
    fun parseReleaseJson(
        jsonString: String,
        repoOwner: String = DEFAULT_REPO_OWNER,
        repoName: String = DEFAULT_REPO_NAME
    ): UpdateInfo? {
        return try {
            val trimmed = jsonString.trim()
            val json = if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                if (array.length() == 0) return null
                array.getJSONObject(0)
            } else {
                JSONObject(trimmed)
            }
            val tagName = json.optString("tag_name", "").ifBlank { return null }
            val name = json.optString("name", "").ifBlank { tagName }
            val body = json.optString("body", "").ifBlank { "Performance improvements and bug fixes." }
            val htmlUrl = json.optString("html_url", "https://github.com/$repoOwner/$repoName/releases/tag/$tagName")

            val assets = json.optJSONArray("assets")
            var apkName = ""
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkName = assetName
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            UpdateInfo(
                tagName = tagName,
                title = name,
                body = body,
                apkName = apkName,
                downloadUrl = downloadUrl,
                apkSize = apkSize,
                htmlUrl = htmlUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseReleaseJson", e)
            null
        }
    }

    /**
     * Compares installed version string with the remote release tag name.
     * Returns true if remote tag is newer than current installed version.
     */
    fun isNewerVersion(currentVersion: String, latestTag: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")

        if (cleanCurrent.equals(cleanLatest, ignoreCase = true)) return false

        val currentTokens = cleanCurrent.split(".", "-", "_").filter { it.isNotEmpty() }
        val latestTokens = cleanLatest.split(".", "-", "_").filter { it.isNotEmpty() }

        val maxTokens = maxOf(currentTokens.size, latestTokens.size)
        for (i in 0 until maxTokens) {
            val currStr = currentTokens.getOrNull(i)
            val lateStr = latestTokens.getOrNull(i)

            if (currStr == null && lateStr != null) {
                val num = lateStr.toLongOrNull()
                if (num != null && num > 0) return true
                if (num == null) return true
            } else if (currStr != null && lateStr == null) {
                val num = currStr.toLongOrNull()
                if (num != null && num > 0) return false
                if (num == null) return false
            } else if (currStr != null && lateStr != null) {
                val currNum = currStr.toLongOrNull()
                val lateNum = lateStr.toLongOrNull()

                if (currNum != null && lateNum != null) {
                    if (lateNum > currNum) return true
                    if (lateNum < currNum) return false
                } else {
                    val cmp = lateStr.compareTo(currStr, ignoreCase = true)
                    if (cmp > 0) return true
                    if (cmp < 0) return false
                }
            }
        }
        return false
    }

    /**
     * Downloads the APK file into app's cache directory (cacheDir/updates) with progress updates.
     * Follows HTTP 301/302/307 redirects (required for GitHub release downloads).
     */
    suspend fun downloadApk(
        downloadUrl: String,
        expectedSize: Long,
        context: Context,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { it.delete() }

        val targetFile = File(updatesDir, "update-latest.apk")

        var currentUrl = downloadUrl
        var redirectCount = 0
        val maxRedirects = 5
        var connection: HttpURLConnection

        while (true) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NovelArrowReaderPro-Android")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location != null && redirectCount < maxRedirects) {
                    currentUrl = location
                    redirectCount++
                    continue
                }
            }
            break
        }

        if (connection.responseCode !in 200..299) {
            throw IOException("Download failed with HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }

        val totalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong else expectedSize
        var bytesReadSoFar = 0L
        val buffer = ByteArray(8192)

        try {
            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        bytesReadSoFar += read
                        onProgress(bytesReadSoFar, totalBytes)
                    }
                }
            }
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }

        targetFile
    }

    /**
     * Copies a downloaded APK file to a custom URI selected by the user.
     */
    suspend fun copyApkToUri(context: Context, sourceApk: File, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val copied = context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                sourceApk.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: 0L
            copied > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if the app is allowed to request package installs on Android 8.0+.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches system settings screen to allow installing unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the system package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            throw FileNotFoundException("APK file not found: ${apkFile.absolutePath}")
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    /**
     * Gets the currently installed application version name.
     */
    fun getInstalledVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "2.5.0"
        } catch (e: Exception) {
            "2.5.0"
        }
    }

    /**
     * Converts a certificate raw byte array into an uppercase colon-separated SHA-256 fingerprint string.
     */
    fun certToSha256(certBytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Retrieves the SHA-256 signing certificate fingerprint of the currently installed application.
     */
    fun getInstalledSigningCertificateFingerprint(context: Context): String? {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                }
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.firstOrNull()?.let { certToSha256(it.toByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query installed signing certificate fingerprint", e)
            null
        }
    }

    /**
     * Retrieves the SHA-256 signing certificate fingerprint from a downloaded APK archive file.
     */
    fun getArchiveSigningCertificateFingerprint(context: Context, apkFile: File): String? {
        return try {
            if (!apkFile.exists()) return null
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return null

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = archiveInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    @Suppress("DEPRECATION")
                    archiveInfo.signatures
                }
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.signatures
            }

            signatures?.firstOrNull()?.let { certToSha256(it.toByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query archive signing certificate fingerprint: ${apkFile.absolutePath}", e)
            null
        }
    }

    /**
     * Verifies that the downloaded APK signature matches the installed package signature.
     * Prevents INSTALL_FAILED_UPDATE_INCOMPATIBLE by warning the user beforehand.
     */
    fun checkSignatureCompatibility(context: Context, apkFile: File): SignatureCompatibility {
        if (!apkFile.exists()) {
            return SignatureCompatibility.Unknown("APK file does not exist: ${apkFile.absolutePath}")
        }

        val installedFingerprint = getInstalledSigningCertificateFingerprint(context)
            ?: return SignatureCompatibility.Compatible // Fallback to system package installer

        val archiveFingerprint = getArchiveSigningCertificateFingerprint(context, apkFile)
            ?: return SignatureCompatibility.Compatible // Fallback to system package installer

        return if (installedFingerprint.equals(archiveFingerprint, ignoreCase = true)) {
            SignatureCompatibility.Compatible
        } else {
            SignatureCompatibility.Mismatch(
                installedFingerprint = installedFingerprint,
                archiveFingerprint = archiveFingerprint
            )
        }
    }
}
