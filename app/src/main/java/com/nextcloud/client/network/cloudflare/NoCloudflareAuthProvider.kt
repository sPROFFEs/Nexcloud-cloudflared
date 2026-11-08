/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Null-object auth provider when Cloudflare Access is disabled.
 */
object NoCloudflareAuthProvider : CloudflareAccessAuthProvider {
    override fun getHeaders(url: String?): Map<String, String> = emptyMap()

    override fun isSessionExpired(): Boolean = false

    override fun getMode(): CloudflareAccessMode = CloudflareAccessMode.DISABLED

    override fun onAuthenticationChallenge(statusCode: Int, url: String?) = Unit
}
