/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.updater

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppUpdateDialog {

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, updater: GitHubAppUpdater = GitHubAppUpdater()) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val textStatus = dialogView.findViewById<TextView>(R.id.update_status_text)
        val textDetails = dialogView.findViewById<TextView>(R.id.update_details_text)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.update_progress_bar)

        textStatus.setText(R.string.updater_checking)
        textDetails.visibility = View.GONE
        progressBar.isIndeterminate = true
        progressBar.visibility = View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.updater_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        dialog.show()

        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)
        scope.launch {
            when (val result = updater.checkForUpdates()) {
                is AppUpdateResult.UpToDate -> {
                    progressBar.visibility = View.GONE
                    textStatus.text = activity.getString(R.string.updater_up_to_date, result.currentVersion)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setText(R.string.common_ok)
                }

                is AppUpdateResult.Error -> {
                    progressBar.visibility = View.GONE
                    textStatus.text = result.message
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setText(R.string.common_ok)
                }

                is AppUpdateResult.Available -> {
                    progressBar.visibility = View.GONE
                    textStatus.text = activity.getString(R.string.updater_update_available, result.release.tagName)
                    if (result.release.body.isNotBlank()) {
                        textDetails.text = result.release.body
                        textDetails.visibility = View.VISIBLE
                    }

                    dialog.setButton(
                        AlertDialog.BUTTON_POSITIVE,
                        activity.getString(R.string.updater_download_and_install)
                    ) { _, _ ->
                        startDownload(activity, updater, result.apkAsset.browserDownloadUrl)
                    }
                }
            }
        }
    }

    private fun startDownload(activity: Activity, updater: GitHubAppUpdater, downloadUrl: String) {
        val progressDialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val textStatus = progressDialogView.findViewById<TextView>(R.id.update_status_text)
        val progressBar = progressDialogView.findViewById<ProgressBar>(R.id.update_progress_bar)
        progressDialogView.findViewById<TextView>(R.id.update_details_text).visibility = View.GONE

        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 0

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.updater_dialog_title)
            .setView(progressDialogView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val apkFile = updater.downloadApk(activity, downloadUrl) { percent ->
                    activity.runOnUiThread {
                        progressBar.progress = percent
                        textStatus.text = activity.getString(R.string.updater_downloading, percent)
                    }
                }
                progressDialog.dismiss()
                updater.installApk(activity, apkFile)
            } catch (e: Exception) {
                progressDialog.dismiss()
                val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                Toast.makeText(
                    activity,
                    activity.getString(R.string.updater_download_failed, errorMsg),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
