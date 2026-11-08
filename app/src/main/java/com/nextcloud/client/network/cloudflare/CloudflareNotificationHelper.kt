/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.network.cloudflare

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.owncloud.android.R
import com.owncloud.android.ui.activity.CloudflareSettingsActivity
import com.owncloud.android.ui.notifications.NotificationUtils

class CloudflareNotificationHelper(private val context: Context) {

    fun showReauthenticationNotification(accountName: String, serverUrl: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val intent = Intent(context, CloudflareSettingsActivity::class.java).apply {
            putExtra(CloudflareSettingsActivity.EXTRA_ACCOUNT_NAME, accountName)
            putExtra(CloudflareSettingsActivity.EXTRA_SERVER_URL, serverUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.cloudflare_reauth_notification_title)
        val text = context.getString(R.string.cloudflare_reauth_notification_text, accountName)

        val notification = NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val NOTIFICATION_ID = 200_001
        private const val NOTIFICATION_REQUEST_CODE = 200_002
    }
}
