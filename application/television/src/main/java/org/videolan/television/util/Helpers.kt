package org.videolan.television.util

import android.app.Activity
import android.content.Intent
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.videolan.television.ui.browser.REQUEST_CODE_NO_CONNECTION
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.resources.util.NoConnectivityException
import org.videolan.tools.Settings
import org.videolan.vlc.R


fun Activity.manageHttpException(e: Exception) {
    when (e) {
        is NoConnectivityException -> {
            if (Settings.showTvUi) {
                val intent = Intent(this, ConfirmationTvActivity::class.java)
                intent.putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TITLE, getString(R.string.no_internet_connection))
                intent.putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TEXT, getString(R.string.open_network_settings))
                startActivityForResult(intent, REQUEST_CODE_NO_CONNECTION)
            } else {
                Snackbar.make(findViewById<View>(android.R.id.content), R.string.no_internet_connection, Snackbar.LENGTH_LONG).show()
            }
        }
    }
}