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
#include <assert.h>
#include <medialibrary/Types.h>
#include <medialibrary/IMediaLibrary.h>
#include <medialibrary/IVideoTrack.h>
#include <medialibrary/IFile.h>
#include <medialibrary/IMedia.h>
#include <medialibrary/IArtist.h>
#include <medialibrary/IGenre.h>
#include <medialibrary/IAlbum.h>
#include <medialibrary/IPlaylist.h>
#include <medialibrary/IFolder.h>
#include <medialibrary/IMediaLibrary.h>
#include <medialibrary/IMetadata.h>
#include<medialibrary/filesystem/IDevice.h>
#include <medialibrary/IMediaGroup.h>
#include <medialibrary/IBookmark.h>
#include <medialibrary/IService.h>
#include <medialibrary/ISubscription.h>
#include <medialibrary/filesystem/Errors.h>

#include <type_traits>

#define VLC_JNI_VERSION JNI_VERSION_1_2

namespace utils
{
namespace jni
{
template <typename T>
class localref
{
    static_assert( std::is_pointer<T>::value, "T must be a pointer type");
public:
    localref(JNIEnv* env, T ref)
        : m_env( env )
        , m_ref( ref )
    {
    }

    localref()
        : m_env( nullptr )
        , m_ref( nullptr )
    {
    }

    ~localref()
    {
        if ( m_ref )
        {
            assert( m_env != nullptr );
            m_env->DeleteLocalRef( m_ref );
        }
    }
    /* Disable copy since there's no good reason to copy those and not move then */
    localref( const localref& ) = delete;
    localref& operator=( const localref& ) = delete;

    localref( localref&& old )
        : m_env( nullptr )
        , m_ref( nullptr )
    {
        using std::swap;
        swap( m_env, old.m_env );
        swap( m_ref, old.m_ref );
    }

    localref& operator=( localref&& rhs )
    {
        if ( m_ref != nullptr )
        {
            assert( m_env != nullptr );
            m_env->DeleteLocalRef( m_ref );
        }
        using std::swap;
        swap( m_env, rhs.m_env );
        swap( m_ref, rhs.m_ref );
        return *this;
    }

    T get() const
    {
        return m_ref;
    }

    /**
     * @brief release Will release the wrapper ownership but will *not* invoke DeleteLocalRef
     * @return The underlying raw pointer
     *
     * This is meant to be used when a JNI value needs to be provided back to java
     * which is expected to release the value itself
     */
    T release()
    {
        auto ref = m_ref;
        m_ref = nullptr;
        return ref;
    }

    bool operator==( std::nullptr_t ) const
    {
        return m_ref == nullptr;
    }

    bool operator!=( std::nullptr_t ) const
    {
        return m_ref != nullptr;
    }

private:
    JNIEnv* m_env;
    T m_ref;
};

using string = localref<jstring>;
using object = localref<jobject>;
using objectArray = localref<jobjectArray>;
using longArray = localref<jlongArray>;

}
}

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
        jmethodID onMediaConvertedToExternalId;
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
        jmethodID onFoldersAddedId;
        jmethodID onFoldersModifiedId;
        jmethodID onFoldersDeletedId;
        jmethodID onPlaylistsAddedId;
        jmethodID onPlaylistsModifiedId;
        jmethodID onPlaylistsDeletedId;
        jmethodID onMediaGroupAddedId;
        jmethodID onMediaGroupModifiedId;
        jmethodID onMediaGroupDeletedId;
        jmethodID onHistoryChangedId;
        jmethodID onDiscoveryStartedId;
        jmethodID onDiscoveryProgressId;
        jmethodID onDiscoveryCompletedId;
        jmethodID onDiscoveryFailedId;
        jmethodID onParsingStatsUpdatedId;
        jmethodID onBackgroundTasksIdleChangedId;
        jmethodID onReloadStartedId;
        jmethodID onReloadCompletedId;
        jmethodID onRootBannedId;
        jmethodID onRootUnbannedId;
        jmethodID onRootAddedId;
        jmethodID onRootRemovedId;
        jmethodID onMediaThumbnailReadyId;
        jmethodID onUnhandledExceptionId;
        jmethodID onSubscriptionsAddedId;
        jmethodID onSubscriptionsModifiedId;
        jmethodID onSubscriptionsDeleteId;
        jmethodID onSubscriptionNewMediaId;
        jmethodID onSubscriptionCacheUpdatedId;
        jmethodID onSubscriptionIdleChangedId;
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
    struct VideoGroup {
        jclass clazz;
        jmethodID initID;
    } VideoGroup;
    struct Bookmark {
        jclass clazz;
        jmethodID initID;
    } Bookmark;
    struct Subscription {
        jclass clazz;
        jmethodID initID;
    } Subscription;
    struct Service {
        jclass clazz;
        jmethodID initID;
    } Service;
};

utils::jni::object mediaToMediaWrapper(JNIEnv*, fields*, const medialibrary::MediaPtr &);
utils::jni::object convertAlbumObject(JNIEnv* env, fields *fields, medialibrary::AlbumPtr const& albumPtr);
utils::jni::object convertArtistObject(JNIEnv* env, fields *fields, medialibrary::ArtistPtr const& artistPtr);
utils::jni::object convertGenreObject(JNIEnv* env, fields *fields, medialibrary::GenrePtr const& genrePtr);
utils::jni::object convertPlaylistObject(JNIEnv* env, fields *fields, medialibrary::PlaylistPtr const& genrePtr, jboolean includeMissing, jboolean onlyFavorites);
utils::jni::object convertFolderObject(JNIEnv* env, fields *fields, medialibrary::FolderPtr const& folderPtr, int count);
utils::jni::object convertVideoGroupObject(JNIEnv* env, fields *fields, medialibrary::MediaGroupPtr const& videogroupPtr);
utils::jni::object convertBookmarkObject(JNIEnv* env, fields *fields, medialibrary::BookmarkPtr const& bookmarkPtr);
utils::jni::object convertSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::SearchAggregate const& searchAggregatePtr, jboolean includeMissing, jboolean onlyFavorites);
utils::jni::object convertSubscriptionObject(JNIEnv* env, fields *fields, medialibrary::SubscriptionPtr const& subsPtr);
utils::jni::object convertServiceObject(JNIEnv* env, fields *fields, medialibrary::ServicePtr const& servicePtr);
utils::jni::objectArray filteredArray(JNIEnv* env, utils::jni::objectArray array, jclass clazz, int removalCount = -1);
utils::jni::longArray idArray(JNIEnv* env, std::set<int64_t> ids);
utils::jni::string vlcNewStringUTF(JNIEnv* env, const char* psz_string);

#endif //VLC_MEDIALIB_UTILS_H
