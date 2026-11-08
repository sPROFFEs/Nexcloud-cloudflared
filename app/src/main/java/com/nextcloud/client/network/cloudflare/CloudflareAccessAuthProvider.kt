/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import com.owncloud.android.lib.common.network.CloudflareAuthProvider

/**
 * Strategy interface providing Cloudflare Zero Trust authentication headers.
 */
interface CloudflareAccessAuthProvider : CloudflareAuthProvider {
    fun getMode(): CloudflareAccessMode
    fun getAuthHeaders(url: String): Map<String, String> = getHeaders(url)
}
