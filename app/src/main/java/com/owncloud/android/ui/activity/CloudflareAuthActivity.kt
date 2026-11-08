/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nextcloud.client.di.Injectable
import com.nextcloud.client.network.cloudflare.CloudflareAccessManager
import com.nextcloud.client.network.cloudflare.CloudflareAccessMode
import com.nextcloud.client.network.cloudflare.CloudflareJwtParser
import com.nextcloud.client.network.cloudflare.CloudflareSession
import com.owncloud.android.R
import javax.inject.Inject

class CloudflareAuthActivity :
    AppCompatActivity(),
    Injectable {

    @Inject
    lateinit var cloudflareAccessManager: CloudflareAccessManager

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator

    private var serverUrl: String = ""
    private var accountName: String? = null
    private var authCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloudflare_auth)

        if (!::cloudflareAccessManager.isInitialized) {
            cloudflareAccessManager = CloudflareAccessManager.from(this)
        }

        serverUrl = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty()
        accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME)
        val initialUrl = intent.getStringExtra(EXTRA_AUTH_URL)?.takeIf { it.isNotBlank() } ?: serverUrl

        if (serverUrl.isBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setupToolbar()
        setupWebView()

        webView.loadUrl(initialUrl)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        progressBar = findViewById(R.id.progress_bar)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.cloudflare_webview)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.databaseEnabled = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < PROGRESS_COMPLETE) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = webView
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return true

                if (scheme != SCHEME_HTTPS && scheme != SCHEME_HTTP) {
                    return true
                }

                checkAndExtractSession(uri.toString())
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { checkAndExtractSession(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { checkAndExtractSession(it) }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // Fail-safe: never bypass SSL verification
                handler?.cancel()
            }
        }
    }

    private fun checkAndExtractSession(currentUrl: String) {
        if (authCompleted) return

        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(serverUrl) ?: cookieManager.getCookie(currentUrl)

        if (cookieString.isNullOrBlank() || !cookieString.contains(COOKIE_CF_AUTH)) {
            return
        }

        val cookies = parseCookieString(cookieString)
        val cfAuth = cookies[COOKIE_CF_AUTH] ?: return

        authCompleted = true
        val expirationMs = CloudflareJwtParser.parseExpiration(cfAuth)
        val session = CloudflareSession(
            cookies = cookies,
            expirationTimestampMs = expirationMs
        )

        cloudflareAccessManager.saveInteractiveSession(accountName, session)

        val existingConfig = cloudflareAccessManager.getConfig(accountName)
        if (existingConfig.mode != CloudflareAccessMode.INTERACTIVE) {
            val updatedConfig = existingConfig.copy(
                mode = CloudflareAccessMode.INTERACTIVE,
                serverUrl = serverUrl
            )
            cloudflareAccessManager.saveConfig(accountName, updatedConfig)
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_SERVER_URL, serverUrl)
            putExtra(EXTRA_ACCOUNT_NAME, accountName)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        val cookieMap = mutableMapOf<String, String>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isNotEmpty()) {
                    cookieMap[name] = value
                }
            }
        }
        return cookieMap
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearHistory()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        const val EXTRA_AUTH_URL = "extra_auth_url"

        private const val PROGRESS_COMPLETE = 100
        private const val SCHEME_HTTPS = "https"
        private const val SCHEME_HTTP = "http"
        private const val COOKIE_CF_AUTH = "CF_Authorization"

        fun createIntent(
            context: Context,
            serverUrl: String,
            accountName: String? = null,
            authUrl: String? = null
        ): Intent = Intent(context, CloudflareAuthActivity::class.java).apply {
            putExtra(EXTRA_SERVER_URL, serverUrl)
            putExtra(EXTRA_ACCOUNT_NAME, accountName)
            putExtra(EXTRA_AUTH_URL, authUrl)
        }
    }
}
