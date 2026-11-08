/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import android.content.Context
import android.content.SharedPreferences
import com.nextcloud.client.account.User
import com.nextcloud.client.account.UserAccountManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CloudflareAccessManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockAccountManager: UserAccountManager
    private lateinit var fakePrefs: FakePrefs
    private lateinit var secureStorage: CloudflareSecureStorage
    private lateinit var manager: CloudflareAccessManager

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAccountManager = mock(UserAccountManager::class.java)
        fakePrefs = FakePrefs()
        secureStorage = CloudflareSecureStorage(mockContext, fakePrefs)
        manager = CloudflareAccessManager(
            context = mockContext,
            secureStorage = secureStorage,
            userAccountManager = mockAccountManager
        )
    }

    @Test
    fun getHeaders_matchingAccountOrigin_returnsServiceTokenHeaders() {
        val mockUser = mock(User::class.java)
        `when`(mockUser.accountName).thenReturn("user@example.com")
        `when`(mockUser.isAnonymous).thenReturn(false)
        `when`(mockAccountManager.user).thenReturn(mockUser)

        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            serverUrl = "https://cloud.example.com"
        )
        secureStorage.saveConfig("user@example.com", config)

        val headers = manager.getHeaders("https://cloud.example.com/status.php")

        assertEquals(2, headers.size)
        assertEquals("test-client-id", headers["CF-Access-Client-Id"])
        assertEquals("test-client-secret", headers["CF-Access-Client-Secret"])
    }

    @Test
    fun getHeaders_matchingStagedOrigin_returnsStagedHeadersWhenNoAccount() {
        `when`(mockAccountManager.user).thenReturn(null)

        val staged = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "staged-id",
            clientSecret = "staged-secret",
            serverUrl = "https://staging.example.com"
        )
        secureStorage.saveStagedConfig(staged)

        val headers = manager.getHeaders("https://staging.example.com/index.php/login/v2")

        assertEquals(2, headers.size)
        assertEquals("staged-id", headers["CF-Access-Client-Id"])
        assertEquals("staged-secret", headers["CF-Access-Client-Secret"])
    }

    @Test
    fun getHeaders_mismatchedOrigin_returnsEmptyMap() {
        val mockUser = mock(User::class.java)
        `when`(mockUser.accountName).thenReturn("user@example.com")
        `when`(mockUser.isAnonymous).thenReturn(false)
        `when`(mockAccountManager.user).thenReturn(mockUser)

        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            serverUrl = "https://cloud.example.com"
        )
        secureStorage.saveConfig("user@example.com", config)

        val headers = manager.getHeaders("https://unrelated-domain.com/status.php")
        assertTrue(headers.isEmpty())
    }

    @Test
    fun onAuthenticationChallenge_notifiesRegisteredListeners() {
        var notifiedCode = 0
        var notifiedUrl = ""

        val listener = object : CloudflareAuthListener {
            override fun onSessionExpired(accountName: String?) {}
            override fun onAuthenticationChallenge(accountName: String?, statusCode: Int, url: String) {
                notifiedCode = statusCode
                notifiedUrl = url
            }
        }

        manager.addListener(listener)
        manager.onAuthenticationChallenge(401, "https://cloud.example.com/api")

        assertEquals(401, notifiedCode)
        assertEquals("https://cloud.example.com/api", notifiedUrl)

        manager.removeListener(listener)
    }

    @Test
    fun isSessionExpired_withExpiredSession_returnsTrue() {
        val mockUser = mock(User::class.java)
        `when`(mockUser.accountName).thenReturn("user@example.com")
        `when`(mockUser.isAnonymous).thenReturn(false)
        `when`(mockAccountManager.user).thenReturn(mockUser)

        val session = CloudflareSession(
            cookies = mapOf("CF_Authorization" to "jwt"),
            expirationTimestampMs = System.currentTimeMillis() - 100_000L
        )
        secureStorage.saveSession("user@example.com", session)

        assertTrue(manager.isSessionExpired())
    }

    private class FakePrefs : SharedPreferences {
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
