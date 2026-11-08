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

class CloudflareServiceTokenAuthProviderTest {

    private val config = CloudflareAccessConfig(
        mode = CloudflareAccessMode.SERVICE_TOKEN,
        clientId = "client-id-123.access",
        clientSecret = "secret-token-xyz-456",
        serverUrl = "https://cloud.example.com"
    )

    private val provider = CloudflareServiceTokenAuthProvider(config)

    @Test
    fun getHeaders_matchingOrigin_returnsServiceTokenHeaders() {
        val headers = provider.getHeaders("https://cloud.example.com/status.php")

        assertEquals(2, headers.size)
        assertEquals("client-id-123.access", headers["CF-Access-Client-Id"])
        assertEquals("secret-token-xyz-456", headers["CF-Access-Client-Secret"])
    }

    @Test
    fun getHeaders_mismatchedOrigin_returnsEmptyHeaders() {
        val headers = provider.getHeaders("https://evil.example.com/status.php")
        assertTrue(headers.isEmpty())
    }

    @Test
    fun getHeaders_nullOrBlankUrl_returnsEmptyHeaders() {
        assertTrue(provider.getHeaders(null).isEmpty())
        assertTrue(provider.getHeaders("").isEmpty())
    }

    @Test
    fun isSessionExpired_alwaysReturnsFalseForServiceToken() {
        assertFalse(provider.isSessionExpired())
    }
}
