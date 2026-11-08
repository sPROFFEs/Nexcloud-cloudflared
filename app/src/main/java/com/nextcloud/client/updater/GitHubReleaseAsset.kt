/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import org.json.JSONObject

data class GitHubReleaseAsset(val name: String, val browserDownloadUrl: String, val sizeBytes: Long) {
    companion object {
        fun fromJson(json: JSONObject): GitHubReleaseAsset = GitHubReleaseAsset(
            name = json.optString("name", ""),
            browserDownloadUrl = json.optString("browser_download_url", ""),
            sizeBytes = json.optLong("size", 0L)
        )
    }
}
