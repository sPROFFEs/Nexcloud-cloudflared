/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareAccessConfigTest {

    @Test
    fun matchesOrigin_exactMatch_returnsTrue() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com"
        )

        assertTrue(config.matchesOrigin("https://cloud.example.com"))
        assertTrue(config.matchesOrigin("https://cloud.example.com/status.php"))
        assertTrue(config.matchesOrigin("https://cloud.example.com/index.php/login/v2"))
        assertTrue(config.matchesOrigin("https://cloud.example.com/remote.php/webdav/file.txt"))
    }

    @Test
    fun matchesOrigin_defaultPort443_returnsTrue() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com:443"
        )

        assertTrue(config.matchesOrigin("https://cloud.example.com"))
        assertTrue(config.matchesOrigin("https://cloud.example.com:443/status.php"))
    }

    @Test
    fun matchesOrigin_customPort_matchesOnlySamePort() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com:8443"
        )

        assertTrue(config.matchesOrigin("https://cloud.example.com:8443/status.php"))
        assertFalse(config.matchesOrigin("https://cloud.example.com/status.php"))
        assertFalse(config.matchesOrigin("https://cloud.example.com:443/status.php"))
        assertFalse(config.matchesOrigin("https://cloud.example.com:9443/status.php"))
    }

    @Test
    fun matchesOrigin_caseInsensitiveSchemeAndHost_returnsTrue() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "HTTPS://CLOUD.EXAMPLE.COM"
        )

        assertTrue(config.matchesOrigin("https://cloud.example.com/status.php"))
        assertTrue(config.matchesOrigin("https://CLOUD.example.com/remote.php"))
    }

    @Test
    fun matchesOrigin_differentHost_returnsFalse() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com"
        )

        assertFalse(config.matchesOrigin("https://evil.example.com/status.php"))
        assertFalse(config.matchesOrigin("https://other.com"))
        assertFalse(config.matchesOrigin("https://api.cloud.example.com"))
    }

    @Test
    fun matchesOrigin_differentScheme_returnsFalse() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com"
        )

        assertFalse(config.matchesOrigin("http://cloud.example.com/status.php"))
    }

    @Test
    fun matchesOrigin_urlWithoutScheme_matchesDefaultHttps() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "cloud.example.com"
        )

        assertTrue(config.matchesOrigin("https://cloud.example.com/status.php"))
        assertTrue(config.matchesOrigin("cloud.example.com/status.php"))
    }

    @Test
    fun matchesOrigin_invalidUrls_returnsFalseSafely() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            serverUrl = "https://cloud.example.com"
        )

        assertFalse(config.matchesOrigin(""))
        assertFalse(config.matchesOrigin("   "))
        assertFalse(config.matchesOrigin("not a url"))
        assertFalse(config.matchesOrigin("http://"))
    }

    @Test
    fun isEnabled_returnsTrueOnlyWhenNotDisabled() {
        val disabled = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val serviceToken = CloudflareAccessConfig(mode = CloudflareAccessMode.SERVICE_TOKEN)
        val interactive = CloudflareAccessConfig(mode = CloudflareAccessMode.INTERACTIVE)

        assertFalse(disabled.isEnabled)
        assertTrue(serviceToken.isEnabled)
        assertTrue(interactive.isEnabled)
    }
}
