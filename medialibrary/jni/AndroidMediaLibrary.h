#ifndef ANDROIDMEDIALIBRARY_H
#define ANDROIDMEDIALIBRARY_H

#include <vector>
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
    bool addDevice(const std::string& uuid, const std::string& path, bool removable);
    std::vector<std::tuple<std::string, std::string, bool>> devices();
    bool removeDevice(const std::string& uuid);
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
    /* History */
    std::vector<medialibrary::MediaPtr> lastMediaPlayed();
    bool addToHistory( const std::string& mrl, const std::string& title );
    std::vector<medialibrary::MediaPtr> lastStreamsPlayed();
    bool clearHistory();

    medialibrary::SearchAggregate search(const std::string& query);
    std::vector<medialibrary::MediaPtr> searchMedia(const std::string& query);
    std::vector<medialibrary::PlaylistPtr> searchPlaylists(const std::string& query);
    std::vector<medialibrary::AlbumPtr> searchAlbums(const std::string& query);
    std::vector<medialibrary::GenrePtr> searchGenre(const std::string& query);
    std::vector<medialibrary::ArtistPtr> searchArtists(const std::string& query);
    medialibrary::MediaPtr media(long id);
    medialibrary::MediaPtr media(const std::string& mrl);
    medialibrary::MediaPtr addMedia(const std::string& mrl);
    std::vector<medialibrary::MediaPtr> videoFiles( const medialibrary::QueryParameters* params = nullptr );
    std::vector<medialibrary::MediaPtr> audioFiles( const medialibrary::QueryParameters* params = nullptr );
    std::vector<medialibrary::AlbumPtr> albums(const medialibrary::QueryParameters* params);
    medialibrary::AlbumPtr album(int64_t albumId);
    std::vector<medialibrary::ArtistPtr> artists(bool includeAll, const medialibrary::QueryParameters* params);
    medialibrary::ArtistPtr artist(int64_t artistId);
    std::vector<medialibrary::GenrePtr> genres(const medialibrary::QueryParameters* params);
    medialibrary::GenrePtr genre(int64_t genreId);
    std::vector<medialibrary::PlaylistPtr> playlists(const medialibrary::QueryParameters* params);
    medialibrary::PlaylistPtr playlist( int64_t playlistId );
    medialibrary::PlaylistPtr PlaylistCreate( const std::string &name );
    std::vector<medialibrary::MediaPtr> tracksFromAlbum( int64_t albumId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::MediaPtr> mediaFromArtist( int64_t artistId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::AlbumPtr> albumsFromArtist( int64_t artistId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::MediaPtr> mediaFromGenre( int64_t genreId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::AlbumPtr> albumsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::ArtistPtr> artistsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params );
    std::vector<medialibrary::MediaPtr> mediaFromPlaylist( int64_t playlistId );
    bool playlistAppend(int64_t playlistId, int64_t mediaId);
    bool playlistAdd(int64_t playlistId, int64_t mediaId, unsigned int position);
    bool playlistMove(int64_t playlistId, int64_t mediaId, unsigned int position);
    bool playlistRemove(int64_t playlistId, int64_t mediaId);
    bool PlaylistDelete( int64_t playlistId );

    void requestThumbnail( int64_t media_id );

    void onMediaAdded( std::vector<medialibrary::MediaPtr> media );
    void onMediaUpdated( std::vector<medialibrary::MediaPtr> media ) ;
    void onMediaDeleted( std::vector<int64_t> ids ) ;

    void onArtistsAdded( std::vector<medialibrary::ArtistPtr> artists ) ;
    void onArtistsModified( std::vector<medialibrary::ArtistPtr> artist );
    void onArtistsDeleted( std::vector<int64_t> ids );

    void onAlbumsAdded( std::vector<medialibrary::AlbumPtr> albums );
    void onAlbumsModified( std::vector<medialibrary::AlbumPtr> albums );
    void onAlbumsDeleted( std::vector<int64_t> ids );

    void onTracksAdded( std::vector<medialibrary::AlbumTrackPtr> tracks );
    void onTracksDeleted( std::vector<int64_t> trackIds );

    void onPlaylistsAdded( std::vector<medialibrary::PlaylistPtr> playlists );
    void onPlaylistsModified( std::vector<medialibrary::PlaylistPtr> playlist );
    void onPlaylistsDeleted( std::vector<int64_t> ids );

    void onDiscoveryStarted( const std::string& entryPoint );
    void onDiscoveryProgress( const std::string& entryPoint );
    void onDiscoveryCompleted( const std::string& entryPoint );
    void onReloadStarted( const std::string& entryPoint );
    void onReloadCompleted( const std::string& entryPoint );
    void onEntryPointBanned( const std::string& entryPoint, bool success );
    void onEntryPointUnbanned( const std::string& entryPoint, bool success );
    void onEntryPointRemoved( const std::string& entryPoint, bool success );
    void onParsingStatsUpdated( uint32_t percent);
    void onBackgroundTasksIdleChanged( bool isIdle );
    void onMediaThumbnailReady( medialibrary::MediaPtr media, bool success );

private:
    void jni_detach_thread(void *data);
    JNIEnv *getEnv();
    void detachCurrentThread();

    pthread_once_t key_once = PTHREAD_ONCE_INIT;
    jweak weak_thiz;
    fields *p_fields;
    medialibrary::IMediaLibrary* p_ml;
    std::shared_ptr<AndroidDeviceLister> p_lister;
    medialibrary::IDeviceListerCb* p_DeviceListerCb = nullptr;
    bool m_paused = false;
    uint32_t m_nbDiscovery = 0, m_progress = 0, m_mediaAddedType = 0, m_mediaUpdatedType = 0;
};
#endif // ANDROIDMEDIALIBRARY_H
