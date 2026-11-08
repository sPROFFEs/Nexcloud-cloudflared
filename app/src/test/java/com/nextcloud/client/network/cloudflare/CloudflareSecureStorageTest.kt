/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class CloudflareSecureStorageTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var storage: CloudflareSecureStorage

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        val mockContext = mock(Context::class.java)
        storage = CloudflareSecureStorage(mockContext, fakePrefs)
    }

    @Test
    fun saveAndLoadConfig_persistsCorrectly() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "my-client-id",
            clientSecret = "my-client-secret",
            serverUrl = "https://cloud.example.com"
        )

        storage.saveConfig("user@example.com", config)
        val loaded = storage.loadConfig("user@example.com")

        assertEquals(CloudflareAccessMode.SERVICE_TOKEN, loaded.mode)
        assertEquals("my-client-id", loaded.clientId)
        assertEquals("my-client-secret", loaded.clientSecret)
        assertEquals("https://cloud.example.com", loaded.serverUrl)
    }

    @Test
    fun saveAndLoadSession_persistsCorrectly() {
        val exp = System.currentTimeMillis() + 3_600_000L
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "jwt-secret-val"),
            expirationTimestampMs = exp
        )

        storage.saveSession("user@example.com", session)
        val loaded = storage.loadSession("user@example.com")

        assertNotNull(loaded)
        assertEquals("jwt-secret-val", loaded?.cookies?.get("CF_Authorization"))
        assertEquals(exp, loaded?.expirationTimestampMs)
    }

    @Test
    fun promoteStaged_transfersConfigAndSessionToTargetAccount() {
        val stagedConfig = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "staged-id",
            clientSecret = "staged-secret",
            serverUrl = "https://cloud.example.com"
        )
        val stagedSession = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "staged-jwt"),
            expirationTimestampMs = 123456L
        )

        storage.saveStagedConfig(stagedConfig)
        storage.saveStagedSession(stagedSession)

        storage.promoteStaged("final_user@example.com")

        // Target account has promoted data
        val accountConfig = storage.loadConfig("final_user@example.com")
        assertEquals("staged-id", accountConfig.clientId)
        val accountSession = storage.loadSession("final_user@example.com")
        assertEquals("staged-jwt", accountSession?.cookies?.get("CF_Authorization"))

        // Staged is cleared
        val remainingStaged = storage.loadStagedConfig()
        assertEquals(CloudflareAccessMode.DISABLED, remainingStaged.mode)
        assertNull(storage.loadStagedSession())
    }

    @Test
    fun removeAccount_clearsBothConfigAndSession() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "id",
            clientSecret = "sec",
            serverUrl = "https://cloud.example.com"
        )
        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "jwt")
        )

        storage.saveConfig("user1", config)
        storage.saveSession("user1", session)

        storage.removeAccount("user1")

        val loadedConfig = storage.loadConfig("user1")
        assertEquals(CloudflareAccessMode.DISABLED, loadedConfig.mode)
        assertNull(storage.loadSession("user1"))
    }

    @Test
    fun getAllAccountConfigs_returnsOnlyEnabledNonStagedConfigs() {
        storage.saveConfig(
            "user1",
            CloudflareAccessConfig(CloudflareAccessMode.SERVICE_TOKEN, "id1", "s1", "https://c1.com")
        )
        storage.saveConfig("user2", CloudflareAccessConfig(CloudflareAccessMode.DISABLED, "", "", "https://c2.com"))
        storage.saveStagedConfig(
            CloudflareAccessConfig(CloudflareAccessMode.SERVICE_TOKEN, "staged", "s", "https://staged.com")
        )

        val all = storage.getAllAccountConfigs()
        assertEquals(1, all.size)
        assertTrue(all.containsKey("user1"))
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
    }

    private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String): SharedPreferences.Editor {
            toRemove.add(key)
            temp.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            data.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            toRemove.forEach { data.remove(it) }
            data.putAll(temp)
        }
    }
}
