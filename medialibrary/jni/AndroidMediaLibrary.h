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
    AndroidMediaLibrary(JavaVM *vm, fields *ref_fields, jobject thiz);
    ~AndroidMediaLibrary();

    medialibrary::InitializeResult initML(const std::string& dbPath, const std::string& thumbsPath);
    void start();
    bool isDeviceKnown(const std::string& uuid, const std::string& path, bool removable);
    bool deleteRemovableDevices();
    void addDevice(const std::string& uuid, const std::string& path, bool removable);
    void clearDatabase(bool restorePlaylists);
    std::vector<std::tuple<std::string, std::string, bool>> devices();
    bool removeDevice(const std::string& uuid, const std::string& path);
    void banFolder(const std::string& path);
    void unbanFolder(const std::string& path);
    void discover(const std::string&);
    void removeEntryPoint(const std::string& entryPoint);
    std::vector<medialibrary::FolderPtr> entryPoints();
    void setMediaUpdatedCbFlag(int flags);
    void setMediaAddedCbFlag(int flags);
    void pauseBackgroundOperations();
    void resumeBackgroundOperations();
    void reload();
    void reload( const std::string& entryPoint );
    void forceParserRetry();
    void forceRescan();
    bool increasePlayCount(int64_t mediaId);
    void removeMediaFromHistory(int64_t mediaId);
    /* History */
    std::vector<medialibrary::MediaPtr> lastMediaPlayed();
    bool addToHistory( const std::string& mrl, const std::string& title );
    std::vector<medialibrary::MediaPtr> lastStreamsPlayed();
    bool clearHistory();

    medialibrary::SearchAggregate search(const std::string& query);
    medialibrary::Query<medialibrary::IMedia> searchMedia(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IMedia> searchAudio(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IMedia> searchVideo(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IPlaylist> searchPlaylists(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IAlbum> searchAlbums(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IGenre> searchGenre(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    medialibrary::Query<medialibrary::IArtist> searchArtists(const std::string& query, const medialibrary::QueryParameters* params = nullptr);
    //Search from ML items
    medialibrary::Query<medialibrary::IMedia> searchFromAlbum( int64_t albumId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> searchAlbumsFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> searchAlbumsFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromPLaylist( int64_t playlistId, const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IFolder> searchFolders( const std::string& query, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> searchFromFolder( int64_t folderId, const std::string& query, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMediaGroup> searchVideoGroups( const std::string& query, const medialibrary::QueryParameters* params = nullptr );
        medialibrary::MediaPtr media(long id);
    medialibrary::MediaPtr media(const std::string& mrl);
    medialibrary::MediaPtr addMedia(const std::string& mrl);
    bool removeExternalMedia(long id);
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
    medialibrary::Query<medialibrary::IPlaylist> playlists(const medialibrary::QueryParameters* params);
    medialibrary::PlaylistPtr playlist( int64_t playlistId );
    medialibrary::PlaylistPtr PlaylistCreate( const std::string &name );
    medialibrary::Query<medialibrary::IMedia> tracksFromAlbum( int64_t albumId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromArtist( int64_t artistId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> albumsFromArtist( int64_t artistId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromGenre( int64_t genreId, bool withThumbnail, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> albumsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IArtist> artistsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromPlaylist( int64_t playlistId );
    // Folders
    medialibrary::Query<medialibrary::IMedia> mediaFromFolder(int64_t folderId, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IFolder> folders(const medialibrary::QueryParameters* params = nullptr, medialibrary::IMedia::Type type = medialibrary::IMedia::Type::Unknown );
    medialibrary::Query<medialibrary::IFolder> subFolders(int64_t folderId, const medialibrary::QueryParameters* params = nullptr );
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

    void onDiscoveryStarted( const std::string& entryPoint );
    void onDiscoveryProgress( const std::string& entryPoint );
    void onDiscoveryCompleted( const std::string& entryPoint, bool success );
    void onReloadStarted( const std::string& entryPoint );
    void onReloadCompleted( const std::string& entryPoint, bool success );
    void onEntryPointBanned( const std::string& entryPoint, bool success );
    void onEntryPointUnbanned( const std::string& entryPoint, bool success );
    void onEntryPointAdded( const std::string& entryPoint, bool success );
    void onEntryPointRemoved( const std::string& entryPoint, bool success );
    void onParsingStatsUpdated( uint32_t percent);
    void onBackgroundTasksIdleChanged( bool isIdle );
    void onMediaThumbnailReady(medialibrary::MediaPtr media, medialibrary::ThumbnailSizeType sizeType,
                               bool success );
    void onHistoryChanged( medialibrary::HistoryType historyType );

    bool onUnhandledException( const char* /* context */, const char* /* errMsg */, bool /* clearSuggested */ );
    void onRescanStarted();

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
    uint32_t m_nbDiscovery = 0, m_progress = 0, m_mediaAddedType = 0, m_mediaUpdatedType = 0;
};
#endif // ANDROIDMEDIALIBRARY_H
