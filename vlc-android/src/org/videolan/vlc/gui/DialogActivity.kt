/*
 * ***************************************************************************
 * DialogActivity.java
 * ***************************************************************************
 * Copyright © 2016 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * ***************************************************************************
 */

package org.videolan.vlc.gui

import android.os.Bundle
import android.text.TextUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.media.MediaUtils

@ExperimentalCoroutinesApi
class DialogActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transparent)
        val key = intent.action
        if (TextUtils.isEmpty(key)) {
            finish()
            return
        }
        when {
            key!!.startsWith(KEY_LOGIN) -> setupLoginDialog(key)
            key.startsWith(KEY_QUESTION) -> setupQuestionDialog(key)
            key.startsWith(KEY_PROGRESS) -> setupProgressDialog(key)
            KEY_SERVER == key -> setupServerDialog()
            KEY_SUBS_DL == key -> setupSubsDialog()
            KEY_DEVICE == key -> setupDeviceDialog()
        }
    }

    private fun setupDeviceDialog() {
        window.decorView.alpha = 0f
        val dialog = DeviceDialog()
        val intent = intent
        dialog.setDevice(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_UUID), intent.getBooleanExtra(EXTRA_SCAN, false))
        dialog.show(supportFragmentManager, "device_dialog")
    }


    private fun setupServerDialog() {
        NetworkServerDialog().show(supportFragmentManager, "fragment_mrl")
    }

    @ObsoleteCoroutinesApi
    private fun setupSubsDialog() {
        val medialist = intent.getParcelableArrayListExtra<AbstractMediaWrapper>(EXTRA_MEDIALIST)
        if (medialist != null)
            MediaUtils.getSubs(this, medialist)
        else
            finish()
    }

    private fun setupLoginDialog(key: String) {
        val dialog = VlcLoginDialog()
        startVlcDialog(key, dialog)
    }

    private fun setupQuestionDialog(key: String) {
        val dialog = VlcQuestionDialog()
        startVlcDialog(key, dialog)
    }

    private fun setupProgressDialog(key: String) {
        val dialog = VlcProgressDialog()
        startVlcDialog(key, dialog)
    }

    private fun startVlcDialog(key: String, dialog: VlcDialog<*, *>) {
        dialog.init(key)
        val fm = supportFragmentManager
        dialog.show(fm, key)
    }

    companion object {

        const val KEY_LOGIN = "LoginDialog"
        const val KEY_QUESTION = "QuestionDialog"
        const val KEY_PROGRESS = "ProgressDialog"
        const val KEY_SERVER = "serverDialog"
        const val KEY_SUBS_DL = "subsdlDialog"
        const val KEY_DEVICE = "deviceDialog"

        const val EXTRA_MEDIALIST = "extra_media"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_UUID = "extra_uuid"
        const val EXTRA_SCAN = "extra_scan"
    }
}
