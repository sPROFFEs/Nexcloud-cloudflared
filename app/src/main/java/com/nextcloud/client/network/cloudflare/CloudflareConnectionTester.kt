/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudflareConnectionTester(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {

    suspend fun testConnection(
        serverUrl: String,
        config: CloudflareAccessConfig,
        session: CloudflareSession? = null
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedUrl = serverUrl.trim().trimEnd('/')
        if (trimmedUrl.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Server URL is required",
                failureOrigin = FailureOrigin.NETWORK
            )
        }

        val statusUrl = "$trimmedUrl/status.php"
        val requestBuilder = Request.Builder().url(statusUrl)

        when (config.mode) {
            CloudflareAccessMode.DISABLED -> {
                // No headers to attach
            }

            CloudflareAccessMode.SERVICE_TOKEN -> {
                val clientId = config.clientId
                val clientSecret = config.clientSecret
                if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                    requestBuilder.addHeader(HEADER_CLIENT_ID, clientId)
                    requestBuilder.addHeader(HEADER_CLIENT_SECRET, clientSecret)
                }
            }

            CloudflareAccessMode.INTERACTIVE -> {
                if (session != null && !session.isExpired()) {
                    val cookieHeader = session.toCookieHeaderValue()
                    if (cookieHeader.isNotBlank()) {
                        requestBuilder.addHeader(HEADER_COOKIE, cookieHeader)
                    }
                }
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val isCfRayPresent = response.header(HEADER_CF_RAY) != null
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    if (body.contains("\"installed\"") || response.header("X-Nextcloud-Version") != null) {
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = code,
                            message = "Connection successful. Nextcloud instance reached.",
                            failureOrigin = FailureOrigin.NONE
                        )
                    } else if (body.contains("Cloudflare Access") || (isCfRayPresent && code == HTTP_OK)) {
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = code,
                            message = "Cloudflare Access login page was returned instead of Nextcloud.",
                            failureOrigin = FailureOrigin.CLOUDFLARE_ACCESS
                        )
                    } else {
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = code,
                            message = "Server responded with status $code",
                            failureOrigin = FailureOrigin.NONE
                        )
                    }
                } else if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
                    if (isCfRayPresent || body.contains("Cloudflare Access") || body.contains("cf-error")) {
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = code,
                            message = "Cloudflare Access rejected credentials (HTTP $code)",
                            failureOrigin = FailureOrigin.CLOUDFLARE_ACCESS
                        )
                    } else {
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = code,
                            message = "Cloudflare Access authenticated. Nextcloud credentials required.",
                            failureOrigin = FailureOrigin.NEXTCLOUD
                        )
                    }
                } else if (code in HTTP_REDIRECT_MIN..HTTP_REDIRECT_MAX) {
                    val location = response.header("Location").orEmpty()
                    if (location.contains("cloudflareaccess.com") || location.contains("cdn-cgi/access")) {
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = code,
                            message = "Redirected to Cloudflare Access login page",
                            failureOrigin = FailureOrigin.CLOUDFLARE_ACCESS
                        )
                    } else {
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = code,
                            message = "Redirected to $location",
                            failureOrigin = FailureOrigin.NONE
                        )
                    }
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Server returned HTTP $code",
                        failureOrigin = FailureOrigin.NETWORK
                    )
                }
            }
        } catch (e: IOException) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Network error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                failureOrigin = FailureOrigin.NETWORK
            )
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_REDIRECT_MIN = 300
        private const val HTTP_REDIRECT_MAX = 399

        private const val HEADER_CLIENT_ID = "CF-Access-Client-Id"
        private const val HEADER_CLIENT_SECRET = "CF-Access-Client-Secret"
        private const val HEADER_COOKIE = "Cookie"
        private const val HEADER_CF_RAY = "cf-ray"
    }
}
