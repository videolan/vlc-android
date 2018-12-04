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
    medialibrary::MediaPtr media(long id);
    medialibrary::MediaPtr media(const std::string& mrl);
    medialibrary::MediaPtr addMedia(const std::string& mrl);
    medialibrary::MediaPtr addStream(const std::string& mrl, const std::string& title);
    medialibrary::Query<medialibrary::IMedia> videoFiles( const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> audioFiles( const medialibrary::QueryParameters* params = nullptr );
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
    medialibrary::Query<medialibrary::IMedia> mediaFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IAlbum> albumsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IArtist> artistsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IMedia> mediaFromPlaylist( int64_t playlistId );
    // Folders
    medialibrary::Query<medialibrary::IMedia> mediaFromFolder(int64_t folderId, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params = nullptr );
    medialibrary::Query<medialibrary::IFolder> folders(const medialibrary::QueryParameters* params = nullptr, medialibrary::IMedia::Type type = medialibrary::IMedia::Type::Unknown );
    medialibrary::Query<medialibrary::IFolder> subFolders(int64_t folderId, const medialibrary::QueryParameters* params = nullptr );
    //PLaylists
    bool playlistAppend(int64_t playlistId, int64_t mediaId);
    bool playlistAdd(int64_t playlistId, int64_t mediaId, unsigned int position);
    bool playlistMove(int64_t playlistId, int64_t mediaId, unsigned int position);
    bool playlistRemove(int64_t playlistId, int64_t mediaId);
    bool PlaylistDelete( int64_t playlistId );

    void requestThumbnail( int64_t media_id );

    void onMediaAdded( std::vector<medialibrary::MediaPtr> media );
    void onMediaModified( std::vector<medialibrary::MediaPtr> media ) ;
    void onMediaDeleted( std::vector<int64_t> ids ) ;

    void onArtistsAdded( std::vector<medialibrary::ArtistPtr> artists ) ;
    void onArtistsModified( std::vector<medialibrary::ArtistPtr> artist );
    void onArtistsDeleted( std::vector<int64_t> ids );

    void onAlbumsAdded( std::vector<medialibrary::AlbumPtr> albums );
    void onAlbumsModified( std::vector<medialibrary::AlbumPtr> albums );
    void onAlbumsDeleted( std::vector<int64_t> ids );

    void onPlaylistsAdded( std::vector<medialibrary::PlaylistPtr> playlists );
    void onPlaylistsModified( std::vector<medialibrary::PlaylistPtr> playlist );
    void onPlaylistsDeleted( std::vector<int64_t> ids );

    void onGenresAdded( std::vector<medialibrary::GenrePtr> );
    void onGenresModified( std::vector<medialibrary::GenrePtr> );
    void onGenresDeleted( std::vector<int64_t> );

    void onDiscoveryStarted( const std::string& entryPoint );
    void onDiscoveryProgress( const std::string& entryPoint );
    void onDiscoveryCompleted( const std::string& entryPoint, bool success );
    void onReloadStarted( const std::string& entryPoint );
    void onReloadCompleted( const std::string& entryPoint, bool success );
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
