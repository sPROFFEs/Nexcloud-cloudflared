/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

/**
 * Identifies the origin of a failure during Cloudflare connection testing.
 */
enum class FailureOrigin {
    NONE,
    CLOUDFLARE_ACCESS,
    NEXTCLOUD,
    NETWORK
}
