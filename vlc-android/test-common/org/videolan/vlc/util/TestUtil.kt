/*******************************************************************************
 *  TestUtil.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 ******************************************************************************/

package org.videolan.vlc.util

import android.net.Uri
import org.videolan.libvlc.Media
import org.videolan.vlc.database.models.BrowserFav
import org.videolan.vlc.database.models.CustomDirectory
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.database.models.Slave
import java.io.File

object TestUtil {
    private const val fakeUri: String = "https://www.videolan.org/fake_"
    private const val fakeSubUri: String = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/"
    private const val fakeMediaUri: String = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/medias/"

    fun createLocalFav(uri: Uri, title: String, iconUrl: String?): BrowserFav {
        return BrowserFav(uri, TYPE_LOCAL_FAV, title, iconUrl)
    }

    fun createLocalUris(count: Int): List<String> {
        return (0 until count).map {
            "${fakeUri}_local$it"
        }
    }

    fun createLocalFavs(count: Int): List<BrowserFav> {
        return (0 until count).map {
            createLocalFav(Uri.parse(fakeUri + "local" + it),
                    "local" + 1,
                    null)
        }
    }

    fun createNetworkFav(uri: Uri, title: String, iconUrl: String?): BrowserFav {
        return BrowserFav(uri, TYPE_NETWORK_FAV, title, iconUrl)
    }

    fun createNetworkUris(count: Int): List<String> {
        return (0 until count).map {
            "${fakeUri}_network$it"
        }
    }

    fun createNetworkFavs(count: Int): List<BrowserFav> {
        return (0 until count).map {
            createNetworkFav(
                    Uri.parse(fakeUri + "network" + it),
                    "network" + 1,
                    null)
        }
    }


    fun createExternalSub(
            idSubtitle: String,
            subtitlePath: String,
            mediaPath: String,
            subLanguageID: String,
            movieReleaseName: String ): ExternalSub {
        return ExternalSub(idSubtitle, subtitlePath, mediaPath, subLanguageID, movieReleaseName)
    }

    fun createExternalSubsForMedia(mediaPath: String, mediaName: String, count: Int): List<ExternalSub> {
        return (0 until count).map {
            ExternalSub(it.toString(),"${fakeSubUri}$mediaName$it", mediaPath, "en", mediaName)
        }
    }

    fun createSubtitleSlave(mediaPath: String, uri: String): Slave {
        return Slave(mediaPath, Media.Slave.Type.Subtitle, 2, uri)
    }

    fun createSubtitleSlavesForMedia(mediaName: String, count:Int): List<Slave> {
        return (0 until count).map {
            createSubtitleSlave( "$fakeMediaUri$mediaName", "$fakeSubUri$mediaName$it.srt" )
        }
    }

    fun createCustomDirectory(path: String): CustomDirectory{
        return CustomDirectory(path)
    }

    fun createCustomDirectories(count: Int): List<CustomDirectory> {
        val directory = "/sdcard/foo"
        return (0 until count).map {
            createCustomDirectory("$directory$it")
        }
    }
}
