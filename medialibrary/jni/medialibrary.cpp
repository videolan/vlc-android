#include <stdlib.h>

#include <algorithm>
#include <string>

#include <jni.h>
#include <medialibrary/IDeviceLister.h>
#include <medialibrary/filesystem/IDevice.h>
#define LOG_TAG "VLC/JNI/MediaLibrary"
#include "log.h"
#include "utils.h"
#include "AndroidMediaLibrary.h"
#include <medialibrary/filesystem/Errors.h>

static JavaVM *myVm;
static fields ml_fields;
static bool m_IsInitialized = false;

#define CLASSPATHNAME "org/videolan/medialibrary/MedialibraryImpl"

static inline void throw_IllegalStateException(JNIEnv *env, const char *p_error);
static inline void throw_IllegalArgumentException(JNIEnv *env, const char *p_error);
static AndroidMediaLibrary *MediaLibrary_getInstanceInternal(JNIEnv *env, jobject thiz);
AndroidMediaLibrary *MediaLibrary_getInstance(JNIEnv *env, jobject thiz);


static void
MediaLibrary_setInstance(JNIEnv *env, jobject thiz, AndroidMediaLibrary *p_obj);

void
constructML(JNIEnv* env, jobject thiz, jstring dbPath, jstring thumbsPath)
{
    const char *thumbs_utfchars = env->GetStringUTFChars(thumbsPath, JNI_FALSE);
    const char *db_utfchars = env->GetStringUTFChars(dbPath, JNI_FALSE);
    AndroidMediaLibrary *aml = new  AndroidMediaLibrary(myVm, &ml_fields, thiz, db_utfchars, thumbs_utfchars);
    MediaLibrary_setInstance(env, thiz, aml);
}

jint
init(JNIEnv* env, jobject thiz, jstring thumbsPath)
{
    const char *thumbs_utfchars = env->GetStringUTFChars(thumbsPath, JNI_FALSE);
    medialibrary::InitializeResult initCode = MediaLibrary_getInstance(env, thiz)->initML();
    m_IsInitialized = initCode != medialibrary::InitializeResult::Failed;
    env->ReleaseStringUTFChars(thumbsPath, thumbs_utfchars);
    return (int) initCode;
}

void
release(JNIEnv* env, jobject thiz)
{
    LOGD("/!\\ release medialib. /!\\");
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    delete aml;
    MediaLibrary_setInstance(env, thiz, NULL);
}

medialibrary::QueryParameters generateParams(medialibrary::SortingCriteria sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    medialibrary::QueryParameters params {
       sortingCriteria,
       static_cast<bool>( desc ),
       static_cast<bool>( includeMissing ),
       false,
       static_cast<bool>( onlyFavorites )
    };
    return params;
}
medialibrary::QueryParameters generateParams(jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    return generateParams(static_cast<medialibrary::SortingCriteria>(sortingCriteria), desc, includeMissing, onlyFavorites);
}

void
clearDatabase(JNIEnv* env, jobject thiz, jboolean restorePlaylists) {
    MediaLibrary_getInstance(env, thiz)->clearDatabase(restorePlaylists);
}

void
banFolder(JNIEnv* env, jobject thiz, jstring folderPath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(folderPath, JNI_FALSE);
    aml->banFolder(path);
    env->ReleaseStringUTFChars(folderPath, path);
}

void
unbanFolder(JNIEnv* env, jobject thiz, jstring folderPath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(folderPath, JNI_FALSE);
    aml->unbanFolder(path);
    env->ReleaseStringUTFChars(folderPath, path);
}

jobjectArray
bannedFolders(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::FolderPtr> folders = aml->bannedRoots();


    std::vector<std::string> mrls;
    mrls.reserve(folders.size());
    for(medialibrary::FolderPtr& folder : folders) {
        try
        {
            mrls.push_back( folder->mrl() );
        }
        catch ( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Just ignore, the device isn't available anymore
        }
    }
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mrls.size(), env->FindClass("java/lang/String"), NULL);
    int index = -1;
    for( const std::string& m : mrls ) {
        auto mrl = vlcNewStringUTF(env, m.c_str());
        env->SetObjectArrayElement(mediaRefs, ++index, mrl.get());
    }
    return mediaRefs;
}

void
addDevice(JNIEnv* env, jobject thiz, jstring uuid, jstring storagePath, jboolean removable)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *uuidChar = env->GetStringUTFChars(uuid, JNI_FALSE);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    aml->addDevice(uuidChar, path, removable);
    env->ReleaseStringUTFChars(uuid, uuidChar);
    env->ReleaseStringUTFChars(storagePath, path);
}

jboolean
isDeviceKnown(JNIEnv* env, jobject thiz, jstring uuid, jstring storagePath, jboolean removable)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *uuidChar = env->GetStringUTFChars(uuid, JNI_FALSE);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    jboolean isNew = aml->isDeviceKnown(uuidChar, path, removable);
    env->ReleaseStringUTFChars(uuid, uuidChar);
    env->ReleaseStringUTFChars(storagePath, path);
    return isNew;
}

jboolean
deleteRemovableDevices(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    jboolean isDeleted = aml->deleteRemovableDevices();
    return isDeleted;
}

jobjectArray
devices(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    auto devices = aml->devices();
    jobjectArray deviceRefs = (jobjectArray) env->NewObjectArray(devices.size(), env->FindClass("java/lang/String"), NULL);
    int index = -1;
    for(auto device : devices) {
        auto path = vlcNewStringUTF(env, std::get<1>(device).c_str());
        env->SetObjectArrayElement(deviceRefs, ++index, path.get());
    }
    return deviceRefs;
}

void
discover(JNIEnv* env, jobject thiz, jstring storagePath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    aml->discover(path);
    env->ReleaseStringUTFChars(storagePath, path);
}

jboolean
setDiscoverNetworkEnabled(JNIEnv* env, jobject thiz, jboolean enabled)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    return aml->setDiscoverNetworkEnabled(enabled);
}

void
setLibVLCInstance(JNIEnv* env, jobject thiz, jlong libVLC){
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->setLibvlcInstance( (libvlc_instance_t*)libVLC );
}

void
removeRoot(JNIEnv* env, jobject thiz, jstring storagePath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    aml->removeRoot(path);
    env->ReleaseStringUTFChars(storagePath, path);
}

jobjectArray
roots(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::FolderPtr> roots = aml->roots();
    std::vector<std::string> mrls;
    mrls.reserve(roots.size());
    for(medialibrary::FolderPtr& root : roots) {
        try
        {
            mrls.push_back( root->mrl() );
        }
        catch ( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Just ignore, the device isn't available anymore
        }
    }
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mrls.size(), env->FindClass("java/lang/String"), NULL);
    int index = -1;
    for( const std::string& m : mrls ) {
        auto mrl = vlcNewStringUTF(env, m.c_str());
        env->SetObjectArrayElement(mediaRefs, ++index, mrl.get());
    }
    return mediaRefs;
}

jboolean
removeDevice(JNIEnv* env, jobject thiz, jstring uuid, jstring storagePath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *uuidChar = env->GetStringUTFChars(uuid, JNI_FALSE);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    jboolean removed = aml->removeDevice(uuidChar, path);
    env->ReleaseStringUTFChars(uuid, uuidChar);
    env->ReleaseStringUTFChars(storagePath, path);
    return removed;
}

void
setMediaUpdatedCbFlag(JNIEnv* env, jobject thiz, jint flags)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->setMediaUpdatedCbFlag(flags);
}

void
setMediaAddedCbFlag(JNIEnv* env, jobject thiz, jint flags)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->setMediaAddedCbFlag(flags);
}

void
pauseBackgroundOperations(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->pauseBackgroundOperations();
}

void
resumeBackgroundOperations(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->resumeBackgroundOperations();
}

void
reload(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->reload();
}

void
reloadRoot(JNIEnv* env, jobject thiz, jstring root)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(root, JNI_FALSE);
    aml->reload(path);
    env->ReleaseStringUTFChars(root, path);
}

void
forceParserRetry(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->forceParserRetry();
}

void
forceRescan(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->forceRescan();
}

jboolean
setLastTime(JNIEnv* env, jobject thiz, jlong id, jlong time)
{
    return MediaLibrary_getInstance(env, thiz)->setLastTime((int64_t)id, (int64_t)time);
}

jboolean
setLastPosition(JNIEnv* env, jobject thiz, jlong id, jfloat lastPosition)
{
    return MediaLibrary_getInstance(env, thiz)->setLastPosition((int64_t)id, (float)lastPosition);
}

jboolean
removeMediaFromHistory(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    return MediaLibrary_getInstance(env, medialibrary)->removeMediaFromHistory((int64_t)id);
}

jobjectArray
history(JNIEnv* env, jobject thiz, jint type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::MediaPtr> mediaPlayed = aml->history((medialibrary::HistoryType)type);
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(mediaPlayed.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaPlayed) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr)
            ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jboolean
addToHistory(JNIEnv* env, jobject thiz, jstring mrl, jstring title)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *mrl_cstr = env->GetStringUTFChars(mrl, JNI_FALSE);
    const char *title_cstr = env->GetStringUTFChars(title, JNI_FALSE);
    jboolean ok = aml->addToHistory(mrl_cstr, title_cstr);
    env->ReleaseStringUTFChars(mrl, mrl_cstr);
    env->ReleaseStringUTFChars(title, title_cstr);
    return ok;
}

bool clearHistory(JNIEnv* env, jobject thiz, jint type)
{
    return MediaLibrary_getInstance(env, thiz)->clearHistory((medialibrary::HistoryType) type);
}

static jobjectArray
getInternalVideos(JNIEnv* env, jobject thiz, const medialibrary::QueryParameters* params = nullptr, jint nbItems = 0,  jint offset = 0 )
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const auto query = aml->videoFiles(params);
    std::vector<medialibrary::MediaPtr> videoFiles = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    utils::jni::objectArray videoRefs{ env, (jobjectArray) env->NewObjectArray(videoFiles.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : videoFiles) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(videoRefs.get(), ++index, item.get());
        if (item == nullptr)
            ++drops;
    }
    return filteredArray(env, std::move( videoRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jobjectArray
getVideos(JNIEnv* env, jobject thiz)
{
    return getInternalVideos(env, thiz);
}

jobjectArray
getSortedVideos(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    return getInternalVideos(env, thiz, &params );
}

jobjectArray
getPagedVideos(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites, jint nbItems,  jint offset)
{
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    return getInternalVideos(env, thiz, &params, nbItems, offset );
}

jobjectArray
getRecentVideos(JNIEnv* env, jobject thiz)
{
    medialibrary::QueryParameters params = generateParams(medialibrary::SortingCriteria::InsertionDate, true, true, false);
    return getInternalVideos(env, thiz, &params);
}

static jobjectArray
getInternalAudio(JNIEnv* env, jobject thiz, const medialibrary::QueryParameters* params = nullptr, jint nbItems = 0,  jint offset = 0 )
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const auto query = aml->audioFiles(params);
    std::vector<medialibrary::MediaPtr> audioFiles = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    utils::jni::objectArray audioRefs{ env, (jobjectArray) env->NewObjectArray(audioFiles.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : audioFiles) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(audioRefs.get(), ++index, item.get());
        if (item == nullptr)
            ++drops;
    }
    return filteredArray(env, std::move( audioRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jobjectArray
getAudio(JNIEnv* env, jobject thiz)
{
    return getInternalAudio(env, thiz);
}

jobjectArray
getRecentAudio(JNIEnv* env, jobject thiz, jboolean includeMissing, jboolean onlyFavorites)
{
    medialibrary::QueryParameters params = generateParams(medialibrary::SortingCriteria::InsertionDate, true, includeMissing, onlyFavorites);
    return getInternalAudio(env, thiz, &params );
}

jobjectArray
getSortedAudio(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    return getInternalAudio(env, thiz, &params);
}

jobjectArray
getPagedAudio(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    return getInternalAudio(env, thiz, &params, nbItems, offset);
}

jobject
search(JNIEnv* env, jobject thiz, jstring query, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    auto searchResult = convertSearchAggregateObject(env, &ml_fields, aml->search(queryChar), includeMissing, onlyFavorites);
    env->ReleaseStringUTFChars(query, queryChar);
    return searchResult.release();
}

jobjectArray
searchMedia(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    auto searchResult = aml->searchMedia(queryChar)->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item.get());
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return mediaList;
}

jobjectArray
searchPagedMedia(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchMedia(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaList;
}

jobjectArray
searchPagedAudio(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAudio(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaList;
}

jobjectArray
searchPagedVideo(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchVideo(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaList;
}

jint
getSearchVideoCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count =  MediaLibrary_getInstance(env, thiz)->searchVideo(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jint
getSearchAudioCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count =  MediaLibrary_getInstance(env, thiz)->searchAudio(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jint
getSearchMediaCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count =  MediaLibrary_getInstance(env, thiz)->searchMedia(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jobjectArray
searchArtist(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    std::vector<medialibrary::ArtistPtr> artists = aml->searchArtists(queryChar)->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return artistRefs;
}

jobjectArray
searchPagedArtist(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchArtists(queryChar, &params);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return artistRefs;
}

jint
getArtistsSearchCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count = (jint) MediaLibrary_getInstance(env, thiz)->searchArtists(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jobjectArray
searchAlbum(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    std::vector<medialibrary::AlbumPtr> albums = aml->searchAlbums(queryChar)->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return albumRefs;
}

jobjectArray
searchPagedAlbum(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAlbums(queryChar, &params);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return albumRefs;
}

jint
getAlbumSearchCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count = (jint) MediaLibrary_getInstance(env, thiz)->searchAlbums(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jobjectArray
searchGenre(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    std::vector<medialibrary::GenrePtr> genres = aml->searchGenre(queryChar)->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        auto item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return genreRefs;
}

jobjectArray
searchPagedGenre(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchGenre(queryChar, &params);
    std::vector<medialibrary::GenrePtr> genres = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        auto item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return genreRefs;
}

jint
getGenreSearchCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count = (jint) MediaLibrary_getInstance(env, thiz)->searchGenre(queryChar)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jobjectArray
searchPlaylist(JNIEnv* env, jobject thiz, jstring query, medialibrary::PlaylistType type, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    std::vector<medialibrary::PlaylistPtr> playlists = aml->searchPlaylists(queryChar, type)->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        auto item = convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites);
        env->SetObjectArrayElement(playlistRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return playlistRefs;
}

jobjectArray
searchPagedPlaylist(JNIEnv* env, jobject thiz, jstring filterQuery, medialibrary::PlaylistType type, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchPlaylists(queryChar, type, &params);
    std::vector<medialibrary::PlaylistPtr> playlists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        auto item = convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites);
        env->SetObjectArrayElement(playlistRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return playlistRefs;
}

jint
getPlaylistSearchCount(JNIEnv* env, jobject thiz, jstring filterQuery, medialibrary::PlaylistType type) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count = (jint) MediaLibrary_getInstance(env, thiz)->searchPlaylists(queryChar, type)->count();
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return count;
}

jint
getVideoCount(JNIEnv* env, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->videoFiles()->count();
}

jint
getAudioCount(JNIEnv* env, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->audioFiles()->count();
}

jobject
getMedia(JNIEnv* env, jobject thiz, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    return mediaToMediaWrapper(env, &ml_fields, aml->media(id)).release();
}

jobject
getMediaFromMrl(JNIEnv* env, jobject thiz, jstring mrl) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    auto mw = mediaToMediaWrapper(env, &ml_fields, aml->media(char_mrl));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    return mw.release();
}

jobject
addMedia(JNIEnv* env, jobject thiz, jstring mrl, jlong duration) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    auto mw = mediaToMediaWrapper(env, &ml_fields, aml->addMedia(char_mrl, duration));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    return mw.release();
}

jboolean
removeExternalMedia(JNIEnv* env, jobject thiz, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    return aml->removeExternalMedia(id);
}

jboolean
flushUserProvidedThumbnails(JNIEnv* env, jobject thiz) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    return aml->flushUserProvidedThumbnails();
}

jobject
addStream(JNIEnv* env, jobject thiz, jstring mrl, jstring title) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    const char *char_title = env->GetStringUTFChars(title, JNI_FALSE);
    auto mw = mediaToMediaWrapper(env, &ml_fields, aml->addStream(char_mrl, char_title));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    env->ReleaseStringUTFChars(title, char_title);
    return mw.release();
}

jobjectArray
getAlbums(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::AlbumPtr> albums = aml->albums( &params )->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item.get());
    }
    return albumRefs;
}

jobjectArray
getPagedAlbums(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->albums(&params);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item.get());
    }
    return albumRefs;
}

jint
getAlbumsCount(JNIEnv* env, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->albums(nullptr)->count();
}

jobject
getAlbum(JNIEnv* env, jobject thiz, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::AlbumPtr album = aml->album(id);
    if (!album)
        return nullptr;
    return convertAlbumObject(env, &ml_fields, album).release();
}

jobjectArray
getArtists(JNIEnv* env, jobject thiz, jboolean all, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::ArtistPtr> artists = aml->artists(all, &params)->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item.get());
    }
    return artistRefs;
}

jobjectArray
getPagedArtists(JNIEnv* env, jobject thiz, jboolean all, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->artists(all, &params);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item.get());
    }
    return artistRefs;
}

jint
getArtistsCount(JNIEnv* env, jobject thiz, jboolean all) {
    return (jint) MediaLibrary_getInstance(env, thiz)->artists(all, nullptr)->count();
}

jobject
getArtist(JNIEnv* env, jobject thiz, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::ArtistPtr artist = aml->artist(id);
    if (!artist)
        return nullptr;
    return convertArtistObject(env, &ml_fields, artist).release();
}

jobjectArray
getGenres(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::GenrePtr> genres = aml->genres( &params )->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        auto item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item.get());
    }
    return genreRefs;
}

jobjectArray
getPagedGenres(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->genres(&params);
    std::vector<medialibrary::GenrePtr> genres = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        auto item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item.get());
    }
    return genreRefs;
}

jint
getGenresCount(JNIEnv* env, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->genres(nullptr)->count();
}

jobject
getGenre(JNIEnv* env, jobject thiz, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::GenrePtr genre = aml->genre(id);
    if (!genre)
        return nullptr;
    return convertGenreObject(env, &ml_fields, genre).release();
}

jobjectArray
getPlaylists(JNIEnv* env, jobject thiz, medialibrary::PlaylistType type, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::PlaylistPtr> playlists = aml->playlists(type, &params)->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        auto item = convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites);
        env->SetObjectArrayElement(playlistRefs, ++index, item.get());
    }
    return playlistRefs;
}


jobjectArray
getPagedPlaylists(JNIEnv* env, jobject thiz, medialibrary::PlaylistType type, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->playlists(type, &params);
    std::vector<medialibrary::PlaylistPtr> playlists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        auto item = convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites);
        env->SetObjectArrayElement(playlistRefs, ++index, item.get());
    }
    return playlistRefs;
}

jint
getPlaylistsCount(JNIEnv* env, medialibrary::PlaylistType type, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->playlists(type, nullptr)->count();
}

jobject
getPlaylist(JNIEnv* env, jobject thiz, jlong id, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::PlaylistPtr playlist = aml->playlist(id);
    if (!playlist)
        return nullptr;
    return convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites).release();
}

jobject
playlistCreate(JNIEnv* env, jobject thiz, jstring name, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *name_cstr = env->GetStringUTFChars(name, JNI_FALSE);
    medialibrary::PlaylistPtr playlist = aml->PlaylistCreate(name_cstr);
    env->ReleaseStringUTFChars(name, name_cstr);
    if (!playlist)
        return nullptr;
    return convertPlaylistObject(env, &ml_fields, playlist, includeMissing, onlyFavorites).release();
}

void
requestThumbnail(JNIEnv* env, jobject thiz, jobject medialibrary, jlong mediaId, medialibrary::ThumbnailSizeType sizeType, uint32_t desiredWidth,
                 uint32_t desiredHeight, float position)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    aml->requestThumbnail(mediaId, sizeType, desiredWidth, desiredHeight, position);
}

/*
 * Album methods
 */


jobjectArray
getTracksFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->tracksFromAlbum(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> tracks = query->all();
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(tracks.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : tracks) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr) ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jint
getTracksFromAlbumCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->tracksFromAlbum(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getPagedTracksFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->tracksFromAlbum(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jobjectArray
searchFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromAlbum(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchFromAlbumCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromAlbum(id, queryChar, nullptr);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

/*
 * Artist methods
 */

jobjectArray
getMediaFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr) ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jobjectArray
getPagedMediaFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jobjectArray
searchFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromArtist(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchFromArtistCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromArtist(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->albumsFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item.get());
    }
    return albumsRefs;
}

jobjectArray
getPagedAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->albumsFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item.get());
    }
    return albumsRefs;
}

jobjectArray
searchAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAlbumsFromArtist(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    }
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return albumsRefs;
}

jint
getSearchAlbumFromArtistCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchAlbumsFromArtist(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}


/*
 * Genre methods
 */

jobjectArray
getMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean withThumbnail, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromGenre(id, withThumbnail, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr)
            ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jobjectArray
getPagedMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean withThumbnail, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromGenre(id, withThumbnail, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jobjectArray
searchMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromGenre(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchMediaFromGenreCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromGenre(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->albumsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item.get());
    }
    return albumRefs;
}

jobjectArray
getPagedAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->albumsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item.get());
    }
    return albumsRefs;
}

jint
getGenreAlbumsCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->albumsFromGenre(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAlbumsFromGenre(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    }
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        auto item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return albumsRefs;
}

jint
getSearchAlbumsFromGenreCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchAlbumsFromGenre(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getArtistsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->artistsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Artist.clazz, NULL);
    std::vector<medialibrary::ArtistPtr> artists = query->all();
    jobjectArray artistsRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistsRefs, ++index, item.get());
    }
    return artistsRefs;
}

jobjectArray
getPagedArtistsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->artistsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Artist.clazz, NULL);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistsRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        auto item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistsRefs, ++index, item.get());
    }
    return artistsRefs;
}

jint
getGenreArtistsCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->artistsFromGenre(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}


/*
 * Media methods
 */

jlong
getMediaLongMetadata(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint metadataType)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    const medialibrary::IMetadata& metadata = media->metadata((medialibrary::IMedia::MetadataType)metadataType);
    return metadata.isSet() ? metadata.asInt() : 0L;
}

jobjectArray
getBookmarks(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    const auto query = media->bookmarks(nullptr);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Bookmark.clazz, NULL);
    std::vector<medialibrary::BookmarkPtr> bookmarks = query->all();
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(bookmarks.size(), ml_fields.Bookmark.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::BookmarkPtr const& bookmark : bookmarks) {
        auto item = convertBookmarkObject(env, &ml_fields, bookmark);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr) ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.Bookmark.clazz, drops).release();
}

jobject
addBookmark(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong time)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    medialibrary::BookmarkPtr bookmark = media->addBookmark(time);
    if (!bookmark)
        return nullptr;
    return convertBookmarkObject(env, &ml_fields, bookmark).release();
}

jboolean
removeBookmark(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong time)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    return media->removeBookmark(time);
}

jboolean
removeAllBookmarks(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    return media->removeAllBookmarks();
}

jboolean
markAsPlayed(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    return media->markAsPlayed();
}

jboolean
setMediaFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setArtistFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::ArtistPtr media = aml->artist(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setAlbumFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::AlbumPtr media = aml->album(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setGenreFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::GenrePtr media = aml->genre(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setFolderFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::FolderPtr media = aml->folder(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setVideoGroupFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaGroupPtr media = aml->videoGroup(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setPlaylistFavorite(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean favorite) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::PlaylistPtr media = aml->playlist(id);
    if (media == nullptr) return 0L;
    return media->setFavorite(favorite);
}

jboolean
setBookmarkName(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring name) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const medialibrary::BookmarkPtr bookmark = aml->bookmark(id);
    const bool result = bookmark->setName(char_name);
    env->ReleaseStringUTFChars(name, char_name);
    return result;
}

jboolean
setBookmarkDescription(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring description) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const char *char_description = env->GetStringUTFChars(description, JNI_FALSE);
    const medialibrary::BookmarkPtr bookmark = aml->bookmark(id);
    const bool result = bookmark->setDescription(char_description);
    env->ReleaseStringUTFChars(description, char_description);
    return result;
}

jboolean
setBookmarkNameAndDescription(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring name, jstring description) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const char *char_description = env->GetStringUTFChars(description, JNI_FALSE);
    const medialibrary::BookmarkPtr bookmark = aml->bookmark(id);
    const bool result = bookmark->setNameAndDescription(char_name, char_description);
    env->ReleaseStringUTFChars(name, char_name);
    env->ReleaseStringUTFChars(description, char_description);
    return result;
}

jboolean
bookmarkMove(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong time) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const medialibrary::BookmarkPtr bookmark = aml->bookmark(id);
    return bookmark->move(time);
}

jobject
getMediaStringMetadata(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint metadataType)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    const medialibrary::IMetadata& metadata = media->metadata((medialibrary::IMedia::MetadataType)metadataType);
    if (!metadata.isSet())
        return nullptr;
    return vlcNewStringUTF(env, metadata.asStr().c_str()).release();
}

void
setMediaStringMetadata(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint metadataType, jstring meta)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return;
    const char *char_meta = env->GetStringUTFChars(meta, JNI_FALSE);
    media->setMetadata((medialibrary::IMedia::MetadataType)metadataType, char_meta);
    env->ReleaseStringUTFChars(meta, char_meta);
}

void
setMediaLongMetadata(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint metadataType, jlong meta)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return;
    media->setMetadata((medialibrary::IMedia::MetadataType)metadataType, meta);
}

void
setMediaThumbnail(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring mrl)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return;
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    media->setThumbnail(char_mrl, medialibrary::ThumbnailSizeType::Thumbnail);
    env->ReleaseStringUTFChars(mrl, char_mrl);
}

jboolean
setMediaPlayCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong playCount)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return false;
    return media->setPlayCount(playCount);
}

jlong
getMediaPlayCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return -1;
    return media->playCount();
}

jboolean
removeMediaThumbnail(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return false;
    return media->removeThumbnail(medialibrary::ThumbnailSizeType::Thumbnail);
}

void
setMediaTitle(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring title)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return;
    const char *char_name = env->GetStringUTFChars(title, JNI_FALSE);
    media->setTitle(char_name);
    env->ReleaseStringUTFChars(title, char_name);
}

/*
 * Playlist methods
 */

jobjectArray
getMediaFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(medialibrary::SortingCriteria::Default, false, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromPlaylist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL) };
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
        if (item == nullptr)
            ++drops;
    }
    return filteredArray(env, std::move( mediaRefs ), ml_fields.MediaWrapper.clazz, drops).release();
}

jobjectArray
getPagedMediaFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint nbItems,  jint offset, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(medialibrary::SortingCriteria::Default, false, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromPlaylist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jint
getPlaylistTracksCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean includeMissing, jboolean onlyFavorites) {
    medialibrary::QueryParameters params = generateParams(medialibrary::SortingCriteria::Default, false, includeMissing, onlyFavorites);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromPlaylist(id, &params);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromPlaylist(id, queryChar);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchFromPlaylistCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromPlaylist(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jboolean
playlistAppend(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId, jlong mediaId)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->playlistAppend(playlistId, mediaId);
}

jboolean
playlistAppendGroup(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId, jlongArray mediaIds)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    bool result = true;
    jsize len = env->GetArrayLength(mediaIds);
    jlong *ids = env->GetLongArrayElements(mediaIds, 0);
    for (int i = 0; i < len; ++i)
        result &= aml->playlistAppend(playlistId, (int64_t) ids[i]);
    env->ReleaseLongArrayElements(mediaIds, ids, 0);
    return result;
}

jboolean
playlistAdd(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId, jlong mediaId, jint position)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->playlistAdd(playlistId, mediaId, position);
}

jboolean
playlistMove(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId, jint oldPosition, jint newPosition)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->playlistMove(playlistId, oldPosition, newPosition);
}

jboolean
playlistRemove(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId, jint position)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->playlistRemove(playlistId, position);
}

jboolean
playlistDelete(JNIEnv* env, jobject thiz, jobject medialibrary, jlong playlistId)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->PlaylistDelete(playlistId);
}

 /*
  * Folder methods
  */
jobjectArray
mediaFromFolder(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint type, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    auto folder = aml->folder(id);
    const auto query = aml->mediaFromFolder(folder.get(), (medialibrary::IMedia::Type)type, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jint
mediaFromFolderCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint type) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    auto folder = aml->folder(id);
    const auto query = aml->mediaFromFolder(folder.get(), (medialibrary::IMedia::Type)type);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchMediaFromFolder(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint mediaType, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromFolder(id, queryChar, (medialibrary::IMedia::Type)mediaType, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchMediaFromFolderCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint mediaType) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromFolder(id, queryChar, (medialibrary::IMedia::Type)mediaType);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
subFolders(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->subFolders(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::FolderPtr> foldersList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray foldersRefs = (jobjectArray) env->NewObjectArray(foldersList.size(), ml_fields.Folder.clazz, NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& folder : foldersList) {
        const auto query = aml->mediaFromFolder(folder.get(), medialibrary::IMedia::Type::Video);
        int count = (query != nullptr ? query->count() : 0);

        auto item = convertFolderObject(env, &ml_fields, folder, count);
        env->SetObjectArrayElement(foldersRefs, ++index, item.get());
    }
    return foldersRefs;
}

jint
subFoldersCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->subFolders(id);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
folders(JNIEnv* env, jobject thiz, jint type, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->folders(&params, (medialibrary::IMedia::Type) type);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Folder.clazz, NULL);
    std::vector<medialibrary::FolderPtr> foldersList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray foldersRefs = (jobjectArray) env->NewObjectArray(foldersList.size(), ml_fields.Folder.clazz, NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& folder : foldersList) {
        try
        {
            const auto query = aml->mediaFromFolder(folder.get(), (medialibrary::IMedia::Type)type);
            int count = (query != nullptr ? query->count() : 0);

            auto item = convertFolderObject(env, &ml_fields, folder, count);
            env->SetObjectArrayElement(foldersRefs, ++index, item.get());
        }
        catch( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Ignore this folder since it's on a removed device.
            auto item = convertFolderObject(env, &ml_fields, folder, 0);
            env->SetObjectArrayElement(foldersRefs, ++index, item.get());
        }
    }
    return foldersRefs;
}

jobject
getFolder(JNIEnv* env, jobject thiz, jint type, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::FolderPtr folder = aml->folder(id);
    if (folder == nullptr) return nullptr;
    const auto query = aml->mediaFromFolder(folder.get(), (medialibrary::IMedia::Type)type);
    int count = (query != nullptr ? query->count() : 0);
    return convertFolderObject(env, &ml_fields, folder, count).release();;
}


jint
foldersCount(JNIEnv* env, jobject thiz, jint type) {
    const auto query = MediaLibrary_getInstance(env, thiz)->folders(nullptr, (medialibrary::IMedia::Type)type);
    return (jint) (query != nullptr ? query->count() : 0);
}

/*
 * Video groups
 */

jobjectArray
videoGroups(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites, jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->videoGroups(&params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.VideoGroup.clazz, NULL);
    std::vector<medialibrary::MediaGroupPtr> groupsList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray groupsRefs = (jobjectArray) env->NewObjectArray(groupsList.size(), ml_fields.VideoGroup.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaGroupPtr const& group : groupsList) {
        try
        {
            auto item = convertVideoGroupObject(env, &ml_fields, group);
            env->SetObjectArrayElement(groupsRefs, ++index, item.get());
        }
        catch( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Ignore this VideoGroup since it's on a removed device.
        }
    }
    return groupsRefs;
}

jobjectArray
searchMediaGroups(JNIEnv* env, jobject thiz, jstring queryString, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(queryString, JNI_FALSE);
    const auto query = aml->searchVideoGroups(queryChar, &params);
    std::vector<medialibrary::MediaGroupPtr> groups = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray groupRefs = (jobjectArray) env->NewObjectArray(groups.size(), ml_fields.VideoGroup.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaGroupPtr const& group : groups) {
        auto item = convertVideoGroupObject(env, &ml_fields, group);
        env->SetObjectArrayElement(groupRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(queryString, queryChar);
    return groupRefs;
}


jobjectArray
searchFolders(JNIEnv* env, jobject thiz, jstring queryString, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(queryString, JNI_FALSE);
    const auto query = aml->searchFolders(queryChar, &params);
    std::vector<medialibrary::FolderPtr> folders = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray folderRefs = (jobjectArray) env->NewObjectArray(folders.size(), ml_fields.Folder.clazz, NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& folder : folders)
    {
        const auto query = aml->mediaFromFolder(folder.get(), medialibrary::IMedia::Type::Video);
        int count = (query != nullptr ? query->count() : 0);
        auto item = convertFolderObject(env, &ml_fields, folder, count);
        env->SetObjectArrayElement(folderRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(queryString, queryChar);
    return folderRefs;
}

jint
getSearchFoldersCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFolders(queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jint
videoGroupsCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    if (filterQuery == nullptr)
    {
        const auto query = MediaLibrary_getInstance(env, thiz)->videoGroups(nullptr);
        return (jint) (query != nullptr ? query->count() : 0);
    }
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, thiz)->searchVideoGroups(queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getPagedMediaFromvideoGroup(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const auto query = aml->mediaFromMediaGroup(id, &params);
    if (query == nullptr)
    {
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jobjectArray
searchFromVideoGroup(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites,  jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromMediaGroup(id, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchFromvideoGroupCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromMediaGroup(id, queryChar, nullptr);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return static_cast<jint> (query != nullptr ? query->count() : 0);
}

jboolean
groupAddId(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong mediaId)
{
    return MediaLibrary_getInstance(env, medialibrary)->groupAddId(id, mediaId);
}

jboolean
groupRemoveId(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jlong mediaId)
{
    return MediaLibrary_getInstance(env, medialibrary)->groupRemoveId(id, mediaId);
}

jstring
groupName(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    return vlcNewStringUTF(env, MediaLibrary_getInstance(env, medialibrary)->groupName(id).c_str()).release();
}

jboolean
groupRename(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring name)
{
    const char *nameChar = env->GetStringUTFChars(name, JNI_FALSE);
    bool result = MediaLibrary_getInstance(env, medialibrary)->groupRename(id, nameChar);
    env->ReleaseStringUTFChars(name, nameChar);
    return result;
}

jboolean
groupUserInteracted(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    return MediaLibrary_getInstance(env, medialibrary)->groupUserInteracted(id);
}

jlong groupDuration(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    return MediaLibrary_getInstance(env, medialibrary)->groupDuration(id);
}

jboolean
groupDestroy(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    return MediaLibrary_getInstance(env, medialibrary)->groupDestroy(id);
}

jobject
createMediaGroupByName(JNIEnv* env, jobject thiz, jstring name)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *name_cstr = env->GetStringUTFChars(name, JNI_FALSE);
    medialibrary::MediaGroupPtr group = aml->createMediaGroup(name_cstr);
    env->ReleaseStringUTFChars(name, name_cstr);
    if (!group)
        return nullptr;
    return convertVideoGroupObject(env, &ml_fields, group).release();
}

jobject
createMediaGroup(JNIEnv* env, jobject thiz, jlongArray mediaIds)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<int64_t> cids;
    jsize size = env->GetArrayLength(mediaIds);
    jlong *ids = env->GetLongArrayElements(mediaIds, 0);
    for (int i = 0; i < size; ++i)
        cids.push_back((int64_t) ids[i]);
    env->ReleaseLongArrayElements(mediaIds, ids, 0);
    medialibrary::MediaGroupPtr group = aml->createMediaGroup(cids);
    if (!group)
        return nullptr;
    return convertVideoGroupObject(env, &ml_fields, group).release();
}

jobject
getMediaGroup(JNIEnv* env, jobject thiz, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::MediaGroupPtr media = aml->videoGroup(id);
    if (media == nullptr) return nullptr;
    return convertVideoGroupObject(env, &ml_fields, media).release();;
}

jboolean regroupAll(JNIEnv* env, jobject thiz)
{
    return MediaLibrary_getInstance(env, thiz)->regroupAll();
}

jboolean
regroup(JNIEnv* env, jobject thiz, jlong id)
{
    return MediaLibrary_getInstance(env, thiz)->regroup((int64_t)id);
}

/*
 * Subscription Cache
 */

jboolean
fitsInSubscriptionCache(JNIEnv* env, jobject thiz, jobject medialibrary, jlong mediaId)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->fitsInSubscriptionCache(*aml->media(mediaId));
}

void
cacheNewSubscriptionMedia(JNIEnv* env, jobject thiz, jobject medialibrary)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    aml->cacheNewSubscriptionMedia();
}

jboolean
setSubscriptionMaxCachedMedia(JNIEnv* env, jobject thiz, jobject medialibrary, jint nbMedia)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->setSubscriptionMaxCachedMedia(nbMedia);
}

jboolean
setMlSubscriptionMaxCacheSize(JNIEnv* env, jobject thiz, jobject medialibrary, jlong size)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->setSubscriptionMaxCacheSize(size);
}

jboolean
setMaxCacheSize(JNIEnv* env, jobject thiz, jobject medialibrary, jlong size)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->setMaxCacheSize(size);
}

jint 
getSubscriptionMaxCachedMedia(JNIEnv* env, jobject thiz, jobject medialibrary)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->getSubscriptionMaxCachedMedia();
}

jlong 
getMlSubscriptionMaxCacheSize(JNIEnv* env, jobject thiz, jobject medialibrary)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->getSubscriptionMaxCacheSize();
}

jlong
getMaxCacheSize(JNIEnv* env, jobject thiz, jobject medialibrary)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    return aml->getMaxCacheSize();
}

jboolean 
refreshAllSubscriptions(JNIEnv* env, jobject thiz, jobject ml)
{
        AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    return aml->refreshAllSubscriptions();
}

/*
 * Services
 */ 

jobject
getService(JNIEnv* env, jobject thiz, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    jobject serviceObject = env->NewObject(ml_fields.Service.clazz,
            ml_fields.Service.initID, (jint) servicePtr->type());
    return serviceObject;
}

jboolean
addSubscription(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type, jstring mrl)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    return servicePtr->addSubscription(char_mrl);
}

jboolean
isAutoDownloadEnabled(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->isAutoDownloadEnabled();
}

jboolean
setAutoDownloadEnabled(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type, jboolean enabled)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->setAutoDownloadEnabled(enabled);
}

jboolean
isNewMediaNotificationEnabled(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->isNewMediaNotificationEnabled();
}

jboolean
setNewMediaNotificationEnabled(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type, jboolean enabled)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->setNewMediaNotificationEnabled(enabled);
}

jlong
getServiceMaxCacheSize(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return -2;
    return servicePtr->maxCacheSize();
}

jboolean
setServiceMaxCacheSize(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type, jlong size)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->setMaxCacheSize(size);
}

jint
getNbSubscriptions(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return -1;
    return servicePtr->nbSubscriptions();
}

jint
getNbUnplayedMedia(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return -1;
    return servicePtr->nbUnplayedMedia();
}

jobjectArray
getSubscriptions(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type,
jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) {
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::SubscriptionPtr> subsArray = servicePtr->subscriptions(&params)->all();
    jobjectArray subsRefs = (jobjectArray) env->NewObjectArray(subsArray.size(),
            ml_fields.Subscription.clazz, NULL);
    int index = -1;
    for (medialibrary::SubscriptionPtr const& sub : subsArray) {
        auto item = convertSubscriptionObject(env, &ml_fields, sub);
        env->SetObjectArrayElement(subsRefs, ++index, item.get());
    }
    return subsRefs;
}

jint
getNbMedia(JNIEnv *env, jobject thiz, jobject ml, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return -1;
    return servicePtr->nbMedia();
}

jobjectArray
getServiceMedia(JNIEnv *env, jobject thiz, jobject medialibrary, jint _type,
jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) {
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
    std::vector<medialibrary::MediaPtr> mediasArray = servicePtr->media(&params)->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediasArray.size(),
            ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for (medialibrary::MediaPtr const& media : mediasArray) {
        auto item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item.get());
    }
    return mediaRefs;
}

jboolean
serviceRefresh(JNIEnv* env, jobject thiz, jobject ml, jint _type)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    medialibrary::IService::Type type = (medialibrary::IService::Type)_type;
    medialibrary::ServicePtr servicePtr = aml->service(type);
    if (servicePtr == nullptr) return false;
    return servicePtr->refresh();
}

/*
 * Subscriptions
 */

jint
subscriptionNewMediaNotification(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return false;
    return subscriptionPtr->newMediaNotification();
}

jboolean
setSubscriptionNewMediaNotification(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id, jint value)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return false;
    return subscriptionPtr->setNewMediaNotification(value);
}

jlong
getSubscriptionCachedSize(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return -2;
    return subscriptionPtr->cachedSize();
}

jlong
getSubscriptionMaxCacheSize(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return -2;
    return subscriptionPtr->maxCacheSize();
}

jboolean
setSubscriptionMaxCacheSize(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id, jlong size)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return false;
    return subscriptionPtr->setMaxCacheSize(size);
}

jint
getSubscriptionNbUnplayedMedia(JNIEnv *env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return -1;
    return subscriptionPtr->nbUnplayedMedia();
}

jboolean
removeSubscription(JNIEnv* env, jobject thiz, jlong id)
{
   return MediaLibrary_getInstance(env, thiz)->removeSubscription((int64_t)id);
}

jobjectArray
getChildSubscriptions(JNIEnv* env, jobject thiz, jobject ml, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
   AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
   medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
   medialibrary::SubscriptionPtr subscription = aml->subscription(id);
   if (subscription == nullptr) return nullptr;
   std::vector<medialibrary::SubscriptionPtr> subsArray = subscription->childSubscriptions(&params)->all();
   jobjectArray subsRefs = (jobjectArray) env->NewObjectArray(subsArray.size(),
           ml_fields.Subscription.clazz, NULL);
   int index = -1;
   for (medialibrary::SubscriptionPtr const& sub : subsArray) {
       auto item = convertSubscriptionObject(env, &ml_fields, sub);
       env->SetObjectArrayElement(subsRefs, ++index, item.get());
   }
   return subsRefs;   
}

jobject
getParent(JNIEnv* env, jobject thiz, jobject ml, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return nullptr;
    medialibrary::SubscriptionPtr parent = subscriptionPtr->parent();
    return convertSubscriptionObject(env, &ml_fields, parent).get();
}

jboolean
refresh(JNIEnv* env, jobject thiz, jobject ml, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return false;
    return subscriptionPtr->refresh();
}

jobjectArray
getSubscriptionMedia(JNIEnv* env, jobject thiz, jobject ml, jlong id, jint sortingCriteria, jboolean desc, jboolean includeMissing, jboolean onlyFavorites)
{
   AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
   medialibrary::QueryParameters params = generateParams(sortingCriteria, desc, includeMissing, onlyFavorites);
   medialibrary::SubscriptionPtr subscription = aml->subscription(id);
   if (subscription == nullptr) return nullptr;
   std::vector<medialibrary::MediaPtr> mediaArray = subscription->media(&params)->all();
   jobjectArray subsRefs = (jobjectArray) env->NewObjectArray(mediaArray.size(),
           ml_fields.MediaWrapper.clazz, nullptr);
   int index = -1;
   for (medialibrary::MediaPtr const& media : mediaArray) {
       auto item = mediaToMediaWrapper(env, &ml_fields, media);
       env->SetObjectArrayElement(subsRefs, ++index, item.get());
   }
   return subsRefs;   
}

jint getSubscriptionNbMedia(JNIEnv* env, jobject thiz, jobject ml, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, ml);
    medialibrary::SubscriptionPtr subscriptionPtr = aml->subscription(id);
    if (subscriptionPtr == nullptr) return -1;
    return subscriptionPtr->nbMedia();
}


 /*
  * JNI stuff
  */
static JNINativeMethod methods[] = {
    {"nativeConstruct", "(Ljava/lang/String;Ljava/lang/String;)V", (void*)constructML },
    {"nativeInit", "(Ljava/lang/String;)I", (void*)init },
    {"nativeRelease", "()V", (void*)release },
    {"nativeClearDatabase", "(Z)Z", (void*)clearDatabase },
    {"nativeAddDevice", "(Ljava/lang/String;Ljava/lang/String;Z)V", (void*)addDevice },
    {"nativeIsDeviceKnown", "(Ljava/lang/String;Ljava/lang/String;Z)Z", (void*)isDeviceKnown },
    {"nativeDeleteRemovableDevices", "()Z", (void*)deleteRemovableDevices },
    {"nativeDevices", "()[Ljava/lang/String;", (void*)devices },
    {"nativeDiscover", "(Ljava/lang/String;)V", (void*)discover },
    {"nativeSetLibVLCInstance", "(J)V", (void*)setLibVLCInstance },
    {"nativeSetDiscoverNetworkEnabled", "(Z)Z", (void*)setDiscoverNetworkEnabled },
    {"nativeRemoveRoot", "(Ljava/lang/String;)V", (void*)removeRoot },
    {"nativeRoots", "()[Ljava/lang/String;", (void*)roots },
    {"nativeRemoveDevice", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)removeDevice },
    {"nativeBanFolder", "(Ljava/lang/String;)V", (void*)banFolder },
    {"nativeUnbanFolder", "(Ljava/lang/String;)V", (void*)unbanFolder },
    {"nativeBannedFolders", "()[Ljava/lang/String;", (void*)bannedFolders },
    {"nativeHistory", "(I)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)history },
    {"nativeAddToHistory", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)addToHistory },
    {"nativeClearHistory", "(I)Z", (void*)clearHistory },
    {"nativeGetVideos", "()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getVideos },
    {"nativeGetSortedVideos", "(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getSortedVideos },
    {"nativeGetSortedPagedVideos", "(IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedVideos },
    {"nativeGetRecentVideos", "()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getRecentVideos },
    {"nativeGetAudio", "()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getAudio },
    {"nativeGetSortedAudio", "(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getSortedAudio },
    {"nativeGetSortedPagedAudio", "(IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedAudio },
    {"nativeGetRecentAudio", "()[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getRecentAudio },
    {"nativeSearch", "(Ljava/lang/String;ZZ)Lorg/videolan/medialibrary/media/SearchAggregate;", (void*)search},
    {"nativeSearchMedia", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchMedia},
    {"nativeSearchPagedMedia", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchPagedMedia},
    {"nativeSearchPagedAudio", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchPagedAudio},
    {"nativeSearchPagedVideo", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchPagedVideo},
    {"nativeGetSearchVideoCount", "(Ljava/lang/String;)I", (void*)getSearchVideoCount },
    {"nativeGetSearchAudioCount", "(Ljava/lang/String;)I", (void*)getSearchAudioCount },
    {"nativeGetSearchMediaCount", "(Ljava/lang/String;)I", (void*)getSearchMediaCount },
    {"nativeSearchAlbum", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)searchAlbum },
    {"nativeSearchPagedAlbum", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)searchPagedAlbum },
    {"nativeGetAlbumSearchCount", "(Ljava/lang/String;)I", (void*)getAlbumSearchCount },
    {"nativeSearchArtist", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)searchArtist },
    {"nativeSearchPagedArtist", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)searchPagedArtist },
    {"nativeGetArtistsSearchCount", "(Ljava/lang/String;)I", (void*)getArtistsSearchCount },
    {"nativeSearchGenre", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/Genre;", (void*)searchGenre },
    {"nativeSearchPagedGenre", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Genre;", (void*)searchPagedGenre },
    {"nativeGetGenreSearchCount", "(Ljava/lang/String;)I", (void*)getGenreSearchCount },
    {"nativeSearchPlaylist", "(Ljava/lang/String;IZZ)[Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)searchPlaylist },
    {"nativeSearchPagedPlaylist", "(Ljava/lang/String;IIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)searchPagedPlaylist },
    {"nativeGetPlaylistSearchCount", "(Ljava/lang/String;)I", (void*)getPlaylistSearchCount },
    {"nativeGetMedia", "(J)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getMedia },
    {"nativeGetMediaFromMrl", "(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getMediaFromMrl },
    {"nativeAddMedia", "(Ljava/lang/String;J)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)addMedia },
    {"nativeRemoveExternalMedia", "(J)Z", (void*)removeExternalMedia },
    {"nativeFlushUserProvidedThumbnails", "()Z", (void*)flushUserProvidedThumbnails },
    {"nativeAddStream", "(Ljava/lang/String;Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)addStream },
    {"nativeGetVideoCount", "()I", (void*)getVideoCount },
    {"nativeGetAudioCount", "()I", (void*)getAudioCount },
    {"nativeGetAlbums", "(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getAlbums },
    {"nativeGetPagedAlbums", "(IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getPagedAlbums },
    {"nativeGetAlbumsCount", "()I", (void*)getAlbumsCount },
    {"nativeGetAlbum", "(J)Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getAlbum },
    {"nativeGetArtists", "(ZIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)getArtists },
    {"nativeGetPagedArtists", "(ZIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)getPagedArtists },
    {"nativeGetArtistsCount", "(Z)I", (void*)getArtistsCount },
    {"nativeGetArtist", "(J)Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)getArtist },
    {"nativeGetGenres", "(IZZZ)[Lorg/videolan/medialibrary/interfaces/media/Genre;", (void*)getGenres },
    {"nativeGetPagedGenres", "(IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Genre;", (void*)getPagedGenres },
    {"nativeGetGenresCount", "()I", (void*)getGenresCount },
    {"nativeGetGenre", "(J)Lorg/videolan/medialibrary/interfaces/media/Genre;", (void*)getGenre },
    {"nativeGetPlaylists", "(IIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)getPlaylists },
    {"nativeGetPagedPlaylists", "(IIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)getPagedPlaylists },
    {"nativeGetPlaylistsCount", "()I", (void*)getPlaylistsCount },
    {"nativeGetPlaylist", "(JZZ)Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)getPlaylist },
    {"nativeGetFolders", "(IIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Folder;", (void*)folders },
    {"nativeGetFoldersCount", "(I)I", (void*)foldersCount },
    {"nativeGetFolder", "(IJ)Lorg/videolan/medialibrary/interfaces/media/Folder;", (void*)getFolder },
    {"nativeSearchPagedFolders", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Folder;", (void*)searchFolders },
    {"nativeGetSearchFoldersCount", "(Ljava/lang/String;)I", (void*)getSearchFoldersCount },
    {"nativePauseBackgroundOperations", "()V", (void*)pauseBackgroundOperations },
    {"nativeResumeBackgroundOperations", "()V", (void*)resumeBackgroundOperations },
    {"nativeReload", "()V", (void*)reload },
    {"nativeReload", "(Ljava/lang/String;)V", (void*)reloadRoot },
    {"nativeForceParserRetry", "()V", (void*)forceParserRetry },
    {"nativeForceRescan", "()V", (void*)forceRescan },
    {"nativeSetLastTime", "(JJ)I", (void*)setLastTime },
    {"nativeSetLastPosition", "(JF)Z", (void*)setLastPosition },
    {"nativeSetMediaUpdatedCbFlag", "(I)V", (void*)setMediaUpdatedCbFlag },
    {"nativeSetMediaAddedCbFlag", "(I)V", (void*)setMediaAddedCbFlag },
    {"nativePlaylistCreate", "(Ljava/lang/String;ZZ)Lorg/videolan/medialibrary/interfaces/media/Playlist;", (void*)playlistCreate },
    {"nativeGetVideoGroups", "(IZZZII)[Lorg/videolan/medialibrary/interfaces/media/VideoGroup;", (void*)videoGroups },
    {"nativeGetVideoGroupsCount", "(Ljava/lang/String;)I", (void*)videoGroupsCount },
    {"nativeCreateGroupByName", "(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/VideoGroup;", (void*)createMediaGroupByName },
    {"nativeSearchPagedGroups", "(Ljava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/VideoGroup;", (void*)searchMediaGroups },
    {"nativeCreateGroup", "([J)Lorg/videolan/medialibrary/interfaces/media/VideoGroup;", (void*)createMediaGroup },
    {"nativeGetGroup", "(J)Lorg/videolan/medialibrary/interfaces/media/VideoGroup;", (void*)getMediaGroup },
    {"nativeRegroupAll", "()Z", (void*)regroupAll },
    {"nativeRegroup", "(J)Z", (void*)regroup },
    {"nativeGetService", "(I)Lorg/videolan/medialibrary/interfaces/media/MlService;", (void*)getService},
    {"nativeFitsInSubscriptionCache", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)fitsInSubscriptionCache},
    {"nativeCacheNewSubscriptionMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;)V", (void*)cacheNewSubscriptionMedia},
    {"nativeSetSubscriptionMaxCachedMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)Z", (void*)setSubscriptionMaxCachedMedia},
    {"nativeSetMlSubscriptionMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)setMlSubscriptionMaxCacheSize},
    {"nativeSetMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)setMaxCacheSize},
    {"nativeGetSubscriptionMaxCachedMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;)I", (void*)getSubscriptionMaxCachedMedia},
    {"nativeGetMlSubscriptionMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;)J", (void*)getMlSubscriptionMaxCacheSize},
    {"nativeGetMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;)J", (void*)getMaxCacheSize},
    {"nativeRefreshAllSubscriptions", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;)Z", (void*)refreshAllSubscriptions},
};

static JNINativeMethod media_methods[] = {
    {"nativeGetMediaLongMetadata", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)J", (void*)getMediaLongMetadata },
    {"nativeGetMediaStringMetadata", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)Ljava/lang/String;", (void*)getMediaStringMetadata },
    {"nativeSetMediaStringMetadata", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JILjava/lang/String;)V", (void*)setMediaStringMetadata },
    {"nativeSetMediaLongMetadata", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIJ)V", (void*)setMediaLongMetadata },
    {"nativeSetMediaThumbnail", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)V", (void*)setMediaThumbnail },
    {"nativeSetMediaPlayCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)setMediaPlayCount },
    {"nativeGetMediaPlayCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)J", (void*)getMediaPlayCount },
    {"nativeRemoveMediaThumbnail", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)removeMediaThumbnail },
    {"nativeSetMediaTitle", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)V", (void*)setMediaTitle },
    {"nativeRemoveFromHistory", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)removeMediaFromHistory },
    {"nativeRequestThumbnail", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIIIF)V", (void*)requestThumbnail },
    {"nativeGetBookmarks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)[Lorg/videolan/medialibrary/interfaces/media/Bookmark;", (void*)getBookmarks },
    {"nativeAddBookmark", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Lorg/videolan/medialibrary/interfaces/media/Bookmark;", (void*)addBookmark },
    {"nativeRemoveBookmark", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)removeBookmark },
    {"nativeRemoveAllBookmarks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)removeAllBookmarks },
    {"nativeMarkAsPlayed", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)markAsPlayed },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setMediaFavorite },
};

static JNINativeMethod bookmark_methods[] = {
    {"nativeSetName", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)Z", (void*)setBookmarkName },
    {"nativeSetDescription", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)Z", (void*)setBookmarkDescription },
    {"nativeSetNameAndDescription", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;Ljava/lang/String;)Z", (void*)setBookmarkNameAndDescription },
    {"nativeMove", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)bookmarkMove },
};
static JNINativeMethod album_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getTracksFromAlbum },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedTracksFromAlbum },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchFromAlbum },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchFromAlbumCount },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)getTracksFromAlbumCount },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setAlbumFavorite },
};

static JNINativeMethod artist_methods[] = {
    {"nativeGetMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getMediaFromArtist },
    {"nativeGetAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getAlbumsFromArtist },
    {"nativeGetPagedMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedMediaFromArtist },
    {"nativeGetPagedAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getPagedAlbumsFromArtist },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchFromArtist },
    {"nativeSearchAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)searchAlbumsFromArtist },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchFromArtistCount },
    {"nativeGetSearchAlbumCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchAlbumFromArtistCount },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setArtistFavorite },
};

static JNINativeMethod genre_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZIZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getMediaFromGenre },
    {"nativeGetAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getAlbumsFromGenre },
    {"nativeGetArtists", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)getArtistsFromGenre },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedMediaFromGenre },
    {"nativeGetPagedAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)getPagedAlbumsFromGenre },
    {"nativeGetPagedArtists", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Artist;", (void*)getPagedArtistsFromGenre },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchMediaFromGenre },
    {"nativeSearchAlbums", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/Album;", (void*)searchAlbumsFromGenre },
    {"nativeGetAlbumsCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)getGenreAlbumsCount },
    {"nativeGetArtistsCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)getGenreArtistsCount },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchMediaFromGenreCount },
    {"nativeGetSearchAlbumCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchAlbumsFromGenreCount },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setGenreFavorite },
};

static JNINativeMethod folder_methods[] = {
    {"nativeMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)mediaFromFolder },
    {"nativeSubfolders", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/Folder;", (void*)subFolders },
    {"nativeMediaCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)I", (void*)mediaFromFolderCount },
    {"nativeSubfoldersCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)I", (void*)subFoldersCount },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchMediaFromFolder },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;I)I", (void*)getSearchMediaFromFolderCount },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setFolderFavorite },
};

static JNINativeMethod videogroup_methods[] = {
    {"nativeMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedMediaFromvideoGroup },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchFromVideoGroup },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchFromvideoGroupCount },
    {"nativeGroupAddId", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)groupAddId },
    {"nativeGroupRemoveId", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)groupRemoveId },
    {"nativeGroupName", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Ljava/lang/String;", (void*)groupName },
    {"nativeGroupRename", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)Z", (void*)groupRename },
    {"nativeGroupUserInteracted", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)groupUserInteracted },
    {"nativeGroupDuration", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)J", (void*)groupDuration },
    {"nativeGroupDestroy", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)groupDestroy },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setVideoGroupFavorite },
};

static JNINativeMethod playlist_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getMediaFromPlaylist },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIIZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getPagedMediaFromPlaylist },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZZ)I", (void*)getPlaylistTracksCount },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;IZZZII)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)searchFromPlaylist },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JLjava/lang/String;)I", (void*)getSearchFromPlaylistCount },
    {"nativePlaylistAppend", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)playlistAppend },
    {"nativePlaylistAppendGroup", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J[J)Z", (void*)playlistAppendGroup },
    {"nativePlaylistAdd", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJI)Z", (void*)playlistAdd },
    {"nativePlaylistMove", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JII)Z", (void*)playlistMove },
    {"nativePlaylistRemove", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)Z", (void*)playlistRemove },
    {"nativePlaylistDelete", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)playlistDelete },
    {"nativeSetFavorite", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JZ)Z", (void*)setPlaylistFavorite },
};

static JNINativeMethod service_methods[] = {
    {"nativeAddSubscription", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;ILjava/lang/String;)Z", (void*)addSubscription},
    {"nativeIsAutoDownloadEnabled", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)Z", (void*)isAutoDownloadEnabled},
    {"nativeSetAutoDownloadEnabled", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;IZ)Z", (void*)setAutoDownloadEnabled},
    {"nativeIsNewMediaNotificationEnabled", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)Z", (void*)isNewMediaNotificationEnabled},
    {"nativeSetNewMediaNotificationEnabled", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;IZ)Z", (void*)setNewMediaNotificationEnabled},
    {"nativeGetServiceMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)J", (void*)getServiceMaxCacheSize},
    {"nativeSetServiceMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;IJ)Z", (void*)setServiceMaxCacheSize},
    {"nativeGetNbSubscriptions", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)I", (void*)getNbSubscriptions},
    {"nativeGetNbUnplayedMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)I", (void*)getNbUnplayedMedia},
    {"nativeGetSubscriptions", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;IIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Subscription;", (void*)getSubscriptions},
    {"nativeGetNbMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)I", (void*)getNbMedia},
    {"nativeGetServiceMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;IIZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getServiceMedia},
    {"nativeServiceRefresh", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;I)Z", (void*)serviceRefresh},
};

static JNINativeMethod subscription_methods[] = {
    {"nativeSubscriptionNewMediaNotification", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)subscriptionNewMediaNotification},
    {"nativeSetSubscriptionNewMediaNotification", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JI)Z", (void*)setSubscriptionNewMediaNotification},
    {"nativeGetSubscriptionCachedSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)J", (void*)getSubscriptionCachedSize},
    {"nativeGetSubscriptionMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)J", (void*)getSubscriptionMaxCacheSize},
    {"nativeSetSubscriptionMaxCacheSize", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JJ)Z", (void*)setSubscriptionMaxCacheSize},
    {"nativeGetSubscriptionNbUnplayedMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)getSubscriptionNbUnplayedMedia},
    {"nativeGetChildSubscriptions", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/Subscription;", (void*)getChildSubscriptions},
    {"nativeGetParent", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Lorg/videolan/medialibrary/interfaces/media/Subscription;", (void*)getParent},
    {"nativeSubscriptionRefresh", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)Z", (void*)refresh},
    {"nativeGetSubscriptionMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;JIZZZ)[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;", (void*)getSubscriptionMedia},
    {"nativeGetSubscriptionNbMedia", "(Lorg/videolan/medialibrary/interfaces/Medialibrary;J)I", (void*)getSubscriptionNbMedia},
};

/* This function is called when a thread attached to the Java VM is canceled or
 * exited */
static void jni_detach_thread(void *data)
{
    //JNIEnv *env = data;
    myVm->DetachCurrentThread();
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv* env = NULL;
    myVm = vm;

    if (vm->GetEnv((void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return -1;

#define GET_CLASS(clazz, str, b_globlal) do { \
    (clazz) = env->FindClass((str)); \
    if (!(clazz)) { \
    LOGE("FindClass(%s) failed", (str)); \
    return -1; \
} \
    if (b_globlal) { \
    (clazz) = (jclass) env->NewGlobalRef((clazz)); \
    if (!(clazz)) { \
    LOGE("NewGlobalRef(%s) failed", (str)); \
    return -1; \
} \
} \
} while (0)

#define GET_ID(get, id, clazz, str, args) do { \
    (id) = env->get((clazz), (str), (args)); \
    if (!(id)) { \
    LOGE(#get"(%s) failed", (str)); \
    return -1; \
} \
} while (0)

    jclass Version_clazz;
    jfieldID SDK_INT_fieldID;
    GET_CLASS(Version_clazz, "android/os/Build$VERSION", false);
    GET_ID(GetStaticFieldID, SDK_INT_fieldID, Version_clazz, "SDK_INT", "I");
    ml_fields.SDK_INT = env->GetStaticIntField(Version_clazz,
                                               SDK_INT_fieldID);

    GET_CLASS(ml_fields.IllegalStateException.clazz,
              "java/lang/IllegalStateException", true);
    GET_CLASS(ml_fields.IllegalArgumentException.clazz,
              "java/lang/IllegalArgumentException", true);

    GET_CLASS(ml_fields.MediaLibrary.clazz, CLASSPATHNAME, true);
    if (env->RegisterNatives(ml_fields.MediaLibrary.clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        LOGE("RegisterNatives failed for '%s'", CLASSPATHNAME);
        return -1;
    }

    GET_CLASS(ml_fields.Artist.clazz, "org/videolan/medialibrary/media/ArtistImpl", true);
    if (env->RegisterNatives(ml_fields.Artist.clazz, artist_methods, sizeof(artist_methods) / sizeof(artist_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/ArtistImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Artist.initID,
           ml_fields.Artist.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIZ)V");

    GET_CLASS(ml_fields.Album.clazz, "org/videolan/medialibrary/media/AlbumImpl", true);
    if (env->RegisterNatives(ml_fields.Album.clazz, album_methods, sizeof(album_methods) / sizeof(album_methods[0])) < 0) {
        LOGE("RegisterNatives failed for 'org/videolan/medialibrary/media/AlbumImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Album.initID,
           ml_fields.Album.clazz,
           "<init>", "(JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;JIIJZ)V");

    GET_CLASS(ml_fields.Genre.clazz, "org/videolan/medialibrary/media/GenreImpl", true);
    if (env->RegisterNatives(ml_fields.Genre.clazz, genre_methods, sizeof(genre_methods) / sizeof(genre_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/GenreImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Genre.initID,
           ml_fields.Genre.clazz,
           "<init>", "(JLjava/lang/String;IIZ)V");

    GET_CLASS(ml_fields.Playlist.clazz, "org/videolan/medialibrary/media/PlaylistImpl", true);
    if (env->RegisterNatives(ml_fields.Playlist.clazz, playlist_methods, sizeof(playlist_methods) / sizeof(playlist_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/PlaylistImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Playlist.initID,
           ml_fields.Playlist.clazz,
           "<init>", "(JLjava/lang/String;IJIIIIZ)V");

    GET_CLASS(ml_fields.Service.clazz, "org/videolan/medialibrary/media/MlServiceImpl", true);
    if (env->RegisterNatives(ml_fields.Service.clazz, service_methods, sizeof(service_methods) / sizeof(service_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/MlServiceImpl");
        return -1;
    }
    GET_ID(GetMethodID,
            ml_fields.Service.initID,
            ml_fields.Service.clazz,
            "<init>", "(I)V");

    GET_CLASS(ml_fields.Subscription.clazz, "org/videolan/medialibrary/media/SubscriptionImpl", true);
    if (env->RegisterNatives(ml_fields.Subscription.clazz, subscription_methods, sizeof(subscription_methods) / sizeof(subscription_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/SubscriptionImpl");
        return -1;
    }
    GET_ID(GetMethodID,
            ml_fields.Subscription.initID,
            ml_fields.Subscription.clazz,
            "<init>", "(JILjava/lang/String;J)V");

    GET_CLASS(ml_fields.MediaWrapper.clazz,
              "org/videolan/medialibrary/media/MediaWrapperImpl", true);
    if (env->RegisterNatives(ml_fields.MediaWrapper.clazz, media_methods, sizeof(media_methods) / sizeof(media_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/MediaWrapperImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.MediaWrapper.initID,
           ml_fields.MediaWrapper.clazz,
           "<init>", "(JLjava/lang/String;JFJILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;IIIIJJZZIZJ)V");

    GET_CLASS(ml_fields.HistoryItem.clazz,
              "org/videolan/medialibrary/media/HistoryItem", true);

    GET_ID(GetMethodID,
           ml_fields.HistoryItem.initID,
           ml_fields.HistoryItem.clazz,
           "<init>", "(Ljava/lang/String;Ljava/lang/String;JZ)V");

    GET_CLASS(ml_fields.SearchAggregate.clazz, "org/videolan/medialibrary/media/SearchAggregate", true);

    GET_ID(GetMethodID,
           ml_fields.SearchAggregate.initID,
           ml_fields.SearchAggregate.clazz,
           "<init>", "([Lorg/videolan/medialibrary/interfaces/media/Album;[Lorg/videolan/medialibrary/interfaces/media/Artist;[Lorg/videolan/medialibrary/interfaces/media/Genre;[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;[Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;[Lorg/videolan/medialibrary/interfaces/media/Playlist;)V");

    GET_CLASS(ml_fields.Folder.clazz, "org/videolan/medialibrary/media/FolderImpl", true);
    if (env->RegisterNatives(ml_fields.Folder.clazz, folder_methods, sizeof(folder_methods) / sizeof(folder_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/FolderImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Folder.initID,
           ml_fields.Folder.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;IZ)V");

    GET_CLASS(ml_fields.VideoGroup.clazz, "org/videolan/medialibrary/media/VideoGroupImpl", true);
    if (env->RegisterNatives(ml_fields.VideoGroup.clazz, videogroup_methods, sizeof(videogroup_methods) / sizeof(videogroup_methods[0])) < 0) {
        LOGE("RegisterNatives failed for 'org/videolan/medialibrary/media/VideoGroupImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.VideoGroup.initID,
           ml_fields.VideoGroup.clazz,
           "<init>", "(JLjava/lang/String;IIIZ)V");

    GET_CLASS(ml_fields.Bookmark.clazz, "org/videolan/medialibrary/media/BookmarkImpl", true);
    if (env->RegisterNatives(ml_fields.Bookmark.clazz, bookmark_methods, sizeof(bookmark_methods) / sizeof(bookmark_methods[0])) < 0) {
        LOGE("RegisterNatives failed for 'org/videolan/medialibrary/media/BookmarkImpl");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Bookmark.initID,
           ml_fields.Bookmark.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;JJ)V");

    GET_ID(GetFieldID,
           ml_fields.MediaLibrary.instanceID,
           ml_fields.MediaLibrary.clazz,
           "mInstanceID", "J");

    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaAddedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaAdded", "([Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaUpdatedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaUpdated", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaDeleted", "([J)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaConvertedToExternalId,
           ml_fields.MediaLibrary.clazz,
           "onMediaConvertedToExternal", "([J)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onArtistsAddedId,
           ml_fields.MediaLibrary.clazz,
           "onArtistsAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onAlbumsAddedId,
           ml_fields.MediaLibrary.clazz,
           "onAlbumsAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onPlaylistsAddedId,
           ml_fields.MediaLibrary.clazz,
           "onPlaylistsAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onGenresAddedId,
           ml_fields.MediaLibrary.clazz,
           "onGenresAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onArtistsDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onArtistsDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onAlbumsDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onAlbumsDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onGenresDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onGenresDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onPlaylistsDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onPlaylistsDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onArtistsModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onArtistsModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onAlbumsModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onAlbumsModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onGenresModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onGenresModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onHistoryChangedId,
           ml_fields.MediaLibrary.clazz,
           "onHistoryChanged", "(I)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaGroupAddedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaGroupAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaGroupModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaGroupModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaGroupDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaGroupDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onFoldersAddedId,
           ml_fields.MediaLibrary.clazz,
           "onFoldersAdded", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onFoldersModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onFoldersModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onFoldersDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onFoldersDeleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onPlaylistsModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onPlaylistsModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryStartedId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryStarted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryProgressId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryProgress", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryCompletedId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryCompleted", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryFailedId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryFailed", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onParsingStatsUpdatedId,
           ml_fields.MediaLibrary.clazz,
           "onParsingStatsUpdated", "(II)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onBackgroundTasksIdleChangedId,
           ml_fields.MediaLibrary.clazz,
           "onBackgroundTasksIdleChanged", "(Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onReloadStartedId,
           ml_fields.MediaLibrary.clazz,
           "onReloadStarted", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onReloadCompletedId,
           ml_fields.MediaLibrary.clazz,
           "onReloadCompleted", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onRootBannedId,
           ml_fields.MediaLibrary.clazz,
           "onRootBanned", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onRootUnbannedId,
           ml_fields.MediaLibrary.clazz,
           "onRootUnbanned", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onRootAddedId,
           ml_fields.MediaLibrary.clazz,
           "onRootAdded", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onRootRemovedId,
           ml_fields.MediaLibrary.clazz,
           "onRootRemoved", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaThumbnailReadyId,
           ml_fields.MediaLibrary.clazz,
           "onMediaThumbnailReady", "(Lorg/videolan/medialibrary/interfaces/media/MediaWrapper;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onUnhandledExceptionId,
           ml_fields.MediaLibrary.clazz,
           "onUnhandledException", "(Ljava/lang/String;Ljava/lang/String;Z)V");

#undef GET_CLASS
#undef GET_ID

    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;

    if (vm->GetEnv((void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return;

    env->DeleteGlobalRef(ml_fields.IllegalArgumentException.clazz);
    env->DeleteGlobalRef(ml_fields.IllegalStateException.clazz);
    env->DeleteGlobalRef(ml_fields.MediaLibrary.clazz);
    env->DeleteGlobalRef(ml_fields.MediaWrapper.clazz);
}


static inline void throw_IllegalStateException(JNIEnv *env, const char *p_error)
{
    env->ThrowNew(ml_fields.IllegalStateException.clazz, p_error);
}

static inline void throw_IllegalArgumentException(JNIEnv *env, const char *p_error)
{
    env->ThrowNew(ml_fields.IllegalArgumentException.clazz, p_error);
}

static AndroidMediaLibrary *
MediaLibrary_getInstanceInternal(JNIEnv *env, jobject thiz)
{
    return (AndroidMediaLibrary*)(intptr_t) env->GetLongField(thiz,
                                                              ml_fields.MediaLibrary.instanceID);
}

AndroidMediaLibrary *
MediaLibrary_getInstance(JNIEnv *env, jobject thiz)
{
    AndroidMediaLibrary *p_obj = MediaLibrary_getInstanceInternal(env, thiz);
    if (!p_obj)
        throw_IllegalStateException(env, "can't get AndroidMediaLibrary instance");
    return p_obj;
}


static void
MediaLibrary_setInstance(JNIEnv *env, jobject thiz, AndroidMediaLibrary *p_obj)
{
    env->SetLongField(thiz,
                      ml_fields.MediaLibrary.instanceID,
                      (jlong)(intptr_t)p_obj);
}
