/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseTest {

    @Test
    fun fromJson_parsesReleaseWithApkAssetCorrectly() {
        val jsonString = """
            {
                "tag_name": "v3.31.0-cf1",
                "name": "Cloudflare Access Release 1",
                "body": "Initial release with Cloudflare Zero Trust support.",
                "html_url": "https://github.com/sPROFFEs/Nexcloud-cloudflared/releases/tag/v3.31.0-cf1",
                "assets": [
                    {
                        "name": "app-gplay-debug.apk",
                        "browser_download_url": "https://github.com/sPROFFEs/Nexcloud-cloudflared/releases/download/v3.31.0-cf1/app-gplay-debug.apk",
                        "size": 140000000
                    },
                    {
                        "name": "checksums.txt",
                        "browser_download_url": "https://github.com/sPROFFEs/Nexcloud-cloudflared/releases/download/v3.31.0-cf1/checksums.txt",
                        "size": 512
                    }
                ]
            }
        """.trimIndent()

        val release = GitHubRelease.fromJson(JSONObject(jsonString))

        assertEquals("v3.31.0-cf1", release.tagName)
        assertEquals("Cloudflare Access Release 1", release.name)
        assertEquals(2, release.assets.size)

        val apkAsset = release.findApkAsset()
        assertNotNull(apkAsset)
        assertEquals("app-gplay-debug.apk", apkAsset?.name)
        assertEquals(
            "https://github.com/sPROFFEs/Nexcloud-cloudflared/releases/download/v3.31.0-cf1/app-gplay-debug.apk",
            apkAsset?.browserDownloadUrl
        )
    }

    @Test
    fun findApkAsset_noApk_returnsNull() {
        val jsonString = """
            {
                "tag_name": "v1.0.0",
                "assets": [
                    {
                        "name": "source.zip",
                        "browser_download_url": "https://example.com/source.zip",
                        "size": 1024
                    }
                ]
            }
        """.trimIndent()

        val release = GitHubRelease.fromJson(JSONObject(jsonString))
        assertNull(release.findApkAsset())
    }
}
