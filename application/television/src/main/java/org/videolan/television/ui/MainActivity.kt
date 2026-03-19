/*
 * ************************************************************************
 *  MainActivity.kt
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

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableList
import org.videolan.television.ui.compose.composable.screens.MainScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.KEY_SHOW_UPDATE
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.FileBrowserFragment
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_MEDIALIST
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_DEFAULT_VALUE
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_TYPE
import org.videolan.vlc.gui.dialogs.CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.UPDATE_DATE
import org.videolan.vlc.gui.dialogs.UPDATE_URL
import org.videolan.vlc.gui.dialogs.UpdateDialog
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.AutoUpdate
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isSchemeStreaming


class MainActivity : DefaultTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        setContent {
            VlcTVTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }

        lifecycleScope.launch {
            AutoUpdate.clean(this@MainActivity.application)
            if (!Settings.getInstance(this@MainActivity).getBoolean(KEY_SHOW_UPDATE, true)) return@launch
            AutoUpdate.checkUpdate(this@MainActivity.application) { url, date ->
                val updateDialog = UpdateDialog().apply {
                    arguments = bundleOf(UPDATE_URL to url, UPDATE_DATE to date.time)
                }
                updateDialog.show(supportFragmentManager, "fragment_update")
            }
        }
    }

}
