/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import android.content.Context
import com.nextcloud.client.account.UserAccountManager
import com.owncloud.android.lib.common.network.CloudflareAuthProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareAccessManager @Inject constructor(
    private val context: Context,
    private val secureStorage: CloudflareSecureStorage,
    private val userAccountManager: UserAccountManager?,
    private val connectionTester: CloudflareConnectionTester = CloudflareConnectionTester(),
    private val notificationHelper: CloudflareNotificationHelper = CloudflareNotificationHelper(context)
) : CloudflareAccessAuthProvider,
    CloudflareAuthProvider {

    private val listeners = CopyOnWriteArraySet<CloudflareAuthListener>()
    private val providerCache = ConcurrentHashMap<String, CloudflareAccessAuthProvider>()

    override fun getHeaders(url: String?): Map<String, String> {
        if (url.isNullOrBlank()) {
            return emptyMap()
        }

        val accountName = resolveAccountForUrl(url)
        val config = if (accountName != null) {
            secureStorage.loadConfig(accountName)
        } else {
            val staged = secureStorage.loadStagedConfig()
            if (staged.isEnabled && staged.matchesOrigin(url)) {
                staged
            } else {
                null
            }
        } ?: return emptyMap()

        if (!config.isEnabled || !config.matchesOrigin(url)) {
            return emptyMap()
        }

        val session = if (accountName != null) {
            secureStorage.loadSession(accountName)
        } else {
            secureStorage.loadStagedSession()
        }

        val provider = getProvider(config, session)
        return provider.getHeaders(url)
    }

    override fun isSessionExpired(): Boolean {
        val currentAccount = userAccountManager?.user?.takeUnless { it.isAnonymous }?.accountName
        val session = if (currentAccount != null) {
            secureStorage.loadSession(currentAccount)
        } else {
            secureStorage.loadStagedSession()
        }
        return session?.isExpired() ?: false
    }

    override fun getMode(): CloudflareAccessMode {
        val currentAccount = userAccountManager?.user?.takeUnless { it.isAnonymous }?.accountName
        return getConfig(currentAccount).mode
    }

    override fun getAuthHeaders(url: String): Map<String, String> = getHeaders(url)

    override fun onAuthenticationChallenge(statusCode: Int, url: String?) {
        val safeUrl = url.orEmpty()
        val accountName = resolveAccountForUrl(safeUrl)
        for (listener in listeners) {
            listener.onAuthenticationChallenge(accountName, statusCode, safeUrl)
        }

        if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
            if (accountName != null) {
                val config = secureStorage.loadConfig(accountName)
                if (config.isEnabled && config.mode == CloudflareAccessMode.INTERACTIVE) {
                    notificationHelper.showReauthenticationNotification(accountName, config.serverUrl)
                }
            }
        }
    }

    fun resolveAccountForUrl(url: String): String? {
        val currentAccount = userAccountManager?.user?.takeUnless { it.isAnonymous }?.accountName
        if (currentAccount != null) {
            val currentConfig = secureStorage.loadConfig(currentAccount)
            if (currentConfig.isEnabled && currentConfig.matchesOrigin(url)) {
                return currentAccount
            }
        }

        val allConfigs = secureStorage.getAllAccountConfigs()
        for ((account, config) in allConfigs) {
            if (config.matchesOrigin(url)) {
                return account
            }
        }
        return null
    }

    private fun getProvider(config: CloudflareAccessConfig, session: CloudflareSession?): CloudflareAccessAuthProvider =
        when (config.mode) {
            CloudflareAccessMode.DISABLED -> NoCloudflareAuthProvider
            CloudflareAccessMode.SERVICE_TOKEN -> CloudflareServiceTokenAuthProvider(config)
            CloudflareAccessMode.INTERACTIVE -> CloudflareInteractiveAuthProvider(config, session)
        }

    fun getConfig(accountName: String?): CloudflareAccessConfig = if (accountName != null) {
        secureStorage.loadConfig(accountName)
    } else {
        secureStorage.loadStagedConfig()
    }

    fun saveConfig(accountName: String?, config: CloudflareAccessConfig) {
        if (accountName != null) {
            secureStorage.saveConfig(accountName, config)
            providerCache.remove(accountName)
        } else {
            secureStorage.saveStagedConfig(config)
        }
    }

    fun removeConfig(accountName: String?) {
        if (accountName != null) {
            secureStorage.removeAccount(accountName)
            providerCache.remove(accountName)
        } else {
            secureStorage.clearStaged()
        }
    }

    fun getStagedConfig(): CloudflareAccessConfig = secureStorage.loadStagedConfig()

    fun saveStagedConfig(config: CloudflareAccessConfig) {
        secureStorage.saveStagedConfig(config)
    }

    fun clearStagedConfig() {
        secureStorage.clearStaged()
    }

    fun promoteStaged(accountName: String) {
        secureStorage.promoteStaged(accountName)
    }

    fun getInteractiveSession(accountName: String?): CloudflareSession? = if (accountName != null) {
        secureStorage.loadSession(accountName)
    } else {
        secureStorage.loadStagedSession()
    }

    fun saveInteractiveSession(accountName: String?, session: CloudflareSession) {
        if (accountName != null) {
            secureStorage.saveSession(accountName, session)
            providerCache.remove(accountName)
        } else {
            secureStorage.saveStagedSession(session)
        }
    }

    fun clearInteractiveSession(accountName: String?) {
        if (accountName != null) {
            secureStorage.clearSession(accountName)
            providerCache.remove(accountName)
        } else {
            secureStorage.clearStaged()
        }
    }

    suspend fun testConnection(
        serverUrl: String,
        config: CloudflareAccessConfig,
        session: CloudflareSession? = null
    ): ConnectionTestResult = connectionTester.testConnection(serverUrl, config, session)

    fun addListener(listener: CloudflareAuthListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CloudflareAuthListener) {
        listeners.remove(listener)
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        @Volatile
        private var instance: CloudflareAccessManager? = null

        fun from(context: Context): CloudflareAccessManager = instance ?: synchronized(this) {
            instance ?: CloudflareAccessManager(
                context.applicationContext,
                CloudflareSecureStorage(context.applicationContext),
                null
            ).also { instance = it }
        }

        fun initialize(manager: CloudflareAccessManager) {
            instance = manager
        }
    }
}
