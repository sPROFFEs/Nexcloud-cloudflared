/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import org.json.JSONObject
import java.util.Base64

/**
 * Utility to parse JWT expiration claim without logging token payload.
 */
object CloudflareJwtParser {
    private const val CLAIM_EXP = "exp"
    private const val JWT_PARTS_COUNT = 3
    private const val SECONDS_TO_MS = 1000L

    fun parseExpirationEpochMs(jwtToken: String?): Long? {
        if (jwtToken.isNullOrBlank()) {
            return null
        }

        val parts = jwtToken.split(".")
        if (parts.size < JWT_PARTS_COUNT) {
            return null
        }

        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val jsonObject = JSONObject(String(payloadBytes, Charsets.UTF_8))
            if (jsonObject.has(CLAIM_EXP)) {
                jsonObject.getLong(CLAIM_EXP) * SECONDS_TO_MS
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseExpiration(jwtToken: String?): Long? = parseExpirationEpochMs(jwtToken)
}
