/*
 * ************************************************************************
 *  DefaultTvActivity.kt
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.parcelableList
import org.videolan.resources.util.startMedialibrary
import org.videolan.television.util.EventThrottler
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_MEDIALIST
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_DEFAULT_VALUE
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_TYPE
import org.videolan.vlc.gui.dialogs.CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CONFIRM_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isSchemeStreaming

private const val TAG = "VLC/DefaultTvActivity"

open class DefaultTvActivity : AppCompatActivity(), IDialogManager {
    private val horizontalThrottler = EventThrottler(75L)
    private val verticalThrottler = EventThrottler(100L)
    private val dialogsDelegate = DialogDelegate()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val isThrottled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                horizontalThrottler.throttleEvent()
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                verticalThrottler.throttleEvent()
            }

            else -> false
        }

        return isThrottled || super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Medialibrary.getInstance().isStarted) startMedialibrary(firstRun = false, upgrade = false, parse = true)
        dialogsDelegate.observeDialogs(this, this)

        supportFragmentManager.setFragmentResultListener(CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT, this) { requestKey, bundle ->
            val media = bundle.parcelable<MediaLibraryItem>(RENAME_DIALOG_MEDIA) as? Playlist ?: return@setFragmentResultListener
            val name = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@setFragmentResultListener
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    media.setName(name)
                    val mainActivityViewModel: MainActivityViewModel by viewModels()
                    mainActivityViewModel.invalidateList(MediaListEntry.AUDIO_PLAYLISTS)
                    mainActivityViewModel.invalidateList(MediaListEntry.ALL_PLAYLISTS)
                    mainActivityViewModel.invalidateList(MediaListEntry.VIDEO_PLAYLISTS)
                }
            }
        }
        supportFragmentManager.setFragmentResultListener(CONFIRM_RENAME_DIALOG_RESULT, this) { key, bundle ->
            lifecycleScope.launch {
                val item: MediaWrapper = bundle.parcelable(RENAME_DIALOG_MEDIA) ?: return@launch
                val name: String = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@launch
                if (isSchemeStreaming(item.uri.scheme)) {
                    lifecycleScope.launch {
                        if (org.videolan.vlc.BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Stream found: renamed to $name")
                        withContext(Dispatchers.IO) { item.rename(name) }
                        val mainActivityViewModel: MainActivityViewModel by viewModels()
                        mainActivityViewModel.invalidateList(MediaListEntry.STREAMS)
                    }
                }
            }
        }
        supportFragmentManager.setFragmentResultListener(CONFIRM_DELETE_DIALOG_RESULT, this) { requestKey, bundle ->
            val items: List<MediaLibraryItem> = bundle.parcelableList(CONFIRM_DELETE_DIALOG_MEDIALIST) ?: listOf()
            val type = bundle.getInt(CONFIRM_DELETE_DIALOG_RESULT_TYPE)
            if (type == CONFIRM_DELETE_DIALOG_RESULT_DEFAULT_VALUE) {
                for (item in items) {
                    items.forEach { mw ->
                        if (mw is MediaWrapper) {
                            val deleteAction = Runnable {
                                lifecycleScope.launch {
                                    MediaUtils.deleteItem(this@DefaultTvActivity, mw) { UiTools.snacker(this@DefaultTvActivity, getString(R.string.msg_delete_failed, mw.title)) }
                                }
                            }
                            if (Permissions.checkWritePermission(this, (item as MediaWrapper), deleteAction)) deleteAction.run()
                        } else MediaUtils.deleteItem(this, item) {
//                            onDeleteFailed(it)
                        }
                    }
                }
            } else if (type == CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER) {
                items.forEach {
                    val path = if (it is Folder) it.mMrl.toUri().path else if (it is MediaWrapper) it.uri.path else null
                    path?.let { path ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val roots: Array<String> = Medialibrary.getInstance().foldersList
                            val strippedPath = path.removePrefix("file://")
                            for (root in roots) {
                                if (root.removePrefix("file://") == strippedPath) {
                                    Log.w(TAG, "banFolder: trying to ban root: $root")
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        UiTools.snacker(this@DefaultTvActivity, getString(R.string.cant_ban_root))
                                    }
                                    return@launch
                                }
                            }
                            MedialibraryUtils.banDir(strippedPath)
                        }

                    } ?: Log.e(TAG, "banFolder: path is null")
                }
            }
        }
    }

    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, this, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) { }
}