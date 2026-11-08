/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.owncloud.android.BuildConfig
import com.owncloud.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubAppUpdater(
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
) {

    suspend fun checkForUpdates(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateResult =
        withContext(Dispatchers.IO) {
            val url = "$GITHUB_API_BASE/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header(HEADER_ACCEPT, GITHUB_JSON_MIME)
                .header(HEADER_USER_AGENT, USER_AGENT)
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AppUpdateResult.Error(
                            "GitHub API returned HTTP ${response.code}: ${response.message}"
                        )
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext AppUpdateResult.Error("Empty response received from GitHub")
                    }

                    val release = GitHubRelease.fromJson(JSONObject(body))
                    val apkAsset = release.findApkAsset()
                        ?: return@withContext AppUpdateResult.Error("No APK found in release ${release.tagName}")

                    val isNewer = AppVersionComparator.isUpdateAvailable(currentVersion, release.tagName)
                    if (isNewer) {
                        AppUpdateResult.Available(release, apkAsset)
                    } else {
                        AppUpdateResult.UpToDate(currentVersion)
                    }
                }
            } catch (e: IOException) {
                AppUpdateResult.Error("Network error while checking for updates: ${e.message}", e)
            } catch (e: Exception) {
                AppUpdateResult.Error("Error parsing release info: ${e.message}", e)
            }
        }

    suspend fun downloadApk(context: Context, downloadUrl: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }
            val apkFile = File(updatesDir, APK_FILE_NAME)

            val request = Request.Builder()
                .url(downloadUrl)
                .header(HEADER_USER_AGENT, USER_AGENT)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((totalRead * 100) / totalBytes).toInt()
                                onProgress(percent.coerceIn(0, 100))
                            }
                        }
                        output.flush()
                    }
                }
            }

            apkFile
        }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            throw IOException("APK file does not exist: ${apkFile.absolutePath}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permissionIntent)
                return
            }
        }

        val authority = context.getString(R.string.file_provider_authority)
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, MIME_TYPE_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    companion object {
        const val DEFAULT_OWNER = "sPROFFEs"
        const val DEFAULT_REPO = "Nexcloud-cloudflared"

        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITHUB_JSON_MIME = "application/vnd.github.v3+json"
        private const val USER_AGENT = "Nextcloud-Android-Updater"
        private const val MIME_TYPE_APK = "application/vnd.android.package-archive"

        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_USER_AGENT = "User-Agent"

        private const val TIMEOUT_SECONDS = 30L
        private const val BUFFER_SIZE = 8192
        private const val UPDATES_DIR_NAME = "updates"
        private const val APK_FILE_NAME = "app-update.apk"
    }
}
