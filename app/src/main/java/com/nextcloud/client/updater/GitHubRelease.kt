/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import org.json.JSONObject

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val assets: List<GitHubReleaseAsset>
) {
    fun findApkAsset(): GitHubReleaseAsset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    companion object {
        fun fromJson(json: JSONObject): GitHubRelease {
            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val body = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "")
            val assetsArray = json.optJSONArray("assets")
            val assetsList = mutableListOf<GitHubReleaseAsset>()

            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val assetJson = assetsArray.getJSONObject(i)
                    assetsList.add(GitHubReleaseAsset.fromJson(assetJson))
                }
            }

            return GitHubRelease(tagName, name, body, htmlUrl, assetsList)
        }
    }
}
