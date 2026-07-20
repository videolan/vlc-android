/*
 * ************************************************************************
 *  ContextMenuDelegate.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.app.Activity
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.BrowserItemCtxFlags
import org.videolan.tools.retrieveParent
import org.videolan.vlc.gui.dialogs.ContextSheet
import org.videolan.vlc.gui.dialogs.CtxMenuItem
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.*
import org.videolan.vlc.util.ContextOption.Companion.createCtxAudioFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxFolderFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxHistoryFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxPlaylistAlbumFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxTrackFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoGroupFlags
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.isSchemeHttpOrHttps
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegate class responsible for handling context menu logic in the Television module.
 * It manages the available actions for different types of media library items
 * and coordinates the execution of these actions through registered listeners.
 */
@Singleton
class ContextMenuDelegate @Inject constructor() {

    private val ctxClickListeners = mutableMapOf<MediaListEntry, (MediaLibraryItem, Int, CtxMenuItem) -> Unit>()

    /**
     * Registers a click listener for a specific [MediaListEntry].
     *
     * @param mediaListEntry The media list entry to register the listener for.
     * @param listener The callback to be invoked when a context menu item is clicked.
     */
    fun addCtxClickListener(mediaListEntry: MediaListEntry, listener: (MediaLibraryItem, Int, CtxMenuItem) -> Unit) {
        ctxClickListeners[mediaListEntry] = listener
    }

    /**
     * Handles a context menu click event for a specific entry and item.
     *
     * @param entry The [MediaListEntry] from which the click originated.
     * @param item The [MediaLibraryItem] associated with the action.
     * @param position The position of the item in the list.
     * @param it The [CtxMenuItem] that was clicked.
     */
    fun onCtxClick(entry: MediaListEntry, item: MediaLibraryItem, position: Int, it: CtxMenuItem) {
        ctxClickListeners[entry]?.invoke(item, position, it)
    }

    /**
     * Generates the list of available context menu actions (flags) for a given item.
     *
     * @param activity The current [Activity] context.
     * @param entry The [MediaListEntry] containing the item.
     * @param item The [MediaLibraryItem] to get flags for.
     * @param onShowSnackbar Callback to show a snackbar message (e.g., for missing media).
     * @return A list of [CtxMenuItem] representing the available actions, or null if no actions are available.
     */
    fun getFlags(activity: Activity, entry: MediaListEntry, item: MediaLibraryItem, onShowSnackbar: (String) -> Unit): List<CtxMenuItem>? {
        val flags: FlagSet<ContextOption> = if (entry == MediaListEntry.HISTORY) {
            createCtxHistoryFlags()
        } else when (item.itemType) {
            MediaLibraryItem.TYPE_MEDIA -> {
                when (item) {
                    is MediaWrapper -> if (item.type == MediaWrapper.TYPE_DIR) {
                        FlagSet(ContextOption::class.java).apply {
                            if (item.hasFlag(BrowserItemCtxFlags.isFolderEmpty)) add(CTX_PLAY)
                            val isFileBrowser = entry.providerClass != NetworkProvider::class.java && item.uri.scheme == "file"
                            if (!entry.isRoot && isFileBrowser) add(ContextOption.CTX_BAN_FOLDER)
                            if (isFileBrowser && !entry.isRoot && !MedialibraryUtils.isScanned(item.uri.toString())) {
                                add(CTX_ADD_SCANNED)
                            }
                            if (isFileBrowser) {
                                add(CTX_APPEND)
                                if (item.hasFlag(BrowserItemCtxFlags.hasMedias)) add(CTX_ADD_FOLDER_PLAYLIST)
                                if (item.hasFlag(BrowserItemCtxFlags.hasSubfolders)) add(CTX_ADD_FOLDER_AND_SUB_PLAYLIST)
                            }
                        }
                    } else if (item.type == MediaWrapper.TYPE_VIDEO) {
                        createCtxVideoFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            if (item.seen > 0) add(CTX_MARK_AS_UNPLAYED) else add(CTX_MARK_AS_PLAYED)
                            if (item.time != 0L) add(CTX_PLAY_FROM_START)
                            if (entry == MediaListEntry.VIDEO_GROUPS || entry.isGroup) {
                                if (entry.isGroup) add(CTX_REMOVE_GROUP) else addAll(CTX_ADD_GROUP, CTX_GROUP_SIMILAR)
                            }
                            //go to folder
                            if (item.uri.retrieveParent() != null) add(CTX_GO_TO_FOLDER)
                            // no sharing on TV
                            remove(ContextOption.CTX_SHARE)
                            if (entry == MediaListEntry.BROWSER) remove(CTX_GO_TO_FOLDER)
                        }
                    } else if (isSchemeHttpOrHttps(item.uri.scheme)) {
                        FlagSet(ContextOption::class.java).apply {
                            addAll(CTX_ADD_SHORTCUT, CTX_ADD_TO_PLAYLIST, CTX_APPEND, CTX_COPY, CTX_DELETE, CTX_RENAME)
                        }
                    } else {
                        createCtxTrackFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            if (item.artistId != item.albumArtistId) add(CTX_GO_TO_ALBUM_ARTIST)
                        }
                    }

                    is Folder -> {
                        createCtxFolderFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                        }
                    }

                    is VideoGroup -> {
                        if (item.presentCount == 0) {
                            onShowSnackbar(activity.resources.getString(R.string.missing_media_snack))
                            return null
                        } else {
                            createCtxVideoGroupFlags().apply {
                                if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            }
                        }
                    }

                    else -> createCtxTrackFlags().apply {
                        if ((item as? MediaWrapper)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                        if ((item as? MediaWrapper)?.artistId != (item as? MediaWrapper)?.albumArtistId) add(CTX_GO_TO_ALBUM_ARTIST)
                    }
                }
            }

            MediaLibraryItem.TYPE_ARTIST -> {
                createCtxAudioFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Artist)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_ALBUM -> {
                createCtxPlaylistAlbumFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Album)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_GENRE -> {
                createCtxAudioFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Genre)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_PLAYLIST -> {
                createCtxPlaylistAlbumFlags().apply {
                    add(CTX_PLAY_AS_AUDIO)
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            else -> FlagSet(ContextOption::class.java)
        }
        return if (flags.isNotEmpty()) ContextSheet.populateMenuItems(activity, flags) else null
    }
}
