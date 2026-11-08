/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

object AppVersionComparator {

    fun isUpdateAvailable(currentVersion: String, remoteTag: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) {
            return false
        }

        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) {
            return false
        }

        val currentParts = extractNumericParts(cleanCurrent)
        val remoteParts = extractNumericParts(cleanRemote)

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) {
                return true
            }
            if (rem < curr) {
                return false
            }
        }

        // Numeric parts are equal: check for numeric suffix like -cf2 vs -cf1
        val currentSuffixNum = extractTrailingNumber(cleanCurrent)
        val remoteSuffixNum = extractTrailingNumber(cleanRemote)
        return remoteSuffixNum > currentSuffixNum
    }

    private fun extractNumericParts(version: String): List<Int> {
        val mainPart = version.substringBefore('-').substringBefore(' ')
        return mainPart.split('.')
            .mapNotNull { it.toIntOrNull() }
    }

    private fun extractTrailingNumber(version: String): Int {
        val digits = version.filter { it.isDigit() }
        return digits.takeLast(4).toIntOrNull() ?: 0
    }
}
