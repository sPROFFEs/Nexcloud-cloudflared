/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Auth provider attaching Cloudflare Access Service Token credentials to matching requests.
 */
class CloudflareServiceTokenAuthProvider(
    private val config: CloudflareAccessConfig,
    private val onChallengeListener: ((statusCode: Int, url: String) -> Unit)? = null
) : CloudflareAccessAuthProvider {

    override fun getHeaders(url: String?): Map<String, String> {
        if (!config.isServiceTokenConfigured() || !config.matchesOrigin(url)) {
            return emptyMap()
        }

        val clientId = config.clientId ?: return emptyMap()
        val clientSecret = config.clientSecret ?: return emptyMap()

        return mapOf(
            HEADER_CF_CLIENT_ID to clientId,
            HEADER_CF_CLIENT_SECRET to clientSecret
        )
    }

    override fun isSessionExpired(): Boolean = false

    override fun getMode(): CloudflareAccessMode = CloudflareAccessMode.SERVICE_TOKEN

    override fun onAuthenticationChallenge(statusCode: Int, url: String?) {
        if (url != null && config.matchesOrigin(url)) {
            onChallengeListener?.invoke(statusCode, url)
        }
    }

    companion object {
        const val HEADER_CF_CLIENT_ID = "CF-Access-Client-Id"
        const val HEADER_CF_CLIENT_SECRET = "CF-Access-Client-Secret"
    }
}
