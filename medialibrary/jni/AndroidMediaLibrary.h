#ifndef ANDROIDMEDIALIBRARY_H
#define ANDROIDMEDIALIBRARY_H

#include <vector>
#include <set>
#include <string>
#include <tuple>
#include <stdlib.h>
#include <jni.h>
#include <pthread.h>

#include "AndroidDeviceLister.h"
#include "utils.h"

#include <medialibrary/IAlbum.h>
#include <medialibrary/IArtist.h>
#include <medialibrary/IGenre.h>
#include <medialibrary/IPlaylist.h>
#include <medialibrary/IFolder.h>
#include <medialibrary/IMediaGroup.h>
#include <medialibrary/Types.h>
#include <medialibrary/IDeviceLister.h>
#include <medialibrary/IMedia.h>
#include <medialibrary/IMediaLibrary.h>
#include <medialibrary/IMetadata.h>

class AndroidMediaLibrary : public medialibrary::IMediaLibraryCb
{
public:
    AndroidMediaLibrary(JavaVM *vm, fields *ref_fields, jobject thiz, const char* dbPath, const char* thumbsPath);
    ~AndroidMediaLibrary();

    medialibrary::InitializeResult initML();
    bool isDeviceKnown(const std::string& uuid, const std::string& path, bool removable);
    bool deleteRemovableDevices();
    void addDevice(const std::string& uuid, const std::string& path, bool removable);
    bool clearDatabase(bool restorePlaylists);
    std::vector<std::tuple<std::string, std::string, bool>> devices();
    bool removeDevice(const std::string& uuid, const std::string& path);
    void banFolder(const std::string& path);
    void unbanFolder(const std::string& path);
    std::vector<medialibrary::FolderPtr> bannedRoots();
    void discover(const std::string&);
    bool setDiscoverNetworkEnabled(bool enabled);
    void removeRoot(const std::string& root);
    std::vector<medialibrary::FolderPtr> roots();
    void setMediaUpdatedCbFlag(int flags);
    void setMediaAddedCbFlag(int flags);
    void pauseBackgroundOperations();
    void resumeBackgroundOperations();
    void reload();
    void reload( const std::string& root );
    void forceParserRetry();
    void forceRescan();
    jint setLastPosition(int64_t mediaId, float progress);
    jint setLastTime(int64_t mediaId, int64_t progress);
    bool removeMediaFromHistory(int64_t mediaId);
    void setLibvlcInstance(libvlc_instance_t* inst);
    /* History */
    std::vector<medialibrary::MediaPtr> history(medialibrary::HistoryType type);
    bool addToHistory( const std::string& mrl, const std::string& title );
    bool clearHistory(medialibrary::HistoryType type);

    medialibrary::SearchAggregate search(const std::string& query);
    medialibrary::Query<medialibrary::IMedia> searchMedia(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IMedia> searchAudio(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IMedia> searchVideo(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IPlaylist> searchPlaylists(const std::string& query, medialibrary::PlaylistType type, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IAlbum> searchAlbums(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IGenre> searchGenre(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IArtist> searchArtists(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    //Search from ML items
    medialibrary::Query<medialibrary::IMedia> searchFromAlbum( int64_t albumId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> searchAlbumsFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> searchAlbumsFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromPlaylist( int64_t playlistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IFolder> searchFolders( const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromFolder( int64_t folderId, const std::string& query, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMediaGroup> searchVideoGroups( const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::BookmarkPtr bookmark(long id);
    medialibrary::MediaPtr media(long id);
    medialibrary::MediaPtr media(const std::string& mrl);
    medialibrary::MediaPtr addMedia(const std::string& mrl, long duration);
    bool removeExternalMedia(long id);
    bool flushUserProvidedThumbnails();
    medialibrary::MediaPtr addStream(const std::string& mrl, const std::string& title);
    medialibrary::Query<medialibrary::IMedia> videoFiles( const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> audioFiles( const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMediaGroup> videoGroups( const medialibrary::QueryParameters* params );
    medialibrary::Query<medialibrary::IAlbum> albums(const medialibrary::QueryParameters* params);
    medialibrary::AlbumPtr album(int64_t albumId);
    medialibrary::Query<medialibrary::IArtist> artists(bool includeAll, const medialibrary::QueryParameters* params);
    medialibrary::ArtistPtr artist(int64_t artistId);
    medialibrary::Query<medialibrary::IGenre> genres(const medialibrary::QueryParameters* params);
    medialibrary::GenrePtr genre(int64_t genreId);
    medialibrary::Query<medialibrary::IPlaylist> playlists(medialibrary::PlaylistType type, medialibrary::QueryParameters* params);
    medialibrary::PlaylistPtr playlist( int64_t playlistId );
    medialibrary::PlaylistPtr PlaylistCreate( const std::string &name );
    medialibrary::Query<medialibrary::IMedia> tracksFromAlbum( int64_t albumId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromArtist( int64_t artistId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> albumsFromArtist( int64_t artistId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromGenre( int64_t genreId, bool withThumbnail, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> albumsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IArtist> artistsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromPlaylist( int64_t playlistId, const medialibrary::QueryParameters* params = nullptr );
    // Folders
    medialibrary::Query<medialibrary::IMedia> mediaFromFolder(const medialibrary::IFolder* folder, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IFolder> folders(const medialibrary::QueryParameters* params = nullptr, medialibrary::IMedia::Type type = medialibrary::IMedia::Type::Unknown );
    medialibrary::Query<medialibrary::IFolder> subFolders(int64_t folderId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::FolderPtr folder(int64_t folderId);
    // VideoGroups
    medialibrary::Query<medialibrary::IMedia> mediaFromMediaGroup(const int64_t groupId, const medialibrary::QueryParameters* params );
    medialibrary::Query<medialibrary::IMedia> searchFromMediaGroup(const int64_t groupId, const std::string& query, const medialibrary::QueryParameters* params );
    void onMediaGroupsAdded(std::vector<medialibrary::MediaGroupPtr> mediaGroups );
    medialibrary::MediaGroupPtr videoGroup( const int64_t groupId );
    bool groupAddId( const int64_t groupId, const int64_t mediaId );
    bool groupRemoveId( const int64_t groupId, const int64_t mediaId );
    std::string groupName( const int64_t groupId );
    bool groupRename( const int64_t groupId, const std::string& name );
    bool groupUserInteracted(const int64_t groupId );
    int64_t groupDuration(const int64_t groupId );
    bool groupDestroy(const int64_t groupId );
    medialibrary::MediaGroupPtr createMediaGroup( std::string name );
    medialibrary::MediaGroupPtr createMediaGroup( std::vector<int64_t> mediaIds );
    bool regroupAll( );
    bool regroup(int64_t mediaId);
    void onMediaGroupsModified( std::set<int64_t> mediaGroupsIds );
    void onMediaGroupsDeleted( std::set<int64_t> mediaGroupsIds );
    void onBookmarksAdded( std::vector<medialibrary::BookmarkPtr> );
    void onBookmarksModified( std::set<int64_t> );
    void onBookmarksDeleted( std::set<int64_t> );
    //Playlists
    bool playlistAppend(int64_t playlistId, int64_t mediaId);
    bool playlistAdd(int64_t playlistId, int64_t mediaId, unsigned int position);
    bool playlistMove(int64_t playlistId, unsigned int oldPosition, unsigned int newPosition);
    bool playlistRemove(int64_t playlistId, unsigned int position);
    bool PlaylistDelete( int64_t playlistId );

    void requestThumbnail( int64_t media_id, medialibrary::ThumbnailSizeType sizeType, uint32_t desiredWidth,
                           uint32_t desiredHeight, float position );

    void onMediaAdded( std::vector<medialibrary::MediaPtr> media );
    void onMediaModified( std::set<int64_t> media ) ;
    void onMediaDeleted( std::set<int64_t> ids ) ;
    void onMediaConvertedToExternal( std::set<int64_t> ids ) ;

    void onArtistsAdded( std::vector<medialibrary::ArtistPtr> artists ) ;
    void onArtistsModified( std::set<int64_t> artist );
    void onArtistsDeleted( std::set<int64_t> ids );

    void onAlbumsAdded( std::vector<medialibrary::AlbumPtr> albums );
    void onAlbumsModified( std::set<int64_t> albums );
    void onAlbumsDeleted( std::set<int64_t> ids );

    void onPlaylistsAdded( std::vector<medialibrary::PlaylistPtr> playlists );
    void onPlaylistsModified( std::set<int64_t> playlist );
    void onPlaylistsDeleted( std::set<int64_t> ids );

    void onGenresAdded( std::vector<medialibrary::GenrePtr> );
    void onGenresModified( std::set<int64_t> );
    void onGenresDeleted( std::set<int64_t> );

    void onFoldersAdded( std::vector<medialibrary::FolderPtr> );
    void onFoldersModified( std::set<int64_t> );
    void onFoldersDeleted( std::set<int64_t> );

    void onDiscoveryStarted();
    void onDiscoveryProgress( const std::string& root );
    void onDiscoveryCompleted();
    void onDiscoveryFailed( const std::string& root );
    void onReloadStarted( const std::string& root );
    void onReloadCompleted( const std::string& root, bool success );
    void onRootBanned( const std::string& root, bool success );
    void onRootUnbanned( const std::string& root, bool success );
    void onRootAdded( const std::string& root, bool success );
    void onRootRemoved( const std::string& root, bool success );
    void onParsingStatsUpdated( uint32_t opsDone, uint32_t opsScheduled );
    void onBackgroundTasksIdleChanged( bool isIdle );
    void onMediaThumbnailReady(medialibrary::MediaPtr media, medialibrary::ThumbnailSizeType sizeType,
                               bool success );
    void onHistoryChanged( medialibrary::HistoryType historyType );

    bool onUnhandledException( const char* /* context */, const char* /* errMsg */, bool /* clearSuggested */ );
    void onRescanStarted();

    bool removeSubscription( int64_t );

    medialibrary::ServicePtr service( medialibrary::IService::Type);
    medialibrary::SubscriptionPtr subscription( int64_t id);
    bool fitsInSubscriptionCache( const medialibrary::IMedia& m);
    void cacheNewSubscriptionMedia();
    bool setSubscriptionMaxCachedMedia( int nbMedia );
    bool setSubscriptionMaxCacheSize( long size );
    bool setMaxCacheSize( long size );
    uint32_t getSubscriptionMaxCachedMedia();
    uint64_t getSubscriptionMaxCacheSize();
    uint64_t getMaxCacheSize();
    bool refreshAllSubscriptions();
    void onSubscriptionsAdded( std::vector<medialibrary::SubscriptionPtr> );
    void onSubscriptionsModified( std::set<int64_t> );
    void onSubscriptionsDeleted( std::set<int64_t> );
    void onSubscriptionNewMedia( std::set<int64_t> );
    void onSubscriptionCacheUpdated( int64_t subscriptionId );
    void onCacheIdleChanged( bool idle );

private:
    void jni_detach_thread(void *data);
    JNIEnv *getEnv();
    void detachCurrentThread();

    pthread_once_t key_once = PTHREAD_ONCE_INIT;
    jweak weak_thiz;
    fields *p_fields;
    medialibrary::IMediaLibrary* p_ml;
    std::shared_ptr<AndroidDeviceLister> p_lister;
    bool m_paused = false;
    uint32_t m_mediaAddedType = 0, m_mediaUpdatedType = 0;
};
#endif // ANDROIDMEDIALIBRARY_H
