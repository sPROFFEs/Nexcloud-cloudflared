/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Supported connection modes for Cloudflare Zero Trust Access.
 */
enum class CloudflareAccessMode(val key: String) {
    DISABLED("disabled"),
    SERVICE_TOKEN("service_token"),
    INTERACTIVE("interactive");

    companion object {
        fun fromKey(key: String?): CloudflareAccessMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: DISABLED
    }
}
