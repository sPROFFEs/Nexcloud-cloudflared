/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Result of testing a Cloudflare Access connection.
 */
data class ConnectionTestResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val failureOrigin: FailureOrigin = FailureOrigin.NONE
)
