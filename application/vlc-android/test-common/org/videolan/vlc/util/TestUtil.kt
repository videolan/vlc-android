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
import androidx.core.net.toUri
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.resources.TYPE_LOCAL_FAV
import org.videolan.resources.TYPE_NETWORK_FAV
import org.videolan.resources.opensubtitles.OpenSubtitle
import org.videolan.resources.opensubtitles.QueryParameters
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem

object TestUtil {
    private const val fakeUri: String = "https://www.videolan.org/fake_"
    private const val fakeSubUri: String = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/subs/"
    private const val fakeMediaUri: String = "/storage/emulated/0/Android/data/org.videolan.vlc.debug/files/media/"

    fun createLocalFav(uri: Uri, title: String, iconUrl: String?): org.videolan.vlc.mediadb.models.BrowserFav {
        return org.videolan.vlc.mediadb.models.BrowserFav(uri, TYPE_LOCAL_FAV, title, iconUrl)
    }

    fun createLocalUris(count: Int): List<String> {
        return (0 until count).map {
            "${fakeMediaUri}local_$it.mp4"
        }
    }

    fun createLocalFavs(count: Int): List<org.videolan.vlc.mediadb.models.BrowserFav> {
        return (0 until count).map {
            createLocalFav("${fakeMediaUri}_$it.mp4".toUri(), "local$it", null)
        }
    }

    fun createNetworkFav(uri: Uri, title: String, iconUrl: String?): org.videolan.vlc.mediadb.models.BrowserFav {
        return org.videolan.vlc.mediadb.models.BrowserFav(uri, TYPE_NETWORK_FAV, title, iconUrl)
    }

    fun createNetworkUris(count: Int): List<String> {
        return (0 until count).map { "${fakeUri}_network$it.mp4" }
    }

    fun createNetworkFavs(count: Int): List<org.videolan.vlc.mediadb.models.BrowserFav> {
        return (0 until count).map {
            createNetworkFav(
                    "${fakeUri}network${it}".toUri(),
                    "network" + 1,
                    null)
        }
    }


    fun createExternalSub(
            idSubtitle: String,
            subtitlePath: String,
            mediaPath: String,
            subLanguageID: String,
            movieReleaseName: String): org.videolan.vlc.mediadb.models.ExternalSub {
        return org.videolan.vlc.mediadb.models.ExternalSub(idSubtitle, subtitlePath, mediaPath, subLanguageID, movieReleaseName)
    }

    fun createExternalSubsForMedia(mediaPath: String, mediaName: String, count: Int): List<org.videolan.vlc.mediadb.models.ExternalSub> {
        return (0 until count).map {
            org.videolan.vlc.mediadb.models.ExternalSub(it.toString(), "${fakeSubUri}$mediaName$it", mediaPath, "en", mediaName)
        }
    }

    fun createSubtitleSlave(mediaPath: String, uri: String): org.videolan.vlc.mediadb.models.Slave {
        return org.videolan.vlc.mediadb.models.Slave(mediaPath, IMedia.Slave.Type.Subtitle, 2, uri)
    }

    fun createSubtitleSlavesForMedia(mediaName: String, count: Int): List<org.videolan.vlc.mediadb.models.Slave> {
        return (0 until count).map {
            createSubtitleSlave("$fakeMediaUri$mediaName", "$fakeSubUri$mediaName$it.srt")
        }
    }

    fun createCustomDirectory(path: String): org.videolan.vlc.mediadb.models.CustomDirectory {
        return org.videolan.vlc.mediadb.models.CustomDirectory(path)
    }

    fun createCustomDirectories(count: Int): List<org.videolan.vlc.mediadb.models.CustomDirectory> {
        val directory = "/sdcard/foo"
        return (0 until count).map {
            createCustomDirectory("$directory$it")
        }
    }

    fun createDownloadingSubtitleItem(
            idSubtitle: String,
            mediaUri: Uri,
            subLanguageID: String,
            movieReleaseName: String,
            zipDownloadLink: String): SubtitleItem = SubtitleItem(idSubtitle, mediaUri, subLanguageID, movieReleaseName, State.Downloading, zipDownloadLink)

    fun createDownloadingSubtitleItem(
            idSubtitle: String,
            mediaPath: String,
            subLanguageID: String,
            movieReleaseName: String,
            zipDownloadLink: String): SubtitleItem = createDownloadingSubtitleItem(idSubtitle, mediaPath.toUri(), subLanguageID, movieReleaseName, zipDownloadLink)

    fun createOpenSubtitle(
            idSubtitle: String,
            subLanguageID: String,
            movieReleaseName: String,
            zipDownloadLink: String) = OpenSubtitle(
                idSubtitle = idSubtitle, subLanguageID = subLanguageID, movieReleaseName = movieReleaseName, zipDownloadLink = zipDownloadLink,
                idMovie = "", idMovieImdb = "", idSubMovieFile = "", idSubtitleFile = "", infoFormat = "", infoOther = "", infoReleaseGroup = "",
                userID = "", iSO639 = "", movieFPS = "", languageName = "", subActualCD = "", subSumVotes = "", subAuthorComment = "", subComments = "",
                score = 0.0, seriesEpisode = "", seriesIMDBParent = "", seriesSeason = "", subAddDate = "", subAutoTranslation = "", subBad = "", subDownloadLink = "",
                subDownloadsCnt = "", subEncoding = "", subFeatured = "", subFileName = "", subForeignPartsOnly = "", subFormat = "", subFromTrusted = "", subHash = "",
                subHD = "", subHearingImpaired = "", subLastTS = "", subRating = "", subSize = "", subSumCD = "", subtitlesLink = "", subTranslator = "", subTSGroup = "",
                subTSGroupHash = "", movieByteSize = "", movieHash = "", movieTimeMS = "", queryParameters = QueryParameters("", "", ""), queryNumber = "",
                userNickName = "", userRank = "", matchedBy = "", movieImdbRating = "", movieKind = "", movieName = "", movieNameEng = "", movieYear = "")
}
