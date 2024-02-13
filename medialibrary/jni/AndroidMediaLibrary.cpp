#include "AndroidMediaLibrary.h"
#define LOG_TAG "VLC/JNI/AndroidMediaLibrary"
#include "log.h"
#define THREAD_NAME "AndroidMedialibrary"

#define FLAG_MEDIA_UPDATED_AUDIO       1 << 0
#define FLAG_MEDIA_UPDATED_AUDIO_EMPTY 1 << 1
#define FLAG_MEDIA_UPDATED_VIDEO       1 << 2
#define FLAG_MEDIA_UPDATED_VIDEO_EMPTY 1 << 3
#define FLAG_MEDIA_ADDED_AUDIO         1 << 4
#define FLAG_MEDIA_ADDED_AUDIO_EMPTY   1 << 5
#define FLAG_MEDIA_ADDED_VIDEO         1 << 6
#define FLAG_MEDIA_ADDED_VIDEO_EMPTY   1 << 7

static pthread_key_t jni_env_key;
static JavaVM *myVm;

static void jni_detach_thread(void *data)
{
   myVm->DetachCurrentThread();
}

static void key_init(void)
{
    pthread_key_create(&jni_env_key, jni_detach_thread);
}

AndroidMediaLibrary::AndroidMediaLibrary(JavaVM *vm, fields *ref_fields, jobject thiz, const char* dbPath, const char* mlFolder)
    : p_fields ( ref_fields )
{
    myVm = vm;
    p_lister = std::make_shared<AndroidDeviceLister>();
    medialibrary::SetupConfig config;
    config.logger = std::make_shared<AndroidMediaLibraryLogger>();
    config.logLevel = medialibrary::LogLevel::Debug;
    config.deviceListers["file://"] = p_lister;
    p_ml = NewMediaLibrary( dbPath, mlFolder, false, &config);
    pthread_once(&key_once, key_init);
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    weak_thiz = env->NewWeakGlobalRef(thiz);
}

AndroidMediaLibrary::~AndroidMediaLibrary()
{
    LOGD("AndroidMediaLibrary delete");
    pthread_key_delete(jni_env_key);
    delete p_ml;
}

medialibrary::InitializeResult
AndroidMediaLibrary::initML()
{
    return p_ml->initialize(this);
}

bool
AndroidMediaLibrary::clearDatabase(bool restorePlaylists) {
    return p_ml->clearDatabase(restorePlaylists);
}

void
AndroidMediaLibrary::addDevice(const std::string& uuid, const std::string& path, bool removable)
{
    p_lister->addDevice(uuid, path, removable);
}

bool
AndroidMediaLibrary::isDeviceKnown(const std::string& uuid, const std::string& path, bool removable)
{
    return p_ml->isDeviceKnown(uuid, path, removable);
}

bool
AndroidMediaLibrary::deleteRemovableDevices()
{
    return p_ml->deleteRemovableDevices();
}

std::vector<std::tuple<std::string, std::string, bool>>
AndroidMediaLibrary::devices()
{
    return p_lister->devices();
}

bool
AndroidMediaLibrary::removeDevice(const std::string& uuid, const std::string& path)
{
    return p_lister->removeDevice(uuid, path);
}

void
AndroidMediaLibrary::banFolder(const std::string& path)
{
    p_ml->banFolder(path);
}

void
AndroidMediaLibrary::unbanFolder(const std::string& path)
{
    p_ml->unbanFolder(path);
}

std::vector<medialibrary::FolderPtr>
AndroidMediaLibrary::bannedRoots()
{
    return p_ml->bannedRoots()->all();
}

void
AndroidMediaLibrary::discover(const std::string& libraryPath)
{
    p_ml->discover(libraryPath);
}

bool
AndroidMediaLibrary::setDiscoverNetworkEnabled(bool enabled)
{
    return p_ml->setDiscoverNetworkEnabled(enabled);
}

void
AndroidMediaLibrary::removeRoot(const std::string& root)
{
    p_ml->removeRoot(root);
}

std::vector<medialibrary::FolderPtr>
AndroidMediaLibrary::roots()
{
    return p_ml->roots(nullptr)->all();
}

void
AndroidMediaLibrary::pauseBackgroundOperations()
{
    p_ml->pauseBackgroundOperations();
    m_paused = true;
}

void
AndroidMediaLibrary::setMediaUpdatedCbFlag(int flags)
{
    m_mediaUpdatedType = flags;
}

void
AndroidMediaLibrary::setMediaAddedCbFlag(int flags)
{
    m_mediaAddedType = flags;
}

void
AndroidMediaLibrary::resumeBackgroundOperations()
{
    p_ml->resumeBackgroundOperations();
    m_paused = false;
}

void
AndroidMediaLibrary::reload()
{
    p_ml->reload();
}

void
AndroidMediaLibrary::reload( const std::string& root )
{
    p_ml->reload(root);
}

void
AndroidMediaLibrary::forceParserRetry()
{
    p_ml->forceParserRetry();
}

void
AndroidMediaLibrary::forceRescan()
{
    p_ml->forceRescan();
}

jint
AndroidMediaLibrary::setLastPosition(int64_t mediaId, float lastPosition)
{
    auto media = p_ml->media(mediaId);
    if (media != nullptr)
        return (int) media->setLastPosition( lastPosition );
    return -1;
}

jint
AndroidMediaLibrary::setLastTime(int64_t mediaId, int64_t time)
{
    auto media = p_ml->media(mediaId);
    if (media != nullptr)
        return (int) media->setLastTime( time );
    return -1;
}

bool
AndroidMediaLibrary::removeMediaFromHistory(int64_t mediaId)
{
    auto media = p_ml->media(mediaId);
    if (media != nullptr) return media->removeFromHistory();
    return false;
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::history(medialibrary::HistoryType type)
{
    return p_ml->history(type)->items( 100, 0 );
}

bool
AndroidMediaLibrary::addToHistory( const std::string& mrl, const std::string& title)
{
    auto media = p_ml->media( mrl );
    if ( media == nullptr )
    {
        media = p_ml->addStream( mrl );
        if ( media == nullptr )
            return false;
    }
    media->setTitle(title);
    return true;
}

bool
AndroidMediaLibrary::clearHistory(medialibrary::HistoryType type)
{
    return p_ml->clearHistory(type);
}

medialibrary::SearchAggregate
AndroidMediaLibrary::search(const std::string& query)
{
    return p_ml->search(query);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchMedia(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchMedia(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchAudio(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchAudio(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchVideo(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchVideo(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromAlbum( int64_t albumId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto album = p_ml->album(albumId);
    return album == nullptr ? nullptr : album->searchTracks(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? nullptr : artist->searchTracks(query, params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::searchAlbumsFromArtist( int64_t artistId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? nullptr : artist->searchAlbums(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? nullptr : genre->searchTracks(query, params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::searchAlbumsFromGenre( int64_t genreId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? nullptr : genre->searchAlbums(query, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromPlaylist( int64_t playlistId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? nullptr : playlist->searchMedia(query, params);
}

medialibrary::Query<medialibrary::IFolder>
AndroidMediaLibrary::searchFolders(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchFolders(query, medialibrary::IMedia::Type::Video, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromFolder( int64_t folderId, const std::string& query, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params )
{
    auto folder = p_ml->folder(folderId);
    return folder == nullptr ? nullptr : folder->searchMedia(query, type, params);
}

medialibrary::Query<medialibrary::IPlaylist>
AndroidMediaLibrary::searchPlaylists(const std::string& query, medialibrary::PlaylistType type, const medialibrary::QueryParameters* params)
{
    return p_ml->searchPlaylists(query, type, params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::searchAlbums(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchAlbums(query, params);
}

medialibrary::Query<medialibrary::IGenre>
AndroidMediaLibrary::searchGenre(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchGenre(query, params);
}

medialibrary::Query<medialibrary::IArtist>
AndroidMediaLibrary::searchArtists(const std::string& query, const medialibrary::QueryParameters* params)
{
    return p_ml->searchArtists(query, medialibrary::ArtistIncluded::All, params);
}

medialibrary::BookmarkPtr
AndroidMediaLibrary::bookmark(long id)
{
    return p_ml->bookmark(id);
}

medialibrary::MediaPtr
AndroidMediaLibrary::media(long id)
{
    return p_ml->media(id);
}

medialibrary::MediaPtr
AndroidMediaLibrary::media(const std::string& mrl)
{
    return p_ml->media(mrl);
}

medialibrary::MediaPtr
AndroidMediaLibrary::addMedia(const std::string& mrl, long duration)
{
    return p_ml->addExternalMedia(mrl, duration);
}

bool
AndroidMediaLibrary::removeExternalMedia(long id)
{
    auto media = p_ml->media(id);
    return media != nullptr && p_ml->removeExternalMedia(media);
}

bool
AndroidMediaLibrary::flushUserProvidedThumbnails()
{
    return p_ml->flushUserProvidedThumbnails();
}

medialibrary::MediaPtr
AndroidMediaLibrary::addStream(const std::string& mrl, const std::string& title)
{
    auto media = p_ml->addStream(mrl);
    if (media != nullptr) media->setTitle(title);
    return media;
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::videoFiles( const medialibrary::QueryParameters* params )
{
    return p_ml->videoFiles(params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::audioFiles( const medialibrary::QueryParameters* params )
{
    return p_ml->audioFiles(params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::albums(const medialibrary::QueryParameters* params)
{
    return p_ml->albums(params);
}

medialibrary::AlbumPtr
AndroidMediaLibrary::album(int64_t albumId)
{
    return p_ml->album(albumId);
}

medialibrary::Query<medialibrary::IArtist>
AndroidMediaLibrary::artists(bool includeAll, const medialibrary::QueryParameters* params)
{
    return p_ml->artists(includeAll ? medialibrary::ArtistIncluded::All : medialibrary::ArtistIncluded::AlbumArtistOnly,
                         params);
}

medialibrary::ArtistPtr
AndroidMediaLibrary::artist(int64_t artistId)
{
    return p_ml->artist(artistId);
}

medialibrary::Query<medialibrary::IGenre>
AndroidMediaLibrary::genres(const medialibrary::QueryParameters* params)
{
    return p_ml->genres(params);
}

medialibrary::GenrePtr
AndroidMediaLibrary::genre(int64_t genreId)
{
    return p_ml->genre(genreId);
}

medialibrary::Query<medialibrary::IPlaylist>
AndroidMediaLibrary::playlists(medialibrary::PlaylistType type, medialibrary::QueryParameters* params)
{
    return p_ml->playlists(type, params);
}

medialibrary::PlaylistPtr
AndroidMediaLibrary::playlist( int64_t playlistId )
{
    return p_ml->playlist(playlistId);
}

medialibrary::PlaylistPtr
AndroidMediaLibrary::PlaylistCreate( const std::string &name )
{
    return p_ml->createPlaylist(name);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::tracksFromAlbum( int64_t albumId, const medialibrary::QueryParameters* params )
{
    auto album = p_ml->album(albumId);
    return album == nullptr ? nullptr : album->tracks(params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::mediaFromArtist( int64_t artistId, const medialibrary::QueryParameters* params )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? nullptr : artist->tracks(params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::albumsFromArtist( int64_t artistId, const medialibrary::QueryParameters* params )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? nullptr : artist->albums(params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::mediaFromGenre( int64_t genreId, bool withThumbnail, const medialibrary::QueryParameters* params )
{
    using medialibrary::IGenre;
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? nullptr : genre->tracks(withThumbnail ? medialibrary::IGenre::TracksIncluded::WithThumbnailOnly : medialibrary::IGenre::TracksIncluded::All,
                                                      params);
}

medialibrary::Query<medialibrary::IAlbum>
AndroidMediaLibrary::albumsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? nullptr : genre->albums(params);
}

medialibrary::Query<medialibrary::IArtist>
AndroidMediaLibrary::artistsFromGenre( int64_t genreId, const medialibrary::QueryParameters* params )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? nullptr : genre->artists(params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::mediaFromPlaylist( int64_t playlistId , const medialibrary::QueryParameters* params)
{
    auto playlist =  p_ml->playlist(playlistId);
    return playlist == nullptr ? nullptr : playlist->media(params);
}

bool
AndroidMediaLibrary::playlistAppend(int64_t playlistId, int64_t mediaId) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->append(mediaId);
}

bool
AndroidMediaLibrary::playlistAdd(int64_t playlistId, int64_t mediaId, unsigned int position) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->add(mediaId, position);
}

bool
AndroidMediaLibrary::playlistMove(int64_t playlistId, unsigned int oldPosition, unsigned int newPosition) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->move(oldPosition, newPosition);
}

bool
AndroidMediaLibrary::playlistRemove(int64_t playlistId, unsigned int position) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->remove(position);
}

bool
AndroidMediaLibrary::PlaylistDelete( int64_t playlistId )
{
    return p_ml->deletePlaylist(playlistId);
}

medialibrary::Query<medialibrary::IFolder>
AndroidMediaLibrary::folders(const medialibrary::QueryParameters* params, medialibrary::IMedia::Type type)
{
    return p_ml->folders(type, params);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::mediaFromFolder(const medialibrary::IFolder* folder, medialibrary::IMedia::Type type, const medialibrary::QueryParameters* params )
{
    return folder != nullptr ? folder->media(type, params) : nullptr;
}

medialibrary::Query<medialibrary::IFolder> AndroidMediaLibrary::subFolders(int64_t folderId, const medialibrary::QueryParameters* params )
{
    medialibrary::FolderPtr folder = p_ml->folder(folderId);
    return folder != nullptr ? folder->subfolders(params) : nullptr;
}

medialibrary::FolderPtr AndroidMediaLibrary::folder(int64_t folderId)
{
    return p_ml->folder(folderId);
}

medialibrary::Query<medialibrary::IMediaGroup>
AndroidMediaLibrary::videoGroups( const medialibrary::QueryParameters* params )
{
    return p_ml->mediaGroups(medialibrary::IMedia::Type::Video, params);
}

medialibrary::Query<medialibrary::IMediaGroup>
AndroidMediaLibrary::searchVideoGroups( const std::string& query, const medialibrary::QueryParameters* params )
{
    return p_ml->searchMediaGroups(query, params);
}

medialibrary::MediaGroupPtr
AndroidMediaLibrary::videoGroup( const int64_t groupId )
{
    return p_ml->mediaGroup(groupId);
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::mediaFromMediaGroup(const int64_t groupId, const medialibrary::QueryParameters* params )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr ? group->media(medialibrary::IMedia::Type::Video, params) : nullptr;
}

medialibrary::Query<medialibrary::IMedia>
AndroidMediaLibrary::searchFromMediaGroup( const int64_t groupId, const std::string& query, const medialibrary::QueryParameters* params )
{
    auto group = p_ml->mediaGroup(groupId);
    return group == nullptr ? nullptr : group->searchMedia(query, medialibrary::IMedia::Type::Video, params);
}

bool
AndroidMediaLibrary::groupAddId( const int64_t groupId, const int64_t mediaId )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr && group->add(mediaId);
}

bool
AndroidMediaLibrary::groupRemoveId( const int64_t groupId, const int64_t mediaId )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr && group->remove(mediaId);
}

std::string
AndroidMediaLibrary::groupName( const int64_t groupId )
{
    const medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group == nullptr ? nullptr : group->name();
}

bool
AndroidMediaLibrary::groupRename( const int64_t groupId, const std::string& name )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr && group->rename(name);
}

bool
AndroidMediaLibrary::groupUserInteracted( const int64_t groupId )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr && group->userInteracted();
}

int64_t
AndroidMediaLibrary::groupDuration( const int64_t groupId )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group == nullptr ? 0 : group->duration();
}

bool
AndroidMediaLibrary::groupDestroy( const int64_t groupId )
{
    medialibrary::MediaGroupPtr group = p_ml->mediaGroup(groupId);
    return group != nullptr && group->destroy();
}

medialibrary::MediaGroupPtr
AndroidMediaLibrary::createMediaGroup( std::string name )
{
    return p_ml->createMediaGroup(name);
}

bool
AndroidMediaLibrary::regroupAll()
{
    return p_ml->regroupAll();
}

bool
AndroidMediaLibrary::regroup(int64_t mediaId)
{
    auto media = p_ml->media(mediaId);
    return media != nullptr && media->regroup();
}

medialibrary::MediaGroupPtr
AndroidMediaLibrary::createMediaGroup( const std::vector<int64_t> mediaIds )
{
    return p_ml->createMediaGroup(mediaIds);
}

void
AndroidMediaLibrary::requestThumbnail( int64_t media_id, medialibrary::ThumbnailSizeType sizeType, uint32_t desiredWidth,
                                       uint32_t desiredHeight, float position )
{
    medialibrary::MediaPtr media = p_ml->media(media_id);
    if (media != nullptr) media->requestThumbnail(sizeType, desiredWidth, desiredHeight, position);
}

void AndroidMediaLibrary::setLibvlcInstance( libvlc_instance_t* inst )
{
    p_ml->setExternalLibvlcInstance( inst );
}

void
AndroidMediaLibrary::onMediaAdded( std::vector<medialibrary::MediaPtr> mediaList )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO | FLAG_MEDIA_ADDED_VIDEO | FLAG_MEDIA_ADDED_AUDIO_EMPTY | FLAG_MEDIA_ADDED_VIDEO_EMPTY)) {
        JNIEnv *env = getEnv();
        if (env == NULL /*|| env->IsSameObject(weak_thiz, NULL)*/)
            return;
        utils::jni::objectArray mediaRefs;
        int index;
        if ((m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_VIDEO)) == 0)
        {
            index = 0;
            mediaRefs = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(0, p_fields->MediaWrapper.clazz, NULL) };
        } else
        {
            mediaRefs = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(mediaList.size(), p_fields->MediaWrapper.clazz, NULL) };
            index = -1;
            for (medialibrary::MediaPtr const& media : mediaList) {
                medialibrary::IMedia::Type type = media->type();
                utils::jni::object item;
                if ((type == medialibrary::IMedia::Type::Audio && m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO) ||
                        (type == medialibrary::IMedia::Type::Video && m_mediaAddedType & FLAG_MEDIA_ADDED_VIDEO))
                {
                    item = mediaToMediaWrapper(env, p_fields, media);
                }
                env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
            }
        }

        if (index > -1)
        {
            if (weak_thiz)
            {
                auto results = filteredArray(env, std::move( mediaRefs ), p_fields->MediaWrapper.clazz, -1);
                env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaAddedId, results.get());
            }
        }
    }
}

void AndroidMediaLibrary::onMediaModified( std::set<int64_t> mediaIds )
{
    if (m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO || m_mediaUpdatedType & FLAG_MEDIA_UPDATED_VIDEO
            || m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO_EMPTY) {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaUpdatedId);
        }
    }
}

void AndroidMediaLibrary::onMediaDeleted( std::set<int64_t> ids )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO_EMPTY|FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_VIDEO|FLAG_MEDIA_ADDED_VIDEO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env != NULL && weak_thiz) {
            auto results = idArray(env, std::move(ids));
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaDeletedId, results.get());
        }
    }
}

void AndroidMediaLibrary::onMediaConvertedToExternal( std::set<int64_t> ids )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO_EMPTY|FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_VIDEO|FLAG_MEDIA_ADDED_VIDEO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env != NULL && weak_thiz) {
            auto results = idArray(env, std::move(ids));
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaConvertedToExternalId, results.get());
        }
    }
}

void AndroidMediaLibrary::onArtistsAdded( std::vector<medialibrary::ArtistPtr> artists )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO_EMPTY|FLAG_MEDIA_ADDED_AUDIO))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz) env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onArtistsAddedId);
    }
}

void AndroidMediaLibrary::onArtistsModified( std::set<int64_t>  artistsIds )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onArtistsModifiedId);
        }
    }
}

void AndroidMediaLibrary::onArtistsDeleted( std::set<int64_t> ids )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env != NULL || weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onArtistsDeletedId);
        }
    }
}

void AndroidMediaLibrary::onAlbumsAdded( std::vector<medialibrary::AlbumPtr> albums )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onAlbumsAddedId);
        }
    }
}

void AndroidMediaLibrary::onAlbumsModified( std::set<int64_t> albums )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onAlbumsModifiedId);
        }
    }
}

void AndroidMediaLibrary::onPlaylistsAdded( std::vector<medialibrary::PlaylistPtr> playlists )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onPlaylistsAddedId);
        }
    }

}

void AndroidMediaLibrary::onPlaylistsModified( std::set<int64_t> playlist )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onPlaylistsModifiedId);
        }
    }
}

void AndroidMediaLibrary::onPlaylistsDeleted( std::set<int64_t> ids )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onPlaylistsDeletedId);
        }
    }
}

void AndroidMediaLibrary::onGenresAdded( std::vector<medialibrary::GenrePtr> )
{
    if (m_mediaAddedType & (FLAG_MEDIA_ADDED_AUDIO|FLAG_MEDIA_ADDED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onGenresAddedId);
        }
    }
}

void AndroidMediaLibrary::onGenresModified( std::set<int64_t> )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onGenresModifiedId);
        }
    }
}

void AndroidMediaLibrary::onGenresDeleted( std::set<int64_t> )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onGenresDeletedId);
        }
    }
}

void AndroidMediaLibrary::onAlbumsDeleted( std::set<int64_t> )
{
    if (m_mediaUpdatedType & (FLAG_MEDIA_UPDATED_AUDIO|FLAG_MEDIA_UPDATED_AUDIO_EMPTY))
    {
        JNIEnv *env = getEnv();
        if (env == NULL) return;
        if (weak_thiz)
        {
            env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onAlbumsDeletedId);
        }
    }
}

void AndroidMediaLibrary::onDiscoveryStarted()
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onDiscoveryStartedId);
    }
}

void AndroidMediaLibrary::onDiscoveryProgress( const std::string& root )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onDiscoveryProgressId, ep.get());
    }
}

void AndroidMediaLibrary::onDiscoveryCompleted()
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onDiscoveryCompletedId);
    }
}

void AndroidMediaLibrary::onDiscoveryFailed(const std::string &root)
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onDiscoveryFailedId, ep.get());
    }
}

void AndroidMediaLibrary::onReloadStarted( const std::string& root )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onReloadStartedId, ep.get());
    }
}

void AndroidMediaLibrary::onReloadCompleted( const std::string& root, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onReloadCompletedId, ep.get());
    }
}

void AndroidMediaLibrary::onRootBanned( const std::string& root, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onRootBannedId, ep.get(), success);
    }
}

void AndroidMediaLibrary::onRootUnbanned( const std::string& root, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onRootUnbannedId, ep.get(), success);
    }
}

void AndroidMediaLibrary::onRootAdded( const std::string& root, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onRootAddedId, ep.get(), success);
    }
}

void AndroidMediaLibrary::onRootRemoved( const std::string& root, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    auto ep = vlcNewStringUTF(env, root.c_str());
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onRootRemovedId, ep.get(), success);
    }
}

void AndroidMediaLibrary::onParsingStatsUpdated( uint32_t opsDone, uint32_t opsScheduled )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onParsingStatsUpdatedId, opsDone, opsScheduled);
    }
}

void AndroidMediaLibrary::onHistoryChanged( medialibrary::HistoryType type)
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onHistoryChangedId, type);
    }
}


void AndroidMediaLibrary::onMediaGroupsAdded( std::vector<medialibrary::MediaGroupPtr> mediaGroups )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaGroupAddedId);
    }
}

void AndroidMediaLibrary::onMediaGroupsModified( std::set<int64_t> mediaGroupsIds )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaGroupModifiedId);
    }
}

void AndroidMediaLibrary::onMediaGroupsDeleted( std::set<int64_t> mediaGroupsIds )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaGroupDeletedId);
    }
}

void AndroidMediaLibrary::onFoldersAdded( std::vector<medialibrary::FolderPtr> folder )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onFoldersAddedId);
    }
}

void AndroidMediaLibrary::onFoldersModified( std::set<int64_t> foldersIds )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onFoldersModifiedId);
    }
}

void AndroidMediaLibrary::onFoldersDeleted( std::set<int64_t> foldersIds )
{
    JNIEnv *env = getEnv();
    if (env != nullptr && weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onFoldersDeletedId);
    }
}

void AndroidMediaLibrary::onBookmarksAdded( std::vector<medialibrary::BookmarkPtr> )
{
}

void AndroidMediaLibrary::onBookmarksModified( std::set<int64_t> )
{
}

void AndroidMediaLibrary::onBookmarksDeleted( std::set<int64_t> )
{
}

void AndroidMediaLibrary::onRescanStarted()
{
}

void AndroidMediaLibrary::onBackgroundTasksIdleChanged( bool isIdle )
{
    JNIEnv *env = getEnv();
    if (env == NULL) return;
    if (weak_thiz)
    {
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onBackgroundTasksIdleChangedId, isIdle);
    }
}

void AndroidMediaLibrary::onMediaThumbnailReady( medialibrary::MediaPtr media, medialibrary::ThumbnailSizeType sizeType, bool success )
{
    JNIEnv *env = getEnv();
    if (env != NULL && weak_thiz)
    {
        auto item = mediaToMediaWrapper(env, p_fields, media);
        env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onMediaThumbnailReadyId, item.get(), success);
    }
}

bool AndroidMediaLibrary::onUnhandledException( const char* context, const char* errMsg, bool clearSuggested )
{
    JNIEnv *env = getEnv();
    auto ctx = vlcNewStringUTF(env, context);
    auto msg = vlcNewStringUTF(env, errMsg);
    env->CallVoidMethod(weak_thiz, p_fields->MediaLibrary.onUnhandledExceptionId,
                        ctx.get(), msg.get(), clearSuggested);
    return true;
}

JNIEnv *
AndroidMediaLibrary::getEnv() {
    JNIEnv *env = (JNIEnv *)pthread_getspecific(jni_env_key);
    if (!env)
    {
        switch (myVm->GetEnv((void**)(&env), VLC_JNI_VERSION))
        {
        case JNI_OK:
            break;
        case JNI_EDETACHED:
            /* attach the thread to the Java VM */
            JavaVMAttachArgs args;
            args.version = VLC_JNI_VERSION;
            args.name = THREAD_NAME;
            args.group = NULL;
            if (myVm->AttachCurrentThread(&env, &args) != JNI_OK)
                return NULL;
            if (pthread_setspecific(jni_env_key, env) != 0)
            {
                detachCurrentThread();
                return NULL;
            }
            break;
        default:
            LOGE("failed to get env");
        }
    }
    return env;
}

void
AndroidMediaLibrary::detachCurrentThread() {
    myVm->DetachCurrentThread();
}

/*
 * Subscriptions
 */

bool
AndroidMediaLibrary::removeSubscription( int64_t id)
{
   return p_ml->removeSubscription( id );
}

medialibrary::ServicePtr
AndroidMediaLibrary::service( medialibrary::IService::Type type)
{
    return p_ml->service( type );
}

medialibrary::SubscriptionPtr
AndroidMediaLibrary::subscription( int64_t id)
{
    return p_ml->subscription( id );
}

bool
AndroidMediaLibrary::fitsInSubscriptionCache( const medialibrary::IMedia& m)
{
    return p_ml->fitsInSubscriptionCache( m );
}

void
AndroidMediaLibrary::cacheNewSubscriptionMedia()
{
    return p_ml->cacheNewSubscriptionMedia();
}

bool
AndroidMediaLibrary::setSubscriptionMaxCachedMedia( int nbMedia )
{
    return p_ml->setSubscriptionMaxCachedMedia( nbMedia );
}

bool
AndroidMediaLibrary::setSubscriptionMaxCacheSize( long size )
{
    return p_ml->setSubscriptionMaxCacheSize( size );
}

bool
AndroidMediaLibrary::setMaxCacheSize( long size )
{
    return p_ml->setMaxCacheSize( size );
}

uint32_t
AndroidMediaLibrary::getSubscriptionMaxCachedMedia()
{
    return p_ml->getSubscriptionMaxCachedMedia();
}

uint64_t
AndroidMediaLibrary::getSubscriptionMaxCacheSize()
{
    return p_ml->getSubscriptionMaxCacheSize();
}

uint64_t
AndroidMediaLibrary::getMaxCacheSize()
{
    return p_ml->getMaxCacheSize();
}

bool
AndroidMediaLibrary::refreshAllSubscriptions()
{
    return p_ml->refreshAllSubscriptions();
}

void AndroidMediaLibrary::onSubscriptionsAdded( std::vector<medialibrary::SubscriptionPtr> )
{
}

void AndroidMediaLibrary::onSubscriptionsModified( std::set<int64_t> )
{
}

void AndroidMediaLibrary::onSubscriptionsDeleted( std::set<int64_t> )
{
}

void AndroidMediaLibrary::onSubscriptionNewMedia( std::set<int64_t> )
{
}

void AndroidMediaLibrary::onSubscriptionCacheUpdated( int64_t subscriptionId )
{
}

void AndroidMediaLibrary::onCacheIdleChanged( bool idle )
{
}
