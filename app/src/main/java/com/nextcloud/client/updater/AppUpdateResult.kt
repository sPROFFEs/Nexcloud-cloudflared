/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

sealed class AppUpdateResult {
    data class Available(val release: GitHubRelease, val apkAsset: GitHubReleaseAsset) : AppUpdateResult()

    data class UpToDate(val currentVersion: String) : AppUpdateResult()

    data class Error(val message: String, val cause: Throwable? = null) : AppUpdateResult()
}
