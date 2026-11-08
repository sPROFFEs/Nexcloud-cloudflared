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

class CloudflareSessionTest {

    @Test
    fun isExpired_pastExpiration_returnsTrue() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "token"),
            expirationTimestampMs = System.currentTimeMillis() - 10_000L
        )
        assertTrue(session.isExpired())
    }

    @Test
    fun isExpired_futureExpiration_returnsFalse() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "token"),
            expirationTimestampMs = System.currentTimeMillis() + 600_000L
        )
        assertFalse(session.isExpired())
    }

    @Test
    fun isExpired_nullExpiration_returnsFalse() {
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "token"),
            expirationTimestampMs = null
        )
        assertFalse(session.isExpired())
    }

    @Test
    fun toCookieHeaderValue_formatsPairsWithSemicolons() {
        val session = CloudflareSession(
            cookies = linkedMapOf("CF_Authorization" to "token1", "cf_clearance" to "token2")
        )
        val header = session.toCookieHeaderValue()
        assertEquals("CF_Authorization=token1; cf_clearance=token2", header)
    }

    @Test
    fun toCookieHeaderValue_emptyCookies_returnsEmptyString() {
        val session = CloudflareSession(cookies = emptyMap())
        assertEquals("", session.toCookieHeaderValue())
    }
}
