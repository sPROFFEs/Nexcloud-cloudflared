/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareInteractiveAuthProviderTest {

    private val config = CloudflareAccessConfig(
        mode = CloudflareAccessMode.INTERACTIVE,
        serverUrl = "https://cloud.example.com"
    )

    @Test
    fun getHeaders_validSessionAndMatchingOrigin_returnsCookieHeader() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "jwt-token-value", "cf_clearance" to "clearance-token"),
            expirationTimestampMs = System.currentTimeMillis() + 3_600_000L
        )
        val provider = CloudflareInteractiveAuthProvider(config, session)

        val headers = provider.getHeaders("https://cloud.example.com/index.php/login/v2")
        assertEquals(1, headers.size)
        val cookieHeader = headers["Cookie"].orEmpty()
        assertTrue(cookieHeader.contains("CF_Authorization=jwt-token-value"))
        assertTrue(cookieHeader.contains("cf_clearance=clearance-token"))
    }

    @Test
    fun getHeaders_expiredSession_returnsEmptyHeaders() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "expired-jwt"),
            expirationTimestampMs = System.currentTimeMillis() - 60_000L
        )
        val provider = CloudflareInteractiveAuthProvider(config, session)

        assertTrue(provider.isSessionExpired())
        assertTrue(provider.getHeaders("https://cloud.example.com/status.php").isEmpty())
    }

    @Test
    fun getHeaders_nullSession_returnsEmptyHeaders() {
        val provider = CloudflareInteractiveAuthProvider(config, null)

        assertTrue(provider.isSessionExpired())
        assertTrue(provider.getHeaders("https://cloud.example.com/status.php").isEmpty())
    }

    @Test
    fun getHeaders_mismatchedOrigin_returnsEmptyHeaders() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "valid-token"),
            expirationTimestampMs = System.currentTimeMillis() + 3_600_000L
        )
        val provider = CloudflareInteractiveAuthProvider(config, session)

        val headers = provider.getHeaders("https://external-service.com/api")
        assertTrue(headers.isEmpty())
    }

    @Test
    fun isSessionExpired_activeSession_returnsFalse() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "valid-token"),
            expirationTimestampMs = System.currentTimeMillis() + 3_600_000L
        )
        val provider = CloudflareInteractiveAuthProvider(config, session)

        assertFalse(provider.isSessionExpired())
    }
}
