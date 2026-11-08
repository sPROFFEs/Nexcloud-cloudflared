/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Auth provider attaching Cloudflare Access session cookies to matching requests.
 */
class CloudflareInteractiveAuthProvider(
    private val config: CloudflareAccessConfig,
    private val sessionSupplier: () -> CloudflareSession?,
    private val onChallengeListener: ((statusCode: Int, url: String) -> Unit)? = null
) : CloudflareAccessAuthProvider {

    constructor(
        config: CloudflareAccessConfig,
        session: CloudflareSession?,
        onChallengeListener: ((statusCode: Int, url: String) -> Unit)? = null
    ) : this(config, { session }, onChallengeListener)

    override fun getHeaders(url: String?): Map<String, String> {
        if (!config.matchesOrigin(url)) {
            return emptyMap()
        }

        val session = sessionSupplier() ?: return emptyMap()
        if (session.isExpired()) {
            return emptyMap()
        }

        val cookieHeader = session.toCookieHeader()
        if (cookieHeader.isBlank()) {
            return emptyMap()
        }

        return mapOf(HEADER_COOKIE to cookieHeader)
    }

    override fun isSessionExpired(): Boolean = sessionSupplier()?.isExpired() ?: true

    override fun getMode(): CloudflareAccessMode = CloudflareAccessMode.INTERACTIVE

    override fun onAuthenticationChallenge(statusCode: Int, url: String?) {
        if (url != null && config.matchesOrigin(url)) {
            onChallengeListener?.invoke(statusCode, url)
        }
    }

    companion object {
        private const val HEADER_COOKIE = "Cookie"
    }
}
