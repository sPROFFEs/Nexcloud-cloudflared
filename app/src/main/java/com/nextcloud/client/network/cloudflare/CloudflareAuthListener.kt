/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

interface CloudflareAuthListener {
    fun onSessionExpired(accountName: String?)
    fun onAuthenticationChallenge(accountName: String?, statusCode: Int, url: String)
}
