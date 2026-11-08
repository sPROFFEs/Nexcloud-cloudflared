/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import java.net.URI

/**
 * Configuration for Cloudflare Zero Trust Access for a given Nextcloud account or login session.
 */
data class CloudflareAccessConfig(
    val mode: CloudflareAccessMode = CloudflareAccessMode.DISABLED,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val serverUrl: String = ""
) {
    val protectedOrigin: String?
        get() = serverUrl.takeIf { it.isNotBlank() }

    val isEnabled: Boolean
        get() = mode != CloudflareAccessMode.DISABLED

    fun isServiceTokenConfigured(): Boolean = mode == CloudflareAccessMode.SERVICE_TOKEN &&
        !clientId.isNullOrBlank() &&
        !clientSecret.isNullOrBlank()

    fun isInteractiveConfigured(): Boolean = mode == CloudflareAccessMode.INTERACTIVE

    fun matchesOrigin(url: String?): Boolean {
        if (url.isNullOrBlank() || serverUrl.isBlank()) {
            return false
        }

        val requestUri = safeParseUri(url) ?: return false
        val protectedUri = safeParseUri(serverUrl) ?: return false

        val requestHost = requestUri.host ?: return false
        val protectedHost = protectedUri.host ?: return false

        if (!requestHost.equals(protectedHost, ignoreCase = true)) {
            return false
        }

        val requestScheme = requestUri.scheme?.lowercase() ?: ""
        val protectedScheme = protectedUri.scheme?.lowercase() ?: ""
        if (requestScheme != protectedScheme) {
            return false
        }

        val defaultRequestPort = if (requestScheme == "https") DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
        val defaultProtectedPort = if (protectedScheme == "https") DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT

        val reqPort = if (requestUri.port != -1) requestUri.port else defaultRequestPort
        val protPort = if (protectedUri.port != -1) protectedUri.port else defaultProtectedPort

        return reqPort == protPort
    }

    companion object {
        private const val DEFAULT_HTTP_PORT = 80
        private const val DEFAULT_HTTPS_PORT = 443

        private fun safeParseUri(url: String): URI? = try {
            val withScheme = if (!url.contains("://")) "https://$url" else url
            URI.create(withScheme)
        } catch (_: Exception) {
            null
        }

        fun normalizeOrigin(rawUrl: String?): String? {
            if (rawUrl.isNullOrBlank()) {
                return null
            }
            val uri = safeParseUri(rawUrl) ?: return null
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return null
            val port = uri.port
            return if (port != -1 && port != DEFAULT_HTTPS_PORT && port != DEFAULT_HTTP_PORT) {
                "$scheme://$host:$port"
            } else {
                "$scheme://$host"
            }
        }
    }
}
