/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.authentication

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nextcloud.client.network.cloudflare.CloudflareAccessConfig
import com.nextcloud.client.network.cloudflare.CloudflareAccessManager
import com.nextcloud.client.network.cloudflare.CloudflareAccessMode
import com.nextcloud.client.network.cloudflare.FailureOrigin
import com.owncloud.android.R
import com.owncloud.android.databinding.ViewCloudflareAccessConfigBinding
import com.owncloud.android.ui.activity.CloudflareAuthActivity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CloudflareAccountSetupHelper(
    private val activity: AppCompatActivity,
    private val cloudflareAccessManager: CloudflareAccessManager
) {
    private var binding: ViewCloudflareAccessConfigBinding? = null
    private var hostUrlEditText: EditText? = null

    private val authLauncher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                updateInteractiveStatus()
                Toast.makeText(activity, R.string.cloudflare_access_auth_success, Toast.LENGTH_SHORT).show()
            }
        }

    fun bind(configBinding: ViewCloudflareAccessConfigBinding?, hostUrlInput: EditText) {
        if (configBinding == null) return
        binding = configBinding
        hostUrlEditText = hostUrlInput

        val staged = cloudflareAccessManager.getStagedConfig()

        initModeSelection(configBinding, staged)
        initServiceTokenActions(configBinding)
        initInteractiveActions(configBinding)
    }

    private fun initModeSelection(configBinding: ViewCloudflareAccessConfigBinding, staged: CloudflareAccessConfig) {
        when (staged.mode) {
            CloudflareAccessMode.DISABLED -> {
                configBinding.cfModeDisabled.isChecked = true
                configBinding.cfServiceTokenContainer.visibility = View.GONE
                configBinding.cfInteractiveContainer.visibility = View.GONE
            }

            CloudflareAccessMode.SERVICE_TOKEN -> {
                configBinding.cfModeServiceToken.isChecked = true
                configBinding.cfServiceTokenContainer.visibility = View.VISIBLE
                configBinding.cfInteractiveContainer.visibility = View.GONE
                configBinding.cfClientIdInput.setText(staged.clientId)
                configBinding.cfClientSecretInput.setText(staged.clientSecret)
            }

            CloudflareAccessMode.INTERACTIVE -> {
                configBinding.cfModeInteractive.isChecked = true
                configBinding.cfServiceTokenContainer.visibility = View.GONE
                configBinding.cfInteractiveContainer.visibility = View.VISIBLE
                updateInteractiveStatus()
            }
        }

        configBinding.cfModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.cf_mode_disabled -> {
                    configBinding.cfServiceTokenContainer.visibility = View.GONE
                    configBinding.cfInteractiveContainer.visibility = View.GONE
                    saveStagedMode(CloudflareAccessMode.DISABLED)
                }

                R.id.cf_mode_service_token -> {
                    configBinding.cfServiceTokenContainer.visibility = View.VISIBLE
                    configBinding.cfInteractiveContainer.visibility = View.GONE
                }

                R.id.cf_mode_interactive -> {
                    configBinding.cfServiceTokenContainer.visibility = View.GONE
                    configBinding.cfInteractiveContainer.visibility = View.VISIBLE
                    updateInteractiveStatus()
                }
            }
        }
    }

    private fun initServiceTokenActions(configBinding: ViewCloudflareAccessConfigBinding) {
        configBinding.cfBtnTestConnection.setOnClickListener {
            testServiceTokenConnection()
        }

        configBinding.cfBtnSaveConfig.setOnClickListener {
            saveServiceTokenConfig()
        }

        configBinding.cfBtnRemoveCredentials.setOnClickListener {
            removeCredentials()
        }
    }

    private fun initInteractiveActions(configBinding: ViewCloudflareAccessConfigBinding) {
        configBinding.cfBtnAuthenticate.setOnClickListener { startInteractiveLogin() }
        configBinding.cfBtnReauthenticate.setOnClickListener { startInteractiveLogin() }

        configBinding.cfBtnLogout.setOnClickListener {
            cloudflareAccessManager.clearInteractiveSession(null)
            updateInteractiveStatus()
        }
    }

    private fun updateInteractiveStatus() {
        val b = binding ?: return
        val session = cloudflareAccessManager.getInteractiveSession(null)
        if (session == null) {
            b.cfTextAuthStatus.setText(R.string.cloudflare_access_status_not_authenticated)
            b.cfTextSessionExpiration.visibility = View.GONE
            b.cfBtnAuthenticate.visibility = View.VISIBLE
            b.cfBtnReauthenticate.visibility = View.GONE
        } else if (session.isExpired()) {
            b.cfTextAuthStatus.setText(R.string.cloudflare_access_status_expired)
            b.cfTextSessionExpiration.visibility = View.GONE
            b.cfBtnAuthenticate.visibility = View.GONE
            b.cfBtnReauthenticate.visibility = View.VISIBLE
        } else {
            b.cfTextAuthStatus.setText(R.string.cloudflare_access_status_authenticated)
            b.cfBtnAuthenticate.visibility = View.GONE
            b.cfBtnReauthenticate.visibility = View.VISIBLE

            val expMs = session.expirationTimestampMs
            if (expMs != null && expMs > 0) {
                val expStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(expMs))
                b.cfTextSessionExpiration.text = activity.getString(R.string.cloudflare_access_session_expires, expStr)
                b.cfTextSessionExpiration.visibility = View.VISIBLE
            } else {
                b.cfTextSessionExpiration.setText(R.string.cloudflare_access_session_established)
                b.cfTextSessionExpiration.visibility = View.VISIBLE
            }
        }
    }

    private fun testServiceTokenConnection() {
        val b = binding ?: return
        val serverUrl = hostUrlEditText?.text?.toString().orEmpty().trim()
        val clientId = b.cfClientIdInput.text?.toString().orEmpty().trim()
        val clientSecret = b.cfClientSecretInput.text?.toString().orEmpty().trim()

        if (serverUrl.isBlank()) {
            showFeedback(activity.getString(R.string.cloudflare_access_missing_server_url), FailureOrigin.NETWORK)
            return
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            showFeedback(activity.getString(R.string.cloudflare_access_missing_credentials), FailureOrigin.NETWORK)
            return
        }

        val testConfig = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = clientId,
            clientSecret = clientSecret,
            serverUrl = serverUrl
        )

        b.cfTextFeedback.setText(R.string.cloudflare_access_testing)
        b.cfTextFeedback.visibility = View.VISIBLE

        activity.lifecycleScope.launch {
            val result = cloudflareAccessManager.testConnection(serverUrl, testConfig)
            val msg = when (result.failureOrigin) {
                FailureOrigin.NONE -> activity.getString(R.string.cloudflare_access_test_success)

                FailureOrigin.CLOUDFLARE_ACCESS ->
                    activity.getString(R.string.cloudflare_access_test_cf_failure, result.message)

                FailureOrigin.NEXTCLOUD ->
                    activity.getString(R.string.cloudflare_access_test_nc_failure, result.message)

                FailureOrigin.NETWORK ->
                    activity.getString(R.string.cloudflare_access_test_network_failure, result.message)
            }
            showFeedback(msg, result.failureOrigin)
        }
    }

    private fun showFeedback(message: String, origin: FailureOrigin) {
        val b = binding ?: return
        b.cfTextFeedback.text = message
        val colorRes = when (origin) {
            FailureOrigin.NONE -> android.R.color.holo_green_dark
            FailureOrigin.CLOUDFLARE_ACCESS -> android.R.color.holo_red_dark
            FailureOrigin.NEXTCLOUD -> android.R.color.holo_orange_dark
            FailureOrigin.NETWORK -> android.R.color.holo_red_dark
        }
        b.cfTextFeedback.setTextColor(ContextCompat.getColor(activity, colorRes))
        b.cfTextFeedback.visibility = View.VISIBLE
    }

    private fun saveServiceTokenConfig() {
        val b = binding ?: return
        val serverUrl = hostUrlEditText?.text?.toString().orEmpty().trim()
        val clientId = b.cfClientIdInput.text?.toString().orEmpty().trim()
        val clientSecret = b.cfClientSecretInput.text?.toString().orEmpty().trim()

        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = clientId,
            clientSecret = clientSecret,
            serverUrl = serverUrl
        )
        cloudflareAccessManager.saveStagedConfig(config)
        Toast.makeText(activity, R.string.cloudflare_access_saved, Toast.LENGTH_SHORT).show()
    }

    private fun saveStagedMode(mode: CloudflareAccessMode) {
        val serverUrl = hostUrlEditText?.text?.toString().orEmpty().trim()
        val current = cloudflareAccessManager.getStagedConfig()
        cloudflareAccessManager.saveStagedConfig(current.copy(mode = mode, serverUrl = serverUrl))
    }

    private fun removeCredentials() {
        val b = binding ?: return
        cloudflareAccessManager.clearStagedConfig()
        b.cfClientIdInput.text = null
        b.cfClientSecretInput.text = null
        b.cfModeDisabled.isChecked = true
        Toast.makeText(activity, R.string.cloudflare_access_removed, Toast.LENGTH_SHORT).show()
    }

    private fun startInteractiveLogin() {
        val serverUrl = hostUrlEditText?.text?.toString().orEmpty().trim()
        if (serverUrl.isBlank()) {
            showFeedback(activity.getString(R.string.cloudflare_access_missing_server_url), FailureOrigin.NETWORK)
            return
        }

        saveStagedMode(CloudflareAccessMode.INTERACTIVE)
        val intent = CloudflareAuthActivity.createIntent(activity, serverUrl, null)
        authLauncher.launch(intent)
    }
}
