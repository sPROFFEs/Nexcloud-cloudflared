/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Persisted interactive session material for Cloudflare Zero Trust Access.
 */
data class CloudflareSession(
    val cookies: Map<String, String> = emptyMap(),
    val expirationTimestampMs: Long? = null,
    val sessionEstablishedAtEpochMs: Long = System.currentTimeMillis()
) {
    val expiresAtEpochMs: Long?
        get() = expirationTimestampMs

    fun cfAuthorizationToken(): String? = cookies[COOKIE_CF_AUTHORIZATION]

    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (cookies.isEmpty()) {
            return true
        }
        val exp = expirationTimestampMs ?: return false
        return nowEpochMs >= exp
    }

    fun toCookieHeader(): String {
        if (cookies.isEmpty()) {
            return ""
        }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun toCookieHeaderValue(): String = toCookieHeader()

    companion object {
        const val COOKIE_CF_AUTHORIZATION = "CF_Authorization"
        const val COOKIE_CF_APP_SESSION = "CF_AppSession"
    }
}
