/*****************************************************************************
 * utils.h
 *****************************************************************************
 * Copyright © 2016 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#ifndef VLC_MEDIALIB_UTILS_H
#define VLC_MEDIALIB_UTILS_H
#include <jni.h>
#include <medialibrary/Types.h>
#include <medialibrary/IMediaLibrary.h>

#define VLC_JNI_VERSION JNI_VERSION_1_2

struct fields {
    jint SDK_INT;
    struct IllegalStateException {
        jclass clazz;
    } IllegalStateException;
    struct IllegalArgumentException {
        jclass clazz;
    } IllegalArgumentException;
    struct MediaLibrary {
        jclass clazz;
        jfieldID instanceID;
        jmethodID onMediaAddedId;
        jmethodID onMediaUpdatedId;
        jmethodID onMediaDeletedId;
        jmethodID onArtistsAddedId;
        jmethodID onArtistsModifiedId;
        jmethodID onArtistsDeletedId;
        jmethodID onAlbumsAddedId;
        jmethodID onAlbumsModifiedId;
        jmethodID onAlbumsDeletedId;
        jmethodID onTracksAddedId;
        jmethodID onTracksDeletedId;
        jmethodID onGenresAddedId;
        jmethodID onGenresModifiedId;
        jmethodID onGenresDeletedId;
        jmethodID onPlaylistsAddedId;
        jmethodID onPlaylistsModifiedId;
        jmethodID onPlaylistsDeletedId;
        jmethodID onDiscoveryStartedId;
        jmethodID onDiscoveryProgressId;
        jmethodID onDiscoveryCompletedId;
        jmethodID onParsingStatsUpdatedId;
        jmethodID onBackgroundTasksIdleChangedId;
        jmethodID onReloadStartedId;
        jmethodID onReloadCompletedId;
        jmethodID onEntryPointBannedId;
        jmethodID onEntryPointUnbannedId;
        jmethodID onEntryPointRemovedId;
        jmethodID onMediaThumbnailReadyId;
    } MediaLibrary;
    struct Album {
        jclass clazz;
        jmethodID initID;
    } Album;
    struct Artist {
        jclass clazz;
        jmethodID initID;
    } Artist;
    struct Genre {
        jclass clazz;
        jmethodID initID;
    } Genre;
    struct Playlist {
        jclass clazz;
        jmethodID initID;
    } Playlist;
    struct MediaWrapper {
        jclass clazz;
        jmethodID initID;
    } MediaWrapper;
    struct HistoryItem {
        jclass clazz;
        jmethodID initID;
    } HistoryItem;
    struct SearchAggregate {
        jclass clazz;
        jmethodID initID;
    } SearchAggregate;
    struct Folder {
        jclass clazz;
        jmethodID initID;
    } Folder;
};

jobject mediaToMediaWrapper(JNIEnv*, fields*, const medialibrary::MediaPtr &);
jobject convertAlbumObject(JNIEnv* env, fields *fields, medialibrary::AlbumPtr const& albumPtr);
jobject convertArtistObject(JNIEnv* env, fields *fields, medialibrary::ArtistPtr const& artistPtr);
jobject convertGenreObject(JNIEnv* env, fields *fields, medialibrary::GenrePtr const& genrePtr);
jobject convertPlaylistObject(JNIEnv* env, fields *fields, medialibrary::PlaylistPtr const& genrePtr);
jobject convertFolderObject(JNIEnv* env, fields *fields, medialibrary::FolderPtr const& folderPtr);
jobject convertSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::SearchAggregate const& searchAggregatePtr);
jobjectArray filteredArray(JNIEnv* env, jobjectArray array, jclass clazz, int removalCount = -1);

#endif //VLC_MEDIALIB_UTILS_H
