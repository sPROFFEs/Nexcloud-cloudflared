/*
 * Nextcloud Android Library
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: MIT
 */
package com.owncloud.android.lib.common.network;

import java.util.Map;

/**
 * Provider interface for Cloudflare Zero Trust Access authentication.
 */
public interface CloudflareAuthProvider {
    /**
     * Return headers to be attached to the request for the given URL,
     * or empty map / null if none should be attached.
     *
     * @param url Request target URL.
     * @return Map of header name -> header value.
     */
    Map<String, String> getHeaders(String url);

    /**
     * Check whether the interactive Cloudflare session has expired.
     */
    boolean isSessionExpired();

    /**
     * Notify that an authentication challenge (401/403 or redirect) was received.
     *
     * @param statusCode HTTP status code.
     * @param url Request target URL.
     */
    void onAuthenticationChallenge(int statusCode, String url);
}
