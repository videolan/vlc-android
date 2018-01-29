#include "AndroidMediaLibrary.h"
#define LOG_TAG "VLC/JNI/AndroidMediaLibrary"
#include "log.h"
#include "jniloader.h"

#define FLAG_MEDIA_UPDATED_AUDIO       1 << 0
#define FLAG_MEDIA_UPDATED_AUDIO_EMPTY 1 << 1
#define FLAG_MEDIA_UPDATED_VIDEO       1 << 2
#define FLAG_MEDIA_ADDED_AUDIO         1 << 3
#define FLAG_MEDIA_ADDED_AUDIO_EMPTY   1 << 4
#define FLAG_MEDIA_ADDED_VIDEO         1 << 5

static pthread_key_t jni_env_key;
static JavaVM *myVm;
static bool m_started = false;

static void jni_detach_thread(void *data)
{
   myVm->DetachCurrentThread();
}

static void key_init(void)
{
    pthread_key_create(&jni_env_key, jni_detach_thread);
}

AndroidMediaLibrary::AndroidMediaLibrary(JavaVM *vm, fields *ref_fields, jobject thiz)
    : p_ml( NewMediaLibrary() ), p_fields ( ref_fields )
{
    myVm = vm;
    p_lister = std::make_shared<AndroidDeviceLister>();
    p_ml->setLogger( new AndroidMediaLibraryLogger );
    p_ml->setVerbosity(medialibrary::LogLevel::Info);
    pthread_once(&key_once, key_init);
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    if (p_fields->MediaLibrary.getWeakReferenceID)
    {
        weak_thiz = nullptr;
        jobject weak_compat = env->CallObjectMethod(thiz, p_fields->MediaLibrary.getWeakReferenceID);
        if (weak_compat)
            this->weak_compat = env->NewGlobalRef(weak_compat);
        env->DeleteLocalRef(weak_compat);
    } else
    {
        weak_thiz = p_fields->MediaLibrary.getWeakReferenceID ? nullptr : env->NewWeakGlobalRef(thiz);
        weak_compat = nullptr;
    }
}

AndroidMediaLibrary::~AndroidMediaLibrary()
{
    LOGD("AndroidMediaLibrary delete");
    pthread_key_delete(jni_env_key);
    delete p_ml;
}

medialibrary::InitializeResult
AndroidMediaLibrary::initML(const std::string& dbPath, const std::string& thumbsPath)
{
    p_DeviceListerCb = p_ml->setDeviceLister(p_lister);
    return p_ml->initialize(dbPath, thumbsPath, this);
}

void
AndroidMediaLibrary::start()
{
    p_ml->start();
    m_started = true;
}

bool
AndroidMediaLibrary::addDevice(const std::string& uuid, const std::string& path, bool removable)
{
    p_lister->addDevice(uuid, path, removable);
    return p_DeviceListerCb != nullptr && (m_started ? p_DeviceListerCb->onDevicePlugged(uuid, path) : !p_DeviceListerCb->isDeviceKnown(uuid));
}

std::vector<std::tuple<std::string, std::string, bool>>
AndroidMediaLibrary::devices()
{
    return p_lister->devices();
}

bool
AndroidMediaLibrary::removeDevice(const std::string& uuid)
{
    bool removed = p_lister->removeDevice(uuid);
    if (removed && p_DeviceListerCb != nullptr)
        p_DeviceListerCb->onDeviceUnplugged(uuid);
    return removed;
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

void
AndroidMediaLibrary::discover(const std::string& libraryPath)
{
    p_ml->discover(libraryPath);
}

void
AndroidMediaLibrary::removeEntryPoint(const std::string& entryPoint)
{
    p_ml->removeEntryPoint(entryPoint);
}

std::vector<medialibrary::FolderPtr>
AndroidMediaLibrary::entryPoints()
{
    return p_ml->entryPoints();
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
AndroidMediaLibrary::reload( const std::string& entryPoint )
{
    p_ml->reload(entryPoint);
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

bool
AndroidMediaLibrary::increasePlayCount(int64_t mediaId)
{
    auto media = p_ml->media(mediaId);
    if (media != nullptr)
        return media->increasePlayCount();
    return false;
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::lastMediaPlayed()
{
    return p_ml->lastMediaPlayed();
}

bool
AndroidMediaLibrary::addToHistory( const std::string& mrl, const std::string& title)
{
    auto media = p_ml->media( mrl );
    if ( media == nullptr )
    {
        media = p_ml->addMedia( mrl );
        if ( media == nullptr )
            return false;
    }
    media->setTitle(title);
    return p_ml->addToStreamHistory( media );
}

std::vector<medialibrary::HistoryPtr>
AndroidMediaLibrary::lastStreamsPlayed()
{
    return p_ml->lastStreamsPlayed();
}

bool
AndroidMediaLibrary::clearHistory()
{
    return p_ml->clearHistory();
}

medialibrary::SearchAggregate
AndroidMediaLibrary::search(const std::string& query)
{
    return p_ml->search(query);
}

medialibrary::MediaSearchAggregate
AndroidMediaLibrary::searchMedia(const std::string& query)
{
    return p_ml->searchMedia(query);
}

std::vector<medialibrary::PlaylistPtr>
AndroidMediaLibrary::searchPlaylists(const std::string& query)
{
    return p_ml->searchPlaylists(query);
}

std::vector<medialibrary::AlbumPtr>
AndroidMediaLibrary::searchAlbums(const std::string& query)
{
    return p_ml->searchAlbums(query);
}

std::vector<medialibrary::GenrePtr>
AndroidMediaLibrary::searchGenre(const std::string& query)
{
    return p_ml->searchGenre(query);
}

std::vector<medialibrary::ArtistPtr>
AndroidMediaLibrary::searchArtists(const std::string& query)
{
    return p_ml->searchArtists(query);
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
AndroidMediaLibrary::addMedia(const std::string& mrl)
{
    return p_ml->addMedia(mrl);
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::videoFiles( medialibrary::SortingCriteria sort, bool desc )
{
    return p_ml->videoFiles(sort, desc);
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::audioFiles( medialibrary::SortingCriteria sort, bool desc )
{
    return p_ml->audioFiles(sort, desc);
}

std::vector<medialibrary::AlbumPtr>
AndroidMediaLibrary::albums()
{
    return p_ml->albums();
}

medialibrary::AlbumPtr
AndroidMediaLibrary::album(int64_t albumId)
{
    return p_ml->album(albumId);
}

std::vector<medialibrary::ArtistPtr>
AndroidMediaLibrary::artists(bool includeAll)
{
    return p_ml->artists(includeAll);
}

medialibrary::ArtistPtr
AndroidMediaLibrary::artist(int64_t artistId)
{
    return p_ml->artist(artistId);
}

std::vector<medialibrary::GenrePtr>
AndroidMediaLibrary::genres()
{
    return p_ml->genres();
}

medialibrary::GenrePtr
AndroidMediaLibrary::genre(int64_t genreId)
{
    return p_ml->genre(genreId);
}

std::vector<medialibrary::PlaylistPtr>
AndroidMediaLibrary::playlists()
{
    return p_ml->playlists();
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

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::tracksFromAlbum( int64_t albumId )
{
    auto album = p_ml->album(albumId);
    return album == nullptr ? std::vector<medialibrary::MediaPtr>() : album->tracks();
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::mediaFromArtist( int64_t artistId )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? std::vector<medialibrary::MediaPtr>() : artist->media(medialibrary::SortingCriteria::Album);
}

std::vector<medialibrary::AlbumPtr>
AndroidMediaLibrary::albumsFromArtist( int64_t artistId )
{
    auto artist = p_ml->artist(artistId);
    return artist == nullptr ? std::vector<medialibrary::AlbumPtr>() : artist->albums();
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::mediaFromGenre( int64_t genreId )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? std::vector<medialibrary::MediaPtr>() : genre->tracks(medialibrary::SortingCriteria::Album);
}

std::vector<medialibrary::AlbumPtr>
AndroidMediaLibrary::albumsFromGenre( int64_t genreId )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? std::vector<medialibrary::AlbumPtr>() : genre->albums();
}

std::vector<medialibrary::ArtistPtr>
AndroidMediaLibrary::artistsFromGenre( int64_t genreId )
{
    auto genre = p_ml->genre(genreId);
    return genre == nullptr ? std::vector<medialibrary::ArtistPtr>() : genre->artists();
}

std::vector<medialibrary::MediaPtr>
AndroidMediaLibrary::mediaFromPlaylist( int64_t playlistId )
{
    auto playlist =  p_ml->playlist(playlistId);
    return playlist == nullptr ? std::vector<medialibrary::MediaPtr>() : playlist->media();
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
AndroidMediaLibrary::playlistMove(int64_t playlistId, int64_t mediaId, unsigned int position) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->move(mediaId, position);
}

bool
AndroidMediaLibrary::playlistRemove(int64_t playlistId, int64_t mediaId) {
    medialibrary::PlaylistPtr playlist = p_ml->playlist(playlistId);
    return playlist == nullptr ? false : playlist->remove(mediaId);
}

bool
AndroidMediaLibrary::PlaylistDelete( int64_t playlistId )
{
    return p_ml->deletePlaylist(playlistId);
}

void
AndroidMediaLibrary::onMediaAdded( std::vector<medialibrary::MediaPtr> mediaList )
{
    if (m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO || m_mediaAddedType & FLAG_MEDIA_ADDED_VIDEO
            || m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO_EMPTY) {
        JNIEnv *env = getEnv();
        if (env == NULL /*|| env->IsSameObject(weak_thiz, NULL)*/)
            return;
        jobjectArray mediaRefs, results;
        int index;
        if (m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO_EMPTY)
        {
            index = 0;
            mediaRefs = (jobjectArray) env->NewObjectArray(0, p_fields->MediaWrapper.clazz, NULL);
        } else
        {
            mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), p_fields->MediaWrapper.clazz, NULL);
            index = -1;
            jobject item;
            for (medialibrary::MediaPtr const& media : mediaList) {
                medialibrary::IMedia::Type type = media->type();
                if ((type == medialibrary::IMedia::Type::Audio && m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO) ||
                        (type == medialibrary::IMedia::Type::Video && m_mediaAddedType & FLAG_MEDIA_ADDED_VIDEO))
                    item = mediaToMediaWrapper(env, p_fields, media);
                else
                    item = nullptr;
                env->SetObjectArrayElement(mediaRefs, ++index, item);
                if (item != nullptr)
                    env->DeleteLocalRef(item);
            }
        }

        if (index > -1)
        {
            jobject thiz = getWeakReference(env);
            if (thiz)
            {
                results = filteredArray(env, p_fields, mediaRefs, -1);
                env->CallVoidMethod(thiz, p_fields->MediaLibrary.onMediaAddedId, results);
                if (weak_compat)
                    env->DeleteLocalRef(thiz);
                env->DeleteLocalRef(results);
            } else
                env->DeleteLocalRef(mediaRefs);
        }
    }
}

void AndroidMediaLibrary::onMediaUpdated( std::vector<medialibrary::MediaPtr> mediaList )
{
    if (m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO || m_mediaUpdatedType & FLAG_MEDIA_UPDATED_VIDEO
            || m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO_EMPTY) {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        jobjectArray mediaRefs, results;
        int index;
        if (m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO_EMPTY)
        {
            index = 0;
            mediaRefs = (jobjectArray) env->NewObjectArray(0, p_fields->MediaWrapper.clazz, NULL);
        } else
        {
            mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), p_fields->MediaWrapper.clazz, NULL);
            index = -1;
            jobject item;
            for (medialibrary::MediaPtr const& media : mediaList) {
                medialibrary::IMedia::Type type = media->type();
                if ((type == medialibrary::IMedia::Type::Audio && m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO) ||
                        (type == medialibrary::IMedia::Type::Video && m_mediaUpdatedType & FLAG_MEDIA_UPDATED_VIDEO))
                    item = mediaToMediaWrapper(env, p_fields, media);
                else
                    item = nullptr;
                env->SetObjectArrayElement(mediaRefs, ++index, item);
                if (item != nullptr)
                    env->DeleteLocalRef(item);
            }
        }
        if (index > -1)
        {
            jobject thiz = getWeakReference(env);
            results = filteredArray(env, p_fields, mediaRefs, -1);
            if (thiz)
            {
                env->CallVoidMethod(thiz, p_fields->MediaLibrary.onMediaUpdatedId, results);
                if (weak_compat)
                    env->DeleteLocalRef(thiz);
                env->DeleteLocalRef(results);
            } else
                env->DeleteLocalRef(mediaRefs);
        }
    }
}

void AndroidMediaLibrary::onMediaDeleted( std::vector<int64_t> ids )
{

}

void AndroidMediaLibrary::onArtistsAdded( std::vector<medialibrary::ArtistPtr> artists )
{
    if (m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO)
    {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        jobject thiz = getWeakReference(env);
        if (thiz)
        {
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onArtistsAddedId);
            if (weak_compat)
                env->DeleteLocalRef(thiz);
        }
    }
}

void AndroidMediaLibrary::onArtistsModified( std::vector<medialibrary::ArtistPtr> artist )
{
    if (m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO)
    {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        jobject thiz = getWeakReference(env);
        if (thiz)
        {
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onArtistsModifiedId);
            if (weak_compat)
                env->DeleteLocalRef(thiz);
        }
    }
}

void AndroidMediaLibrary::onArtistsDeleted( std::vector<int64_t> ids )
{

}

void AndroidMediaLibrary::onAlbumsAdded( std::vector<medialibrary::AlbumPtr> albums )
{
    if (m_mediaAddedType & FLAG_MEDIA_ADDED_AUDIO)
    {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        jobject thiz = getWeakReference(env);
        if (thiz)
        {
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onAlbumsAddedId);
            if (weak_compat)
                env->DeleteLocalRef(thiz);
        }
    }
}

void AndroidMediaLibrary::onAlbumsModified( std::vector<medialibrary::AlbumPtr> albums )
{
    if (m_mediaUpdatedType & FLAG_MEDIA_UPDATED_AUDIO)
    {
        JNIEnv *env = getEnv();
        if (env == NULL)
            return;
        jobject thiz = getWeakReference(env);
        if (thiz)
        {
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onAlbumsModifiedId);
            if (weak_compat)
                env->DeleteLocalRef(thiz);
        }
    }
}

void AndroidMediaLibrary::onPlaylistsAdded( std::vector<medialibrary::PlaylistPtr> playlists )
{

}

void AndroidMediaLibrary::onPlaylistsModified( std::vector<medialibrary::PlaylistPtr> playlist )
{

}

void AndroidMediaLibrary::onPlaylistsDeleted( std::vector<int64_t> ids )
{

}

void AndroidMediaLibrary::onAlbumsDeleted( std::vector<int64_t> ids )
{

}

void AndroidMediaLibrary::onTracksAdded( std::vector<medialibrary::AlbumTrackPtr> tracks )
{

}
void AndroidMediaLibrary::onTracksDeleted( std::vector<int64_t> trackIds )
{

}

void AndroidMediaLibrary::onDiscoveryStarted( const std::string& entryPoint )
{
    ++m_nbDiscovery;
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz != NULL)
    {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onDiscoveryStartedId, ep);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onDiscoveryProgress( const std::string& entryPoint )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz)
    {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onDiscoveryProgressId, ep);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);

}

void AndroidMediaLibrary::onDiscoveryCompleted( const std::string& entryPoint )
{
    --m_nbDiscovery;
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        if (m_progress)
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onParsingStatsUpdatedId, m_progress);
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onDiscoveryCompletedId, ep);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onReloadStarted( const std::string& entryPoint )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onReloadStartedId, ep);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onReloadCompleted( const std::string& entryPoint )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        if (m_progress)
            env->CallVoidMethod(thiz, p_fields->MediaLibrary.onParsingStatsUpdatedId, m_progress);
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onReloadCompletedId, ep);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onEntryPointBanned( const std::string& entryPoint, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onEntryPointBannedId, ep, success);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onEntryPointUnbanned( const std::string& entryPoint, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onEntryPointUnbannedId, ep, success);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onEntryPointRemoved( const std::string& entryPoint, bool success )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jstring ep = env->NewStringUTF(entryPoint.c_str());
    jobject thiz = getWeakReference(env);
    if (thiz) {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onEntryPointRemovedId, ep, success);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
    env->DeleteLocalRef(ep);
}

void AndroidMediaLibrary::onParsingStatsUpdated( uint32_t percent)
{
    m_progress = percent;
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jint progress = percent;
    jobject thiz = getWeakReference(env);
    if (thiz != NULL)
    {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onParsingStatsUpdatedId, progress);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
}


void AndroidMediaLibrary::onBackgroundTasksIdleChanged( bool isIdle )
{
    JNIEnv *env = getEnv();
    if (env == NULL)
        return;
    jobject thiz = getWeakReference(env);
    if (thiz != NULL)
    {
        env->CallVoidMethod(thiz, p_fields->MediaLibrary.onBackgroundTasksIdleChangedId, isIdle);
        if (weak_compat)
            env->DeleteLocalRef(thiz);
    }
}

jobject
AndroidMediaLibrary::getWeakReference(JNIEnv *env)
{
    return weak_thiz != nullptr ? weak_thiz : env->CallObjectMethod(weak_compat, p_fields->WeakReference.getID);
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
            if (myVm->AttachCurrentThread(&env, NULL) != JNI_OK)
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
