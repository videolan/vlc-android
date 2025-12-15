/*
 * ************************************************************************
 *  VideoGroupFolderActivity.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.television.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.KEY_FOLDER
import org.videolan.resources.KEY_GROUP
import org.videolan.resources.util.parcelable
import org.videolan.television.ui.compose.composable.screens.VideoGroupScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.tools.KEY_SHOW_UPDATE
import org.videolan.tools.Settings
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.dialogs.UPDATE_DATE
import org.videolan.vlc.gui.dialogs.UPDATE_URL
import org.videolan.vlc.gui.dialogs.UpdateDialog
import org.videolan.vlc.util.AutoUpdate


class VideoGroupFolderActivity : DefaultTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val folder = intent?.parcelable<Folder>(KEY_FOLDER)
        val parentGroup = intent?.parcelable<VideoGroup>(KEY_GROUP)
        if (folder == null && parentGroup == null) throw IllegalStateException("No folder or group provided")


        setContent {
            VlcTVTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (folder != null)
                        VideoGroupScreen(folder = folder)
                    else
                        VideoGroupScreen(group = parentGroup)
                }
            }
        }

        lifecycleScope.launch {
            AutoUpdate.clean(this@VideoGroupFolderActivity.application)
            if (!Settings.getInstance(this@VideoGroupFolderActivity).getBoolean(KEY_SHOW_UPDATE, true)) return@launch
            AutoUpdate.checkUpdate(this@VideoGroupFolderActivity.application) { url, date ->
                val updateDialog = UpdateDialog().apply {
                    arguments = bundleOf(UPDATE_URL to url, UPDATE_DATE to date.time)
                }
                updateDialog.show(supportFragmentManager, "fragment_update")
            }
        }
    }

}

fun Activity.openVideoGroupFolder(item: MediaLibraryItem) {
    val i = Intent(this, VideoGroupFolderActivity::class.java)
    if (item is Folder) i.putExtra(KEY_FOLDER, item)
    else if (item is VideoGroup) i.putExtra(KEY_GROUP, item)
    startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
}
