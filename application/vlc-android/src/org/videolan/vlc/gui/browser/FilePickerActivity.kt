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

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity
import kotlin.reflect.jvm.jvmName

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FilePickerActivity : AppCompatActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        if (callingActivity?.className != VideoPlayerActivity::class.jvmName)
            AppCompatDelegate.setDefaultNightMode(Integer.valueOf(Settings.getInstance(this).getString(KEY_APP_THEME, "-1")!!))
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_picker_activity)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.fragment_placeholder, FilePickerFragment().apply { arguments = bundleOf(KEY_MEDIA to intent.getParcelableExtra<MediaWrapper>(KEY_MEDIA), KEY_PICKER_TYPE to intent.getIntExtra(KEY_PICKER_TYPE, 0)) }, "picker")
        ft.commit()
        window.attributes.gravity = Gravity.BOTTOM
    }

    override fun onBackPressed() {
        val fpf = supportFragmentManager.findFragmentById(R.id.fragment_placeholder) as FilePickerFragment
        when {
            fpf.isRootDirectory -> finish()
            supportFragmentManager.backStackEntryCount > 0 -> super.onBackPressed()
            else -> fpf.browseUp()
        }
    }

    fun onCloseClick(v:View) {
        finish()
    }
}
