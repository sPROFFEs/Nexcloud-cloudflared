/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Android Keystore-backed encrypted storage for Cloudflare Zero Trust credentials and sessions.
 */
class CloudflareSecureStorage @Inject constructor(
    private val context: Context,
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) {
    private val secretKey: SecretKey by lazy { getOrCreateSecretKey() }

    fun saveConfig(accountName: String, config: CloudflareAccessConfig) {
        val json = JSONObject().apply {
            put(KEY_MODE, config.mode.key)
            put(KEY_CLIENT_ID, config.clientId ?: "")
            put(KEY_CLIENT_SECRET, config.clientSecret ?: "")
            put(KEY_ORIGIN, config.serverUrl)
        }.toString()

        val encrypted = encrypt(json)
        preferences.edit().putString(prefixKey(accountName, SUFFIX_CONFIG), encrypted).apply()
    }

    fun loadConfig(accountName: String): CloudflareAccessConfig {
        val encrypted = preferences.getString(prefixKey(accountName, SUFFIX_CONFIG), null)
            ?: return CloudflareAccessConfig()

        val jsonStr = decrypt(encrypted) ?: return CloudflareAccessConfig()
        return try {
            val json = JSONObject(jsonStr)
            CloudflareAccessConfig(
                mode = CloudflareAccessMode.fromKey(json.optString(KEY_MODE)),
                clientId = json.optString(KEY_CLIENT_ID).takeIf { it.isNotBlank() },
                clientSecret = json.optString(KEY_CLIENT_SECRET).takeIf { it.isNotBlank() },
                serverUrl = json.optString(KEY_ORIGIN)
            )
        } catch (_: Exception) {
            CloudflareAccessConfig()
        }
    }

    fun saveSession(accountName: String, session: CloudflareSession) {
        val cookiesJson = JSONObject()
        for ((k, v) in session.cookies) {
            cookiesJson.put(k, v)
        }

        val json = JSONObject().apply {
            put(KEY_COOKIES, cookiesJson)
            if (session.expirationTimestampMs != null) {
                put(KEY_EXP, session.expirationTimestampMs)
            }
            put(KEY_ESTABLISHED, session.sessionEstablishedAtEpochMs)
        }.toString()

        val encrypted = encrypt(json)
        preferences.edit().putString(prefixKey(accountName, SUFFIX_SESSION), encrypted).apply()
    }

    fun loadSession(accountName: String): CloudflareSession? {
        val encrypted = preferences.getString(prefixKey(accountName, SUFFIX_SESSION), null)
            ?: return null

        val jsonStr = decrypt(encrypted) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val cookiesJson = json.optJSONObject(KEY_COOKIES)
            val cookiesMap = mutableMapOf<String, String>()
            if (cookiesJson != null) {
                for (key in cookiesJson.keys()) {
                    cookiesMap[key] = cookiesJson.getString(key)
                }
            }
            val exp = if (json.has(KEY_EXP)) json.getLong(KEY_EXP) else null
            val established = json.optLong(KEY_ESTABLISHED, System.currentTimeMillis())
            CloudflareSession(cookiesMap, exp, established)
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession(accountName: String) {
        preferences.edit().remove(prefixKey(accountName, SUFFIX_SESSION)).apply()
    }

    fun removeAccount(accountName: String) {
        preferences.edit()
            .remove(prefixKey(accountName, SUFFIX_CONFIG))
            .remove(prefixKey(accountName, SUFFIX_SESSION))
            .apply()
    }

    fun saveStagedConfig(config: CloudflareAccessConfig) {
        saveConfig(STAGED_ACCOUNT_NAME, config)
    }

    fun loadStagedConfig(): CloudflareAccessConfig = loadConfig(STAGED_ACCOUNT_NAME)

    fun saveStagedSession(session: CloudflareSession) {
        saveSession(STAGED_ACCOUNT_NAME, session)
    }

    fun loadStagedSession(): CloudflareSession? = loadSession(STAGED_ACCOUNT_NAME)

    fun clearStaged() {
        removeAccount(STAGED_ACCOUNT_NAME)
    }

    fun getAllAccountConfigs(): Map<String, CloudflareAccessConfig> {
        val result = mutableMapOf<String, CloudflareAccessConfig>()
        val suffixWithUnderscore = "_$SUFFIX_CONFIG"
        val allKeys = preferences.all.keys
        for (key in allKeys) {
            if (key.endsWith(suffixWithUnderscore)) {
                val account = key.removeSuffix(suffixWithUnderscore)
                if (account != STAGED_ACCOUNT_NAME) {
                    val config = loadConfig(account)
                    if (config.isEnabled) {
                        result[account] = config
                    }
                }
            }
        }
        return result
    }

    fun promoteStaged(accountName: String) {
        val config = loadStagedConfig()
        if (config.isEnabled) {
            saveConfig(accountName, config)
        }
        val session = loadStagedSession()
        if (session != null) {
            saveSession(accountName, session)
        }
        clearStaged()
    }

    private fun encrypt(plainText: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        Base64.getEncoder().encodeToString(combined)
    } catch (_: Exception) {
        Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size < GCM_IV_LENGTH_BYTES) {
                return null
            }
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                val raw = Base64.getDecoder().decode(encryptedBase64)
                String(raw, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    } catch (_: Exception) {
        val fallbackGen = KeyGenerator.getInstance("AES")
        fallbackGen.init(KEY_SIZE_BITS)
        fallbackGen.generateKey()
    }

    private fun prefixKey(accountName: String, suffix: String): String = "${accountName.lowercase()}_$suffix"

    companion object {
        private const val PREFS_NAME = "nc_cf_access_vault"
        private const val KEY_ALIAS = "CloudflareAccessMasterKey"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256

        const val STAGED_ACCOUNT_NAME = "__staged_login__"

        private const val SUFFIX_CONFIG = "config"
        private const val SUFFIX_SESSION = "session"

        private const val KEY_MODE = "mode"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val KEY_ORIGIN = "origin"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_EXP = "exp"
        private const val KEY_ESTABLISHED = "established"
    }
}
