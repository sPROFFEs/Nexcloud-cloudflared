/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareConnectionTesterTest {

    private fun createTesterWithResponse(
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap()
    ): CloudflareConnectionTester {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val responseBuilder = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Response")
                    .body(body.toResponseBody("text/html".toMediaType()))

                headers.forEach { (name, value) ->
                    responseBuilder.addHeader(name, value)
                }

                responseBuilder.build()
            }
            .build()

        return CloudflareConnectionTester(client)
    }

    @Test
    fun testConnection_blankUrl_returnsNetworkFailure() = runBlocking {
        val tester = CloudflareConnectionTester()
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("", config)

        assertFalse(result.isSuccess)
        assertEquals(FailureOrigin.NETWORK, result.failureOrigin)
    }

    @Test
    fun testConnection_nextcloudInstalledBody_returnsSuccess() = runBlocking {
        val tester = createTesterWithResponse(200, "{\"installed\":true,\"version\":\"29.0.0\"}")
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("https://cloud.example.com", config)

        assertTrue(result.isSuccess)
        assertEquals(FailureOrigin.NONE, result.failureOrigin)
    }

    @Test
    fun testConnection_cloudflareIntercepted200_returnsCfFailure() = runBlocking {
        val tester = createTesterWithResponse(200, "<html><title>Cloudflare Access</title></html>")
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("https://cloud.example.com", config)

        assertFalse(result.isSuccess)
        assertEquals(FailureOrigin.CLOUDFLARE_ACCESS, result.failureOrigin)
    }

    @Test
    fun testConnection_cloudflareChallenge401_returnsCfFailure() = runBlocking {
        val tester = createTesterWithResponse(
            401,
            "Unauthorized",
            mapOf("cf-ray" to "1234567890abcdef-FRA")
        )
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("https://cloud.example.com", config)

        assertFalse(result.isSuccess)
        assertEquals(FailureOrigin.CLOUDFLARE_ACCESS, result.failureOrigin)
    }

    @Test
    fun testConnection_nextcloudAuthRequired401_returnsSuccessWithNcOrigin() = runBlocking {
        val tester = createTesterWithResponse(401, "{\"message\":\"Current user is not logged in\"}")
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("https://cloud.example.com", config)

        assertTrue(result.isSuccess)
        assertEquals(FailureOrigin.NEXTCLOUD, result.failureOrigin)
    }

    @Test
    fun testConnection_cloudflareRedirect302_returnsCfFailure() = runBlocking {
        val tester = createTesterWithResponse(
            302,
            "",
            mapOf("Location" to "https://myteam.cloudflareaccess.com/cdn-cgi/access/login")
        )
        val config = CloudflareAccessConfig(mode = CloudflareAccessMode.DISABLED)
        val result = tester.testConnection("https://cloud.example.com", config)

        assertFalse(result.isSuccess)
        assertEquals(FailureOrigin.CLOUDFLARE_ACCESS, result.failureOrigin)
    }
}
