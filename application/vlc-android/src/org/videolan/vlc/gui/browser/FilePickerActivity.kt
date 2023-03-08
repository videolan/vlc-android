/*
 * *************************************************************************
 *  FilePickerActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.parcelable
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.video.VideoPlayerActivity
import kotlin.reflect.jvm.jvmName

class FilePickerActivity : BaseActivity() {
    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? = findViewById(android.R.id.content)

    /**
     * Forces the dark theme if the dialog is opened from the VideoPlayerActivity
     */
    override fun forcedTheme() =
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO && callingActivity?.className == VideoPlayerActivity::class.jvmName)
            R.style.Theme_VLC_PickerDialog_Dark
        else null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_picker_activity)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.fragment_placeholder, FilePickerFragment().apply { arguments = bundleOf(KEY_MEDIA to intent.parcelable<MediaWrapper>(KEY_MEDIA), KEY_PICKER_TYPE to intent.getIntExtra(KEY_PICKER_TYPE, 0)) }, "picker")
        ft.commit()
        window.attributes.gravity = Gravity.BOTTOM
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fpf = supportFragmentManager.findFragmentById(R.id.fragment_placeholder) as FilePickerFragment
                when {
                    fpf.isRootDirectory -> finish()
                    else -> fpf.browseUp()
                }
            }
        })
    }

    fun onCloseClick(@Suppress("UNUSED_PARAMETER") v:View) {
        finish()
    }
}
