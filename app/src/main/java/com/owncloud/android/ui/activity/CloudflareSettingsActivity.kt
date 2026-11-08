/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.nextcloud.client.network.cloudflare.CloudflareAccessConfig
import com.nextcloud.client.network.cloudflare.CloudflareAccessManager
import com.nextcloud.client.network.cloudflare.CloudflareAccessMode
import com.nextcloud.client.network.cloudflare.FailureOrigin
import com.owncloud.android.R
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CloudflareSettingsActivity : AppCompatActivity() {

    private lateinit var cloudflareAccessManager: CloudflareAccessManager

    private var accountName: String? = null
    private var serverUrl: String = ""

    private lateinit var modeGroup: RadioGroup
    private lateinit var serviceTokenContainer: LinearLayout
    private lateinit var interactiveContainer: LinearLayout
    private lateinit var clientIdEdit: TextInputEditText
    private lateinit var clientSecretEdit: TextInputEditText
    private lateinit var textAuthStatus: TextView
    private lateinit var textSessionExpiration: TextView
    private lateinit var btnAuthenticate: MaterialButton
    private lateinit var btnReauthenticate: MaterialButton
    private lateinit var textFeedback: TextView

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            updateInteractiveUi()
            Toast.makeText(this, R.string.cloudflare_access_auth_success, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloudflare_settings)

        cloudflareAccessManager = CloudflareAccessManager.from(this)

        accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME)
        serverUrl = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty()

        initViews()
        loadConfiguration()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        modeGroup = findViewById(R.id.cloudflare_mode_group)
        serviceTokenContainer = findViewById(R.id.service_token_container)
        interactiveContainer = findViewById(R.id.interactive_container)
        clientIdEdit = findViewById(R.id.client_id_edit)
        clientSecretEdit = findViewById(R.id.client_secret_edit)
        textAuthStatus = findViewById(R.id.text_auth_status)
        textSessionExpiration = findViewById(R.id.text_session_expiration)
        btnAuthenticate = findViewById(R.id.btn_authenticate)
        btnReauthenticate = findViewById(R.id.btn_reauthenticate)
        textFeedback = findViewById(R.id.text_result_feedback)

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mode_disabled -> {
                    serviceTokenContainer.visibility = View.GONE
                    interactiveContainer.visibility = View.GONE
                    saveDisabledMode()
                }

                R.id.mode_service_token -> {
                    serviceTokenContainer.visibility = View.VISIBLE
                    interactiveContainer.visibility = View.GONE
                }

                R.id.mode_interactive -> {
                    serviceTokenContainer.visibility = View.GONE
                    interactiveContainer.visibility = View.VISIBLE
                    updateInteractiveUi()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_test_connection).setOnClickListener {
            testServiceTokenConnection()
        }

        findViewById<MaterialButton>(R.id.btn_save_config).setOnClickListener {
            saveServiceTokenConfig()
        }

        findViewById<MaterialButton>(R.id.btn_remove_credentials).setOnClickListener {
            removeCredentials()
        }

        btnAuthenticate.setOnClickListener { startInteractiveAuth() }
        btnReauthenticate.setOnClickListener { startInteractiveAuth() }

        findViewById<MaterialButton>(R.id.btn_logout_cloudflare).setOnClickListener {
            cloudflareAccessManager.clearInteractiveSession(accountName)
            updateInteractiveUi()
        }
    }

    private fun loadConfiguration() {
        val config = cloudflareAccessManager.getConfig(accountName)
        if (serverUrl.isBlank() && config.serverUrl.isNotBlank()) {
            serverUrl = config.serverUrl
        }

        when (config.mode) {
            CloudflareAccessMode.DISABLED -> {
                findViewById<RadioButton>(R.id.mode_disabled).isChecked = true
            }

            CloudflareAccessMode.SERVICE_TOKEN -> {
                findViewById<RadioButton>(R.id.mode_service_token).isChecked = true
                clientIdEdit.setText(config.clientId)
                clientSecretEdit.setText(config.clientSecret)
            }

            CloudflareAccessMode.INTERACTIVE -> {
                findViewById<RadioButton>(R.id.mode_interactive).isChecked = true
                updateInteractiveUi()
            }
        }
    }

    private fun updateInteractiveUi() {
        val session = cloudflareAccessManager.getInteractiveSession(accountName)
        if (session == null) {
            textAuthStatus.setText(R.string.cloudflare_access_status_not_authenticated)
            textSessionExpiration.visibility = View.GONE
            btnAuthenticate.visibility = View.VISIBLE
            btnReauthenticate.visibility = View.GONE
        } else if (session.isExpired()) {
            textAuthStatus.setText(R.string.cloudflare_access_status_expired)
            textSessionExpiration.visibility = View.GONE
            btnAuthenticate.visibility = View.GONE
            btnReauthenticate.visibility = View.VISIBLE
        } else {
            textAuthStatus.setText(R.string.cloudflare_access_status_authenticated)
            btnAuthenticate.visibility = View.GONE
            btnReauthenticate.visibility = View.VISIBLE

            val expMs = session.expirationTimestampMs
            if (expMs != null && expMs > 0) {
                val expStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(expMs))
                textSessionExpiration.text = getString(R.string.cloudflare_access_session_expires, expStr)
                textSessionExpiration.visibility = View.VISIBLE
            } else {
                textSessionExpiration.setText(R.string.cloudflare_access_session_established)
                textSessionExpiration.visibility = View.VISIBLE
            }
        }
    }

    private fun testServiceTokenConnection() {
        val clientId = clientIdEdit.text?.toString().orEmpty().trim()
        val clientSecret = clientSecretEdit.text?.toString().orEmpty().trim()

        if (clientId.isBlank() || clientSecret.isBlank()) {
            showFeedback(getString(R.string.cloudflare_access_missing_credentials), FailureOrigin.NETWORK)
            return
        }
        if (serverUrl.isBlank()) {
            showFeedback(getString(R.string.cloudflare_access_missing_server_url), FailureOrigin.NETWORK)
            return
        }

        val testConfig = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = clientId,
            clientSecret = clientSecret,
            serverUrl = serverUrl
        )

        textFeedback.setText(R.string.cloudflare_access_testing)
        textFeedback.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = cloudflareAccessManager.testConnection(serverUrl, testConfig)
            val msg = when (result.failureOrigin) {
                FailureOrigin.NONE -> getString(R.string.cloudflare_access_test_success)

                FailureOrigin.CLOUDFLARE_ACCESS ->
                    getString(R.string.cloudflare_access_test_cf_failure, result.message)

                FailureOrigin.NEXTCLOUD ->
                    getString(R.string.cloudflare_access_test_nc_failure, result.message)

                FailureOrigin.NETWORK ->
                    getString(R.string.cloudflare_access_test_network_failure, result.message)
            }
            showFeedback(msg, result.failureOrigin)
        }
    }

    private fun showFeedback(message: String, origin: FailureOrigin) {
        textFeedback.text = message
        val colorRes = when (origin) {
            FailureOrigin.NONE -> android.R.color.holo_green_dark
            FailureOrigin.CLOUDFLARE_ACCESS -> android.R.color.holo_red_dark
            FailureOrigin.NEXTCLOUD -> android.R.color.holo_orange_dark
            FailureOrigin.NETWORK -> android.R.color.holo_red_dark
        }
        textFeedback.setTextColor(ContextCompat.getColor(this, colorRes))
        textFeedback.visibility = View.VISIBLE
    }

    private fun saveServiceTokenConfig() {
        val clientId = clientIdEdit.text?.toString().orEmpty().trim()
        val clientSecret = clientSecretEdit.text?.toString().orEmpty().trim()

        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.SERVICE_TOKEN,
            clientId = clientId,
            clientSecret = clientSecret,
            serverUrl = serverUrl
        )
        cloudflareAccessManager.saveConfig(accountName, config)
        Toast.makeText(this, R.string.cloudflare_access_saved, Toast.LENGTH_SHORT).show()
    }

    private fun saveDisabledMode() {
        val config = CloudflareAccessConfig(
            mode = CloudflareAccessMode.DISABLED,
            serverUrl = serverUrl
        )
        cloudflareAccessManager.saveConfig(accountName, config)
    }

    private fun removeCredentials() {
        cloudflareAccessManager.removeConfig(accountName)
        clientIdEdit.text = null
        clientSecretEdit.text = null
        findViewById<RadioButton>(R.id.mode_disabled).isChecked = true
        Toast.makeText(this, R.string.cloudflare_access_removed, Toast.LENGTH_SHORT).show()
    }

    private fun startInteractiveAuth() {
        if (serverUrl.isBlank()) {
            showFeedback(getString(R.string.cloudflare_access_missing_server_url), FailureOrigin.NETWORK)
            return
        }
        val intent = CloudflareAuthActivity.createIntent(this, serverUrl, accountName)
        authLauncher.launch(intent)
    }

    companion object {
        const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        const val EXTRA_SERVER_URL = "extra_server_url"

        fun createIntent(context: Context, accountName: String?, serverUrl: String): Intent =
            Intent(context, CloudflareSettingsActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_NAME, accountName)
                putExtra(EXTRA_SERVER_URL, serverUrl)
            }
    }
}
