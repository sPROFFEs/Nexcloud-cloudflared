/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionComparatorTest {

    @Test
    fun isUpdateAvailable_higherMajor_returnsTrue() {
        assertTrue(AppVersionComparator.isUpdateAvailable("3.31.0", "v4.0.0"))
    }

    @Test
    fun isUpdateAvailable_higherMinor_returnsTrue() {
        assertTrue(AppVersionComparator.isUpdateAvailable("3.31.0", "v3.32.0"))
    }

    @Test
    fun isUpdateAvailable_higherPatch_returnsTrue() {
        assertTrue(AppVersionComparator.isUpdateAvailable("3.31.0", "v3.31.1"))
    }

    @Test
    fun isUpdateAvailable_sameVersion_returnsFalse() {
        assertFalse(AppVersionComparator.isUpdateAvailable("3.31.0", "v3.31.0"))
        assertFalse(AppVersionComparator.isUpdateAvailable("3.31.0", "3.31.0"))
    }

    @Test
    fun isUpdateAvailable_lowerVersion_returnsFalse() {
        assertFalse(AppVersionComparator.isUpdateAvailable("3.31.1", "v3.31.0"))
        assertFalse(AppVersionComparator.isUpdateAvailable("4.0.0", "v3.31.0"))
    }

    @Test
    fun isUpdateAvailable_suffixNumberIncrement_returnsTrue() {
        assertTrue(AppVersionComparator.isUpdateAvailable("3.31.0-cf1", "v3.31.0-cf2"))
    }

    @Test
    fun isUpdateAvailable_blankOrInvalid_returnsFalse() {
        assertFalse(AppVersionComparator.isUpdateAvailable("", "v3.31.0"))
        assertFalse(AppVersionComparator.isUpdateAvailable("3.31.0", ""))
        assertFalse(AppVersionComparator.isUpdateAvailable("   ", "   "))
    }
}
