/*
 * ************************************************************************
 *  MediaSessionBrowser.kt
 * *************************************************************************
 *  Copyright © 2016-2020 VLC authors and VideoLAN
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
 *
 *  *************************************************************************
 */
package org.videolan.vlc.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.WorkerThread
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider.appContext
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionManagerService.ExtensionManagerActivity
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.helpers.AudioUtil.readCoverBitmap
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.UiTools.getDefaultAudioDrawable
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.media.MediaUtils.getMediaSubtitle
import java.util.*
import java.util.concurrent.Semaphore

class MediaSessionBrowser : ExtensionManagerActivity {
    override fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, isRefresh: Boolean) {
        if (showParams && items.size == 1 && items[0].getType() == VLCExtensionItem.TYPE_DIRECTORY) {
            extensionManagerService?.browse(items[0].stringId)
            return
        }
        var mediaItem: MediaDescriptionCompat.Builder
        var extensionItem: VLCExtensionItem
        for ((i, extensionItem) in items.withIndex()) {
            if (extensionItem.getType() != VLCExtensionItem.TYPE_AUDIO && extensionItem.getType() != VLCExtensionItem.TYPE_DIRECTORY) continue
            mediaItem = MediaDescriptionCompat.Builder()
            val coverUri = extensionItem.getImageUri()
            if (coverUri == null) mediaItem.setIconBitmap(getDefaultAudioDrawable(appContext).bitmap)
            else mediaItem.setIconUri(coverUri)
            mediaItem.setTitle(extensionItem.getTitle())
            mediaItem.setSubtitle(extensionItem.getSubTitle())
            val playable = extensionItem.getType() == VLCExtensionItem.TYPE_AUDIO
            if (playable) {
                mediaItem.setMediaId("${ExtensionsManager.EXTENSION_PREFIX}_${extensionId}_${extensionItem.getLink()}")
                extensionItems.add(MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            } else {
                mediaItem.setMediaId("${ExtensionsManager.EXTENSION_PREFIX}_${extensionId}_${extensionItem.stringId}")
                extensionItems.add(MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
            }
            if (i == MAX_EXTENSION_SIZE - 1) break
        }
        extensionLock.release()
    }

    companion object {
        private const val TAG = "VLC/MediaSessionBrowser"
        private val instance = MediaSessionBrowser()
        private var BASE_DRAWABLE_URI: String? = null
        const val ID_ROOT = "ID_ROOT"
        private const val ID_ARTISTS = "ID_ARTISTS"
        private const val ID_ALBUMS = "ID_ALBUMS"
        private const val ID_SONGS = "ID_SONGS"
        private const val ID_GENRES = "ID_GENRES"
        private const val ID_PLAYLISTS = "ID_PLAYLISTS"
        private const val ID_HISTORY = "ID_HISTORY"
        private const val ID_LAST_ADDED = "ID_RECENT"
        const val ID_SHUFFLE_ALL = "ID_SHUFFLE_ALL"
        const val ALBUM_PREFIX = "album"
        private const val ARTIST_PREFIX = "artist"
        private const val GENRE_PREFIX = "genre"
        const val PLAYLIST_PREFIX = "playlist"
        private const val DUMMY = "dummy"
        private const val MAX_HISTORY_SIZE = 50
        private const val MAX_EXTENSION_SIZE = 100

        // Extensions management
        private var extensionServiceConnection: ServiceConnection? = null
        private var extensionManagerService: ExtensionManagerService? = null
        private val extensionItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        private val extensionLock = Semaphore(0)

        @WorkerThread
        fun browse(context: Context, parentId: String): List<MediaBrowserCompat.MediaItem>? {
            var results: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
            var list: Array<out MediaLibraryItem>? = null
            var limitSize = false
            val res = context.resources

            //Extensions
            if (parentId.startsWith(ExtensionsManager.EXTENSION_PREFIX)) {
                if (extensionServiceConnection == null) {
                    createExtensionServiceConnection(context)
                    try {
                        extensionLock.acquire()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                if (extensionServiceConnection == null) return null
                val data = parentId.split("_").toTypedArray()
                val index = Integer.valueOf(data[1])
                extensionItems.clear()
                if (data.size == 2) {
                    //case extension root
                    extensionManagerService?.connectService(index)
                } else {
                    //case sub-directory
                    val stringId = parentId.replace("${ExtensionsManager.EXTENSION_PREFIX}_${index}_", "")
                    extensionManagerService?.browse(stringId)
                }
                try {
                    extensionLock.acquire()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                results = extensionItems
            } else {
                val ml by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
                when (parentId) {
                    ID_ROOT -> {
                        var item = MediaDescriptionCompat.Builder()
                        //List of Extensions
                        val extensions = ExtensionsManager.getInstance().getExtensions(context, true)
                        for ((i, extension) in extensions.withIndex()) {
                            if (extension.androidAutoEnabled()
                                    && Settings.getInstance(context).getBoolean(ExtensionsManager.EXTENSION_PREFIX + "_" + extension.componentName().packageName + "_" + ExtensionsManager.ANDROID_AUTO_SUFFIX, false)) {
                                item.setMediaId(ExtensionsManager.EXTENSION_PREFIX + "_" + i)
                                        .setTitle(extension.title())
                                val iconRes = extension.menuIcon()
                                var b: Bitmap? = null
                                var extensionRes: Resources?
                                if (iconRes != 0) {
                                    try {
                                        extensionRes = context.packageManager
                                                .getResourcesForApplication(extension.componentName().packageName)
                                        b = BitmapFactory.decodeResource(extensionRes, iconRes)
                                    } catch (ignored: PackageManager.NameNotFoundException) {
                                    }
                                }
                                if (b != null) item.setIconBitmap(b) else try {
                                    b = (context.packageManager.getApplicationIcon(extension.componentName().packageName) as BitmapDrawable).bitmap
                                    item.setIconBitmap(b)
                                } catch (e: PackageManager.NameNotFoundException) {
                                    b = context.getBitmapFromDrawable(R.drawable.icon)
                                    item.setIconBitmap(b)
                                }
                                results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                            }
                        }
                        if (BASE_DRAWABLE_URI == null) BASE_DRAWABLE_URI = "android.resource://" + context.packageName + "/drawable/"
                        //Shuffle
                        item = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_SHUFFLE_ALL)
                                .setTitle(res.getString(R.string.shuffle_all_title))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_audio_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                        //Last added
                        item = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_LAST_ADDED)
                                .setTitle(res.getString(R.string.last_added_media))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_history_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //History
                        item = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_HISTORY)
                                .setTitle(res.getString(R.string.history))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_history_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Playlists
                        item.setMediaId(ID_PLAYLISTS)
                                .setTitle(res.getString(R.string.playlists))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_playlist_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Artists
                        item.setMediaId(ID_ARTISTS)
                                .setTitle(res.getString(R.string.artists))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_artist_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Albums
                        item.setMediaId(ID_ALBUMS)
                                .setTitle(res.getString(R.string.albums))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_album_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Songs
                        item.setMediaId(ID_SONGS)
                                .setTitle(res.getString(R.string.songs))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_audio_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Genres
                        item.setMediaId(ID_GENRES)
                                .setTitle(res.getString(R.string.genres))
                                .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_genre_normal"))
                        results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        return results
                    }
                    ID_LAST_ADDED -> {
                        limitSize = true
                        list = ml.recentAudio
                    }
                    ID_HISTORY -> {
                        limitSize = true
                        list = ml.lastMediaPlayed()
                    }
                    ID_ARTISTS -> list = ml.getArtists(Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false))
                    ID_ALBUMS -> list = ml.albums
                    ID_GENRES -> list = ml.genres
                    ID_PLAYLISTS -> list = ml.playlists
                    ID_SONGS -> list = ml.audio
                    else -> {
                        val idSections = parentId.split("_").toTypedArray()
                        val id = idSections[1].toLong()
                        when (idSections[0]) {
                            ARTIST_PREFIX -> list = ml.getArtist(id).albums
                            GENRE_PREFIX -> list = ml.getGenre(id).albums
                        }
                    }
                }
                list?.let { list ->
                    val item = MediaDescriptionCompat.Builder()
                    for (libraryItem in list) {
                        if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type != MediaWrapper.TYPE_AUDIO) continue
                        var cover = readCoverBitmap(Uri.decode(libraryItem.artworkMrl), 256)
                        if (cover == null) cover = getDefaultAudioDrawable(context).bitmap
                        item.setTitle(libraryItem.title)
                                .setMediaId(generateMediaId(libraryItem))
                        item.setIconBitmap(cover)
                        if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA) {
                            item.setMediaUri((libraryItem as MediaWrapper).uri)
                                    .setSubtitle(getMediaSubtitle(libraryItem))
                        } else item.setSubtitle(libraryItem.description)
                        val playable = libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA || libraryItem.itemType == MediaLibraryItem.TYPE_ALBUM || libraryItem.itemType == MediaLibraryItem.TYPE_PLAYLIST
                        results.add(MediaBrowserCompat.MediaItem(item.build(), if (playable) MediaBrowserCompat.MediaItem.FLAG_PLAYABLE else MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        if (limitSize && results.size == MAX_HISTORY_SIZE) break
                    }
                }
            }
            if (results.isEmpty()) {
                val mediaItem = MediaDescriptionCompat.Builder()
                mediaItem.setMediaId(DUMMY)
                mediaItem.setTitle(context.getString(R.string.search_no_result))
                results.add(MediaBrowserCompat.MediaItem(mediaItem.build(), 0))
            }
            return results
        }

        fun generateMediaId(libraryItem: MediaLibraryItem): String {
            val prefix = when (libraryItem.itemType) {
                MediaLibraryItem.TYPE_ALBUM -> ALBUM_PREFIX
                MediaLibraryItem.TYPE_ARTIST -> ARTIST_PREFIX
                MediaLibraryItem.TYPE_GENRE -> GENRE_PREFIX
                MediaLibraryItem.TYPE_PLAYLIST -> PLAYLIST_PREFIX
                else -> return libraryItem.id.toString()
            }
            return "${prefix}_${libraryItem.id}"
        }

        private fun createExtensionServiceConnection(context: Context) {
            extensionServiceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    extensionManagerService = (service as ExtensionManagerService.LocalBinder).service.apply {
                        setExtensionManagerActivity(instance)
                    }
                    extensionLock.release()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    context.unbindService(extensionServiceConnection!!)
                    extensionServiceConnection = null
                    extensionManagerService!!.stopSelf()
                }
            }
            extensionServiceConnection?.let {
                val intent = Intent(context, ExtensionManagerService::class.java)
                if (!context.bindService(intent, it, Context.BIND_AUTO_CREATE)) extensionServiceConnection = null
            }
        }

        fun unbindExtensionConnection() {
            extensionManagerService?.disconnect()
        }
    }
}