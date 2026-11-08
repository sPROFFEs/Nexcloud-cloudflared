/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudflareJwtParserTest {

    @Test
    fun parseExpiration_validJwtWithExp_returnsEpochMillis() {
        // {"alg":"RS256","typ":"JWT"} . {"sub":"user@example.com","exp":1700000000} . signature
        val jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiZXhwIjoxNzAwMDAwMDAwfQ.sig"
        val expectedMs = 1700000000L * 1000L

        val result = CloudflareJwtParser.parseExpiration(jwt)
        assertEquals(expectedMs, result)
    }

    @Test
    fun parseExpiration_jwtWithoutExpClaim_returnsNull() {
        // {"sub":"user@example.com"} in payload
        val jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.sig"

        val result = CloudflareJwtParser.parseExpiration(jwt)
        assertNull(result)
    }

    @Test
    fun parseExpiration_malformedTokens_returnsNullSafely() {
        assertNull(CloudflareJwtParser.parseExpiration(null))
        assertNull(CloudflareJwtParser.parseExpiration(""))
        assertNull(CloudflareJwtParser.parseExpiration("single_part_token"))
        assertNull(CloudflareJwtParser.parseExpiration("part1.part2"))
        assertNull(CloudflareJwtParser.parseExpiration("part1.invalid_base64!!!.part3"))
        assertNull(CloudflareJwtParser.parseExpiration("part1.bm90X2pzb24=.part3")) // payload is "not_json"
    }
}
