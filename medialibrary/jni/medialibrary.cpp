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

#define CLASSPATHNAME "org/videolan/medialibrary/Medialibrary"

static inline void throw_IllegalStateException(JNIEnv *env, const char *p_error);
static inline void throw_IllegalArgumentException(JNIEnv *env, const char *p_error);
static AndroidMediaLibrary *MediaLibrary_getInstanceInternal(JNIEnv *env, jobject thiz);
AndroidMediaLibrary *MediaLibrary_getInstance(JNIEnv *env, jobject thiz);


static void
MediaLibrary_setInstance(JNIEnv *env, jobject thiz, AndroidMediaLibrary *p_obj);

jint
init(JNIEnv* env, jobject thiz, jstring dbPath, jstring thumbsPath)
{
    AndroidMediaLibrary *aml = new  AndroidMediaLibrary(myVm, &ml_fields, thiz);
    MediaLibrary_setInstance(env, thiz, aml);
    const char *db_utfchars = env->GetStringUTFChars(dbPath, JNI_FALSE);
    const char *thumbs_utfchars = env->GetStringUTFChars(thumbsPath, JNI_FALSE);
    medialibrary::InitializeResult initCode = aml->initML(db_utfchars, thumbs_utfchars);
    m_IsInitialized = initCode != medialibrary::InitializeResult::Failed;
    env->ReleaseStringUTFChars(dbPath, db_utfchars);
    env->ReleaseStringUTFChars(thumbsPath, thumbs_utfchars);
    return (int) initCode;
}

void
start(JNIEnv* env, jobject thiz)
{
    MediaLibrary_getInstance(env, thiz)->start();
}

void
release(JNIEnv* env, jobject thiz)
{
    LOGD("/!\\ release medialib. /!\\");
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    delete aml;
    MediaLibrary_setInstance(env, thiz, NULL);
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

jboolean
addDevice(JNIEnv* env, jobject thiz, jstring uuid, jstring storagePath, jboolean removable)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *uuidChar = env->GetStringUTFChars(uuid, JNI_FALSE);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    jboolean isNew = aml->addDevice(uuidChar, path, removable);
    env->ReleaseStringUTFChars(uuid, uuidChar);
    env->ReleaseStringUTFChars(storagePath, path);
    return isNew;
}

jobjectArray
devices(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    auto devices = aml->devices();
    jobjectArray deviceRefs = (jobjectArray) env->NewObjectArray(devices.size(), env->FindClass("java/lang/String"), NULL);
    int index = -1;
    for(auto device : devices) {
        jstring path = env->NewStringUTF(std::get<1>(device).c_str());
        env->SetObjectArrayElement(deviceRefs, ++index, path);
        env->DeleteLocalRef(path);
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

void
removeEntryPoint(JNIEnv* env, jobject thiz, jstring storagePath)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(storagePath, JNI_FALSE);
    aml->removeEntryPoint(path);
    env->ReleaseStringUTFChars(storagePath, path);
}

jobjectArray
entryPoints(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::FolderPtr> entryPoints = aml->entryPoints();
    entryPoints.erase(std::remove_if( begin( entryPoints ), end( entryPoints ), []( const medialibrary::FolderPtr f ) { return f->isPresent() == false; } ), end( entryPoints ));
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(entryPoints.size(), env->FindClass("java/lang/String"), NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& entrypoint : entryPoints) {
        jstring mrl = env->NewStringUTF(entrypoint->mrl().c_str());
        env->SetObjectArrayElement(mediaRefs, ++index, mrl);
        env->DeleteLocalRef(mrl);
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
reloadEntryPoint(JNIEnv* env, jobject thiz, jstring entryPoint)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *path = env->GetStringUTFChars(entryPoint, JNI_FALSE);
    aml->reload(path);
    env->ReleaseStringUTFChars(entryPoint, path);
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
increasePlayCount(JNIEnv* env, jobject thiz, jlong id)
{
    return MediaLibrary_getInstance(env, thiz)->increasePlayCount((int64_t)id);
}

void
removeMediaFromHistory(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    MediaLibrary_getInstance(env, medialibrary)->removeMediaFromHistory((int64_t)id);
}

jobjectArray
lastMediaPLayed(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::MediaPtr> mediaPlayed = aml->lastMediaPlayed();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaPlayed.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaPlayed) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, mediaRefs, ml_fields.MediaWrapper.clazz, drops);
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

jobjectArray
lastStreamsPlayed(JNIEnv* env, jobject thiz)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    std::vector<medialibrary::MediaPtr> streamsPlayed = aml->lastStreamsPlayed();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(streamsPlayed.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : streamsPlayed) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

bool clearHistory(JNIEnv* env, jobject thiz)
{
    return MediaLibrary_getInstance(env, thiz)->clearHistory();
}

static jobjectArray
getInternalVideos(JNIEnv* env, jobject thiz, const medialibrary::QueryParameters* params = nullptr, jint nbItems = 0,  jint offset = 0 )
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const auto query = aml->videoFiles(params);
    std::vector<medialibrary::MediaPtr> videoFiles = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray videoRefs = (jobjectArray) env->NewObjectArray(videoFiles.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : videoFiles) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(videoRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, videoRefs, ml_fields.MediaWrapper.clazz, drops);
}

jobjectArray
getVideos(JNIEnv* env, jobject thiz)
{
    return getInternalVideos(env, thiz);
}

jobjectArray
getSortedVideos(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc)
{
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    return getInternalVideos(env, thiz, &params );
}

jobjectArray
getPagedVideos(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    return getInternalVideos(env, thiz, &params, nbItems, offset );
}

jobjectArray
getRecentVideos(JNIEnv* env, jobject thiz)
{
    medialibrary::QueryParameters params {
        medialibrary::SortingCriteria::InsertionDate,
        true,
    };
    return getInternalVideos(env, thiz, &params);
}

static jobjectArray
getInternalAudio(JNIEnv* env, jobject thiz, const medialibrary::QueryParameters* params = nullptr, jint nbItems = 0,  jint offset = 0 )
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const auto query = aml->audioFiles(params);
    std::vector<medialibrary::MediaPtr> audioFiles = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray audioRefs = (jobjectArray) env->NewObjectArray(audioFiles.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : audioFiles) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(audioRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, audioRefs, ml_fields.MediaWrapper.clazz, drops);
}

jobjectArray
getAudio(JNIEnv* env, jobject thiz)
{
    return getInternalAudio(env, thiz);
}

jobjectArray
getRecentAudio(JNIEnv* env, jobject thiz)
{
    medialibrary::QueryParameters params {
        medialibrary::SortingCriteria::InsertionDate, true
    };
    return getInternalAudio(env, thiz, &params );
}

jobjectArray
getSortedAudio(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc)
{
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    return getInternalAudio(env, thiz, &params);
}

jobjectArray
getPagedAudio(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    return getInternalAudio(env, thiz, &params, nbItems, offset);
}

jobject
search(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    jobject searchResult = convertSearchAggregateObject(env, &ml_fields, aml->search(queryChar));
    env->ReleaseStringUTFChars(query, queryChar);
    return searchResult;
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
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return mediaList;
}

jobjectArray
searchPagedMedia(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchMedia(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaList;
}

jobjectArray
searchPagedAudio(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAudio(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaList;
}

jobjectArray
searchPagedVideo(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchVideo(queryChar, &params);
    const auto searchResult = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaList = (jobjectArray) env->NewObjectArray(searchResult.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : searchResult) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaList, ++index, item);
        env->DeleteLocalRef(item);
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
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return artistRefs;
}

jobjectArray
searchPagedArtist(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchArtists(queryChar, &params);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item);
        env->DeleteLocalRef(item);
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
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return albumRefs;
}

jobjectArray
searchPagedAlbum(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchAlbums(queryChar, &params);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item);
        env->DeleteLocalRef(item);
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
        jobject item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return genreRefs;
}

jobjectArray
searchPagedGenre(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchGenre(queryChar, &params);
    std::vector<medialibrary::GenrePtr> genres = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        jobject item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item);
        env->DeleteLocalRef(item);
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
searchPlaylist(JNIEnv* env, jobject thiz, jstring query)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *queryChar = env->GetStringUTFChars(query, JNI_FALSE);
    std::vector<medialibrary::PlaylistPtr> playlists = aml->searchPlaylists(queryChar)->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        jobject item = convertPlaylistObject(env, &ml_fields, playlist);
        env->SetObjectArrayElement(playlistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(query, queryChar);
    return playlistRefs;
}

jobjectArray
searchPagedPlaylist(JNIEnv* env, jobject thiz, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchPlaylists(queryChar, &params);
    std::vector<medialibrary::PlaylistPtr> playlists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        jobject item = convertPlaylistObject(env, &ml_fields, playlist);
        env->SetObjectArrayElement(playlistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return playlistRefs;
}

jint
getPlaylistSearchCount(JNIEnv* env, jobject thiz, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    jint count = (jint) MediaLibrary_getInstance(env, thiz)->searchPlaylists(queryChar)->count();
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
    return mediaToMediaWrapper(env, &ml_fields, aml->media(id));
}

jobject
getMediaFromMrl(JNIEnv* env, jobject thiz, jstring mrl) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    jobject mw = mediaToMediaWrapper(env, &ml_fields, aml->media(char_mrl));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    return mw;
}

jobject
addMedia(JNIEnv* env, jobject thiz, jstring mrl) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    jobject mw = mediaToMediaWrapper(env, &ml_fields, aml->addMedia(char_mrl));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    return mw;
}

jboolean
removeExternalMedia(JNIEnv* env, jobject thiz, jlong id) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    return aml->removeExternalMedia(id);
}

jobject
addStream(JNIEnv* env, jobject thiz, jstring mrl, jstring title) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *char_mrl = env->GetStringUTFChars(mrl, JNI_FALSE);
    const char *char_title = env->GetStringUTFChars(title, JNI_FALSE);
    jobject mw = mediaToMediaWrapper(env, &ml_fields, aml->addStream(char_mrl, char_title));
    env->ReleaseStringUTFChars(mrl, char_mrl);
    env->ReleaseStringUTFChars(title, char_title);
    return mw;
}

jobjectArray
getAlbums(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    std::vector<medialibrary::AlbumPtr> albums = aml->albums( &params )->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return albumRefs;
}

jobjectArray
getPagedAlbums(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->albums(&params);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item);
        env->DeleteLocalRef(item);
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
    return album != nullptr ? convertAlbumObject(env, &ml_fields, album) : nullptr;
}

jobjectArray
getArtists(JNIEnv* env, jobject thiz, jboolean all, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    std::vector<medialibrary::ArtistPtr> artists = aml->artists(all, &params)->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return artistRefs;
}

jobjectArray
getPagedArtists(JNIEnv* env, jobject thiz, jboolean all, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->artists(all, &params);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistRefs, ++index, item);
        env->DeleteLocalRef(item);
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
    return artist != nullptr ? convertArtistObject(env, &ml_fields, artist) : nullptr;
}

jobjectArray
getGenres(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    std::vector<medialibrary::GenrePtr> genres = aml->genres( &params )->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        jobject item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return genreRefs;
}

jobjectArray
getPagedGenres(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->genres(&params);
    std::vector<medialibrary::GenrePtr> genres = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray genreRefs = (jobjectArray) env->NewObjectArray(genres.size(), ml_fields.Genre.clazz, NULL);
    int index = -1;
    for(medialibrary::GenrePtr const& genre : genres) {
        jobject item = convertGenreObject(env, &ml_fields, genre);
        env->SetObjectArrayElement(genreRefs, ++index, item);
        env->DeleteLocalRef(item);
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
    return genre != nullptr ? convertGenreObject(env, &ml_fields, genre) : nullptr;
}

jobjectArray
getPlaylists(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    std::vector<medialibrary::PlaylistPtr> playlists = aml->playlists(&params)->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        jobject item = convertPlaylistObject(env, &ml_fields, playlist);
        env->SetObjectArrayElement(playlistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return playlistRefs;
}


jobjectArray
getPagedPlaylists(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->playlists(&params);
    std::vector<medialibrary::PlaylistPtr> playlists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray playlistRefs = (jobjectArray) env->NewObjectArray(playlists.size(), ml_fields.Playlist.clazz, NULL);
    int index = -1;
    for(medialibrary::PlaylistPtr const& playlist : playlists) {
        jobject item = convertPlaylistObject(env, &ml_fields, playlist);
        env->SetObjectArrayElement(playlistRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return playlistRefs;
}

jint
getPlaylistsCount(JNIEnv* env, jobject thiz) {
    return (jint) MediaLibrary_getInstance(env, thiz)->playlists(nullptr)->count();
}

jobject
getPlaylist(JNIEnv* env, jobject thiz, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::PlaylistPtr playlist = aml->playlist(id);
    return playlist != nullptr ? convertPlaylistObject(env, &ml_fields, playlist) : nullptr;
}

jobject
playlistCreate(JNIEnv* env, jobject thiz, jstring name)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    const char *name_cstr = env->GetStringUTFChars(name, JNI_FALSE);
    medialibrary::PlaylistPtr playlist = aml->PlaylistCreate(name_cstr);
    env->ReleaseStringUTFChars(name, name_cstr);
    return playlist != nullptr ? convertPlaylistObject(env, &ml_fields, playlist) : nullptr;
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
getTracksFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->tracksFromAlbum(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> tracks = query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(tracks.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    jobject item = nullptr;
    for(medialibrary::MediaPtr const& media : tracks) {
        item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr) ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, mediaRefs, ml_fields.MediaWrapper.clazz, drops);
}

jint
getTracksFromAlbumCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->tracksFromAlbum(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getPagedTracksFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->tracksFromAlbum(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

jobjectArray
searchFromAlbum(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
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
getMediaFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->mediaFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr) ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, mediaRefs, ml_fields.MediaWrapper.clazz, drops);
}

jobjectArray
getPagedMediaFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->mediaFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

jobjectArray
searchFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getArtistTracksCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromArtist(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jint
getSearchFromArtistCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromArtist(id, queryChar);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->albumsFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return albumsRefs;
}

jobjectArray
getPagedAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->albumsFromArtist(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return albumsRefs;
}

jobjectArray
searchAlbumsFromArtist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return albumsRefs;
}

jint
getArtistAlbumsCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->albumsFromArtist(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
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
getMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean withThumbnail, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->mediaFromGenre(id, withThumbnail, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, mediaRefs, ml_fields.MediaWrapper.clazz, drops);
}

jobjectArray
getPagedMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jboolean withThumbnail, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->mediaFromGenre(id, withThumbnail, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

jint
getGenreTracksCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromGenre(id, false, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchMediaFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
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
getAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->albumsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = query->all();
    jobjectArray albumRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return albumRefs;
}

jobjectArray
getPagedAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->albumsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Album.clazz, NULL);
    std::vector<medialibrary::AlbumPtr> albums = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray albumsRefs = (jobjectArray) env->NewObjectArray(albums.size(), ml_fields.Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : albums) {
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return albumsRefs;
}

jint
getGenreAlbumsCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->albumsFromGenre(id, nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchAlbumsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = convertAlbumObject(env, &ml_fields, album);
        env->SetObjectArrayElement(albumsRefs, ++index, item);
        env->DeleteLocalRef(item);
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
getArtistsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->artistsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Artist.clazz, NULL);
    std::vector<medialibrary::ArtistPtr> artists = query->all();
    jobjectArray artistsRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistsRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return artistsRefs;
}

jobjectArray
getPagedArtistsFromGenre(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->artistsFromGenre(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Artist.clazz, NULL);
    std::vector<medialibrary::ArtistPtr> artists = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray artistsRefs = (jobjectArray) env->NewObjectArray(artists.size(), ml_fields.Artist.clazz, NULL);
    int index = -1;
    for(medialibrary::ArtistPtr const& artist : artists) {
        jobject item = convertArtistObject(env, &ml_fields, artist);
        env->SetObjectArrayElement(artistsRefs, ++index, item);
        env->DeleteLocalRef(item);
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

jobject
getMediaStringMetadata(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint metadataType)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::MediaPtr media = aml->media(id);
    if (media == nullptr) return 0L;
    const medialibrary::IMetadata& metadata = media->metadata((medialibrary::IMedia::MetadataType)metadataType);
    return metadata.isSet() ? env->NewStringUTF(metadata.asStr().c_str()) : nullptr;
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
getMediaFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const auto query = aml->mediaFromPlaylist(id);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1, drops = 0;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        if (item == nullptr)
            ++drops;
        env->DeleteLocalRef(item);
    }
    return filteredArray(env, mediaRefs, ml_fields.MediaWrapper.clazz, drops);
}

jobjectArray
getPagedMediaFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    const auto query = aml->mediaFromPlaylist(id);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

jint
getPlaylistTracksCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromPlaylist(id);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchFromPlaylist(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromPLaylist(id, queryChar);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    return mediaRefs;
}

jint
getSearchFromPlaylistCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromPLaylist(id, queryChar);
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
mediaFromFolder(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint type, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->mediaFromFolder(id, (medialibrary::IMedia::Type)type, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return mediaRefs;
}

jint
mediaFromFolderCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint type) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromFolder(id, (medialibrary::IMedia::Type)type);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
searchMediaFromFolder(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jstring filterQuery, jint mediaType, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
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
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
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
subFolders(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->subFolders(id, &params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    std::vector<medialibrary::FolderPtr> foldersList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray foldersRefs = (jobjectArray) env->NewObjectArray(foldersList.size(), ml_fields.Folder.clazz, NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& folder : foldersList) {
        jobject item = convertFolderObject(env, &ml_fields, folder);
        env->SetObjectArrayElement(foldersRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    return foldersRefs;
}

jint
subFoldersCount(JNIEnv* env, jobject thiz, jobject medialibrary, jlong id) {
    const auto query = MediaLibrary_getInstance(env, medialibrary)->subFolders(id);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
folders(JNIEnv* env, jobject thiz, jint type, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->folders(&params, (medialibrary::IMedia::Type) type);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.Folder.clazz, NULL);
    std::vector<medialibrary::FolderPtr> foldersList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray foldersRefs = (jobjectArray) env->NewObjectArray(foldersList.size(), ml_fields.Folder.clazz, NULL);
    int index = -1;
    for(medialibrary::FolderPtr const& folder : foldersList) {
        try
        {
            jobject item = convertFolderObject(env, &ml_fields, folder);
            env->SetObjectArrayElement(foldersRefs, ++index, item);
            env->DeleteLocalRef(item);
        }
        catch( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Ignore this folder since it's on a removed device.
        }
    }
    return foldersRefs;
}

jint
foldersCount(JNIEnv* env, jobject thiz, jint type) {
    const auto query = MediaLibrary_getInstance(env, thiz)->folders(nullptr, (medialibrary::IMedia::Type)type);
    return (jint) (query != nullptr ? query->count() : 0);
}

/*
 * Video groups
 */

void
setVideoGroupsPrefixLength( JNIEnv* env, jobject thiz, jint prefixLength )
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    aml->setVideoGroupsPrefixLength(static_cast<uint32_t>(prefixLength));
}

jobjectArray
videoGroups(JNIEnv* env, jobject thiz, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset ) {
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, thiz);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const auto query = aml->videoGroups(&params);
    if (query == nullptr) return (jobjectArray) env->NewObjectArray(0, ml_fields.VideoGroup.clazz, NULL);
    std::vector<medialibrary::VideoGroupPtr> groupsList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray groupsRefs = (jobjectArray) env->NewObjectArray(groupsList.size(), ml_fields.VideoGroup.clazz, NULL);
    int index = -1;
    for(medialibrary::VideoGroupPtr const& group : groupsList) {
        try
        {
            jobject item = convertVideoGroupObject(env, &ml_fields, group);
            env->SetObjectArrayElement(groupsRefs, ++index, item);
            env->DeleteLocalRef(item);
        }
        catch( const medialibrary::fs::errors::DeviceRemoved& )
        {
            // Ignore this VideoGroup since it's on a removed device.
        }
    }
    return groupsRefs;
}

jint
videoGroupsCount(JNIEnv* env, jobject thiz) {
    const auto query = MediaLibrary_getInstance(env, thiz)->videoGroups(nullptr);
    return (jint) (query != nullptr ? query->count() : 0);
}

jobjectArray
getPagedMediaFromvideoGroup(JNIEnv* env, jobject thiz, jobject medialibrary, jstring name, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const auto query = aml->mediaFromVideoGroup(char_name, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(name, char_name);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(name, char_name);
    return mediaRefs;
}

jint
getvideoGroupMediaCount(JNIEnv* env, jobject thiz, jobject medialibrary, jstring name) {
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->mediaFromVideoGroup(char_name, nullptr);
    env->ReleaseStringUTFChars(name, char_name);
    return static_cast<jint> (query != nullptr ? query->count() : 0);
}

jobjectArray
searchFromvideoGroup(JNIEnv* env, jobject thiz, jobject medialibrary, jstring name, jstring filterQuery, jint sortingCriteria, jboolean desc, jint nbItems,  jint offset)
{
    AndroidMediaLibrary *aml = MediaLibrary_getInstance(env, medialibrary);
    medialibrary::QueryParameters params {
        static_cast<medialibrary::SortingCriteria>(sortingCriteria),
        static_cast<bool>( desc )
    };
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const auto query = aml->searchFromVideoGroup(char_name, queryChar, &params);
    if (query == nullptr)
    {
        env->ReleaseStringUTFChars(filterQuery, queryChar);
        env->ReleaseStringUTFChars(name, char_name);
        return (jobjectArray) env->NewObjectArray(0, ml_fields.MediaWrapper.clazz, NULL);
    }
    std::vector<medialibrary::MediaPtr> mediaList = nbItems != 0 ? query->items(nbItems, offset) : query->all();
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(mediaList.size(), ml_fields.MediaWrapper.clazz, NULL);
    int index = -1;
    for(medialibrary::MediaPtr const& media : mediaList) {
        jobject item = mediaToMediaWrapper(env, &ml_fields, media);
        env->SetObjectArrayElement(mediaRefs, ++index, item);
        env->DeleteLocalRef(item);
    }
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    env->ReleaseStringUTFChars(name, char_name);
    return mediaRefs;
}

jint
getSearchFromvideoGroupCount(JNIEnv* env, jobject thiz, jobject medialibrary, jstring name, jstring filterQuery) {
    const char *queryChar = env->GetStringUTFChars(filterQuery, JNI_FALSE);
    const char *char_name = env->GetStringUTFChars(name, JNI_FALSE);
    const auto query = MediaLibrary_getInstance(env, medialibrary)->searchFromVideoGroup(char_name, queryChar, nullptr);
    env->ReleaseStringUTFChars(filterQuery, queryChar);
    env->ReleaseStringUTFChars(name, char_name);
    return static_cast<jint> (query != nullptr ? query->count() : 0);
}
 /*
  * JNI stuff
  */
static JNINativeMethod methods[] = {
    {"nativeInit", "(Ljava/lang/String;Ljava/lang/String;)I", (void*)init },
    {"nativeStart", "()V", (void*)start },
    {"nativeRelease", "()V", (void*)release },
    {"nativeClearDatabase", "(Z)V", (void*)clearDatabase },
    {"nativeAddDevice", "(Ljava/lang/String;Ljava/lang/String;Z)Z", (void*)addDevice },
    {"nativeDevices", "()[Ljava/lang/String;", (void*)devices },
    {"nativeDiscover", "(Ljava/lang/String;)V", (void*)discover },
    {"nativeRemoveEntryPoint", "(Ljava/lang/String;)V", (void*)removeEntryPoint },
    {"nativeEntryPoints", "()[Ljava/lang/String;", (void*)entryPoints },
    {"nativeRemoveDevice", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)removeDevice },
    {"nativeBanFolder", "(Ljava/lang/String;)V", (void*)banFolder },
    {"nativeUnbanFolder", "(Ljava/lang/String;)V", (void*)unbanFolder },
    {"nativeLastMediaPlayed", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)lastMediaPLayed },
    {"nativeLastStreamsPlayed", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)lastStreamsPlayed },
    {"nativeAddToHistory", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)addToHistory },
    {"nativeClearHistory", "()Z", (void*)clearHistory },
    {"nativeSetVideoGroupsPrefixLength", "(I)V", (void*)setVideoGroupsPrefixLength },
    {"nativeGetVideos", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getVideos },
    {"nativeGetSortedVideos", "(IZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getSortedVideos },
    {"nativeGetSortedPagedVideos", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedVideos },
    {"nativeGetRecentVideos", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getRecentVideos },
    {"nativeGetAudio", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getAudio },
    {"nativeGetSortedAudio", "(IZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getSortedAudio },
    {"nativeGetSortedPagedAudio", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedAudio },
    {"nativeGetRecentAudio", "()[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getRecentAudio },
    {"nativeSearch", "(Ljava/lang/String;)Lorg/videolan/medialibrary/media/SearchAggregate;", (void*)search},
    {"nativeSearchMedia", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchMedia},
    {"nativeSearchPagedMedia", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchPagedMedia},
    {"nativeSearchPagedAudio", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchPagedAudio},
    {"nativeSearchPagedVideo", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchPagedVideo},
    {"nativeGetSearchVideoCount", "(Ljava/lang/String;)I", (void*)getSearchVideoCount },
    {"nativeGetSearchAudioCount", "(Ljava/lang/String;)I", (void*)getSearchAudioCount },
    {"nativeGetSearchMediaCount", "(Ljava/lang/String;)I", (void*)getSearchMediaCount },
    {"nativeSearchAlbum", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)searchAlbum },
    {"nativeSearchPagedAlbum", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)searchPagedAlbum },
    {"nativeGetAlbumSearchCount", "(Ljava/lang/String;)I", (void*)getAlbumSearchCount },
    {"nativeSearchArtist", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)searchArtist },
    {"nativeSearchPagedArtist", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)searchPagedArtist },
    {"nativeGetArtistsSearchCount", "(Ljava/lang/String;)I", (void*)getArtistsSearchCount },
    {"nativeSearchGenre", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;", (void*)searchGenre },
    {"nativeSearchPagedGenre", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;", (void*)searchPagedGenre },
    {"nativeGetGenreSearchCount", "(Ljava/lang/String;)I", (void*)getGenreSearchCount },
    {"nativeSearchPlaylist", "(Ljava/lang/String;)[Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)searchPlaylist },
    {"nativeSearchPagedPlaylist", "(Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)searchPagedPlaylist },
    {"nativeGetPlaylistSearchCount", "(Ljava/lang/String;)I", (void*)getPlaylistSearchCount },
    {"nativeGetMedia", "(J)Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getMedia },
    {"nativeGetMediaFromMrl", "(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getMediaFromMrl },
    {"nativeAddMedia", "(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)addMedia },
    {"nativeRemoveExternalMedia", "(J)Z", (void*)removeExternalMedia },
    {"nativeAddStream", "(Ljava/lang/String;Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)addStream },
    {"nativeGetVideoCount", "()I", (void*)getVideoCount },
    {"nativeGetAudioCount", "()I", (void*)getAudioCount },
    {"nativeGetAlbums", "(IZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getAlbums },
    {"nativeGetPagedAlbums", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getPagedAlbums },
    {"nativeGetAlbumsCount", "()I", (void*)getAlbumsCount },
    {"nativeGetAlbum", "(J)Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getAlbum },
    {"nativeGetArtists", "(ZIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)getArtists },
    {"nativeGetPagedArtists", "(ZIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)getPagedArtists },
    {"nativeGetArtistsCount", "(Z)I", (void*)getArtistsCount },
    {"nativeGetArtist", "(J)Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)getArtist },
    {"nativeGetGenres", "(IZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;", (void*)getGenres },
    {"nativeGetPagedGenres", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;", (void*)getPagedGenres },
    {"nativeGetGenresCount", "()I", (void*)getGenresCount },
    {"nativeGetGenre", "(J)Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;", (void*)getGenre },
    {"nativeGetPlaylists", "(IZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)getPlaylists },
    {"nativeGetPagedPlaylists", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)getPagedPlaylists },
    {"nativeGetPlaylistsCount", "()I", (void*)getPlaylistsCount },
    {"nativeGetPlaylist", "(J)Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)getPlaylist },
    {"nativeGetFolders", "(IIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractFolder;", (void*)folders },
    {"nativeGetFoldersCount", "(I)I", (void*)foldersCount },
    {"nativePauseBackgroundOperations", "()V", (void*)pauseBackgroundOperations },
    {"nativeResumeBackgroundOperations", "()V", (void*)resumeBackgroundOperations },
    {"nativeReload", "()V", (void*)reload },
    {"nativeReload", "(Ljava/lang/String;)V", (void*)reloadEntryPoint },
    {"nativeForceParserRetry", "()V", (void*)forceParserRetry },
    {"nativeForceRescan", "()V", (void*)forceRescan },
    {"nativeIncreasePlayCount", "(J)Z", (void*)increasePlayCount },
    {"nativeSetMediaUpdatedCbFlag", "(I)V", (void*)setMediaUpdatedCbFlag },
    {"nativeSetMediaAddedCbFlag", "(I)V", (void*)setMediaAddedCbFlag },
    {"nativePlaylistCreate", "(Ljava/lang/String;)Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;", (void*)playlistCreate },
    {"nativeGetVideoGroups", "(IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractVideoGroup;", (void*)videoGroups },
    {"nativeGetVideoGroupsCount", "()I", (void*)videoGroupsCount },

};

static JNINativeMethod media_methods[] = {
    {"nativeGetMediaLongMetadata", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)J", (void*)getMediaLongMetadata },
    {"nativeGetMediaStringMetadata", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)Ljava/lang/String;", (void*)getMediaStringMetadata },
    {"nativeSetMediaStringMetadata", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JILjava/lang/String;)V", (void*)setMediaStringMetadata },
    {"nativeSetMediaLongMetadata", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIJ)V", (void*)setMediaLongMetadata },
    {"nativeSetMediaThumbnail", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)V", (void*)setMediaThumbnail },
    {"nativeSetMediaTitle", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)V", (void*)setMediaTitle },
    {"nativeRemoveFromHistory", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)V", (void*)removeMediaFromHistory },
    {"nativeRequestThumbnail", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIIIF)V", (void*)requestThumbnail },
};

static JNINativeMethod album_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getTracksFromAlbum },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedTracksFromAlbum },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchFromAlbum },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchFromAlbumCount },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getTracksFromAlbumCount },
};

static JNINativeMethod artist_methods[] = {
    {"nativeGetMedia", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getMediaFromArtist },
    {"nativeGetAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getAlbumsFromArtist },
    {"nativeGetPagedMedia", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedMediaFromArtist },
    {"nativeGetPagedAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getPagedAlbumsFromArtist },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchFromArtist },
    {"nativeSearchAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)searchAlbumsFromArtist },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getArtistTracksCount },
    {"nativeGetAlbumsCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getArtistAlbumsCount },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchFromArtistCount },
    {"nativeGetSearchAlbumCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchAlbumFromArtistCount },
};

static JNINativeMethod genre_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JZIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getMediaFromGenre },
    {"nativeGetAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getAlbumsFromGenre },
    {"nativeGetArtists", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZ)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)getArtistsFromGenre },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JZIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedMediaFromGenre },
    {"nativeGetPagedAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)getPagedAlbumsFromGenre },
    {"nativeGetPagedArtists", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;", (void*)getPagedArtistsFromGenre },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchMediaFromGenre },
    {"nativeSearchAlbums", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;", (void*)searchAlbumsFromGenre },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getGenreTracksCount },
    {"nativeGetAlbumsCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getGenreAlbumsCount },
    {"nativeGetArtistsCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getGenreArtistsCount },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchMediaFromGenreCount },
    {"nativeGetSearchAlbumCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchAlbumsFromGenreCount },
};

static JNINativeMethod folder_methods[] = {
    {"nativeMedia", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)mediaFromFolder },
    {"nativeSubfolders", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractFolder;", (void*)subFolders },
    {"nativeMediaCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)I", (void*)mediaFromFolderCount },
    {"nativeSubfoldersCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)I", (void*)subFoldersCount },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IIZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchMediaFromFolder },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;I)I", (void*)getSearchMediaFromFolderCount },
};

static JNINativeMethod videogroup_methods[] = {
    {"nativeMedia", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedMediaFromvideoGroup },
//    {"nativeMediaCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)I", (void*)getvideoGroupMediaCount },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;Ljava/lang/String;Ljava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchFromvideoGroup },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;Ljava/lang/String;Ljava/lang/String;)I", (void*)getSearchFromvideoGroupCount },
};

static JNINativeMethod playlist_methods[] = {
    {"nativeGetTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getMediaFromPlaylist },
    {"nativeGetPagedTracks", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)getPagedMediaFromPlaylist },
    {"nativeGetTracksCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)I", (void*)getPlaylistTracksCount },
    {"nativeSearch", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;IZII)[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;", (void*)searchFromPlaylist },
    {"nativeGetSearchCount", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JLjava/lang/String;)I", (void*)getSearchFromPlaylistCount },
    {"nativePlaylistAppend", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JJ)Z", (void*)playlistAppend },
    {"nativePlaylistAppendGroup", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J[J)Z", (void*)playlistAppendGroup },
    {"nativePlaylistAdd", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JJI)Z", (void*)playlistAdd },
    {"nativePlaylistMove", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JII)Z", (void*)playlistMove },
    {"nativePlaylistRemove", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;JI)Z", (void*)playlistRemove },
    {"nativePlaylistDelete", "(Lorg/videolan/medialibrary/interfaces/AbstractMedialibrary;J)Z", (void*)playlistDelete },
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

    GET_CLASS(ml_fields.Artist.clazz, "org/videolan/medialibrary/media/Artist", true);
    if (env->RegisterNatives(ml_fields.Artist.clazz, artist_methods, sizeof(artist_methods) / sizeof(artist_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/Artist");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Artist.initID,
           ml_fields.Artist.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

    GET_CLASS(ml_fields.Album.clazz, "org/videolan/medialibrary/media/Album", true);
    if (env->RegisterNatives(ml_fields.Album.clazz, album_methods, sizeof(album_methods) / sizeof(album_methods[0])) < 0) {
        LOGE("RegisterNatives failed for 'org/videolan/medialibrary/media/Album");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Album.initID,
           ml_fields.Album.clazz,
           "<init>", "(JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;JII)V");

    GET_CLASS(ml_fields.Folder.clazz, "org/videolan/medialibrary/media/Folder", true);
    if (env->RegisterNatives(ml_fields.Folder.clazz, folder_methods, sizeof(folder_methods) / sizeof(folder_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/Folder");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Folder.initID,
           ml_fields.Folder.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;)V");

    GET_CLASS(ml_fields.Genre.clazz, "org/videolan/medialibrary/media/Genre", true);
    if (env->RegisterNatives(ml_fields.Genre.clazz, genre_methods, sizeof(genre_methods) / sizeof(genre_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/Genre");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Genre.initID,
           ml_fields.Genre.clazz,
           "<init>", "(JLjava/lang/String;)V");

    GET_CLASS(ml_fields.Playlist.clazz, "org/videolan/medialibrary/media/Playlist", true);
    if (env->RegisterNatives(ml_fields.Playlist.clazz, playlist_methods, sizeof(playlist_methods) / sizeof(playlist_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/Playlist");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Playlist.initID,
           ml_fields.Playlist.clazz,
           "<init>", "(JLjava/lang/String;I)V");


    GET_CLASS(ml_fields.MediaWrapper.clazz,
              "org/videolan/medialibrary/media/MediaWrapper", true);
    if (env->RegisterNatives(ml_fields.MediaWrapper.clazz, media_methods, sizeof(media_methods) / sizeof(media_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/MediaWrapper");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.MediaWrapper.initID,
           ml_fields.MediaWrapper.clazz,
           "<init>", "(JLjava/lang/String;JJILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;IIIIJJZI)V");

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
           "<init>", "([Lorg/videolan/medialibrary/interfaces/media/AbstractAlbum;[Lorg/videolan/medialibrary/interfaces/media/AbstractArtist;[Lorg/videolan/medialibrary/interfaces/media/AbstractGenre;[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;[Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;[Lorg/videolan/medialibrary/interfaces/media/AbstractPlaylist;)V");

    GET_CLASS(ml_fields.Folder.clazz, "org/videolan/medialibrary/media/Folder", true);
    if (env->RegisterNatives(ml_fields.Folder.clazz, folder_methods, sizeof(folder_methods) / sizeof(folder_methods[0])) < 0) {
        LOGE("RegisterNatives failed for org/videolan/medialibrary/media/Folder");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.Folder.initID,
           ml_fields.Folder.clazz,
           "<init>", "(JLjava/lang/String;Ljava/lang/String;)V");

    GET_CLASS(ml_fields.VideoGroup.clazz, "org/videolan/medialibrary/media/VideoGroup", true);
    if (env->RegisterNatives(ml_fields.VideoGroup.clazz, videogroup_methods, sizeof(videogroup_methods) / sizeof(videogroup_methods[0])) < 0) {
        LOGE("RegisterNatives failed for 'org/videolan/medialibrary/media/VideoGroup");
        return -1;
    }
    GET_ID(GetMethodID,
           ml_fields.VideoGroup.initID,
           ml_fields.VideoGroup.clazz,
           "<init>", "(Ljava/lang/String;I)V");

    GET_ID(GetFieldID,
           ml_fields.MediaLibrary.instanceID,
           ml_fields.MediaLibrary.clazz,
           "mInstanceID", "J");

    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaAddedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaAdded", "([Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaUpdatedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaUpdated", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaDeletedId,
           ml_fields.MediaLibrary.clazz,
           "onMediaDeleted", "()V");
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
           ml_fields.MediaLibrary.onPlaylistsModifiedId,
           ml_fields.MediaLibrary.clazz,
           "onPlaylistsModified", "()V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryStartedId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryStarted", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryProgressId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryProgress", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onDiscoveryCompletedId,
           ml_fields.MediaLibrary.clazz,
           "onDiscoveryCompleted", "(Ljava/lang/String;)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onParsingStatsUpdatedId,
           ml_fields.MediaLibrary.clazz,
           "onParsingStatsUpdated", "(I)V");
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
           ml_fields.MediaLibrary.onEntryPointBannedId,
           ml_fields.MediaLibrary.clazz,
           "onEntryPointBanned", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onEntryPointUnbannedId,
           ml_fields.MediaLibrary.clazz,
           "onEntryPointUnbanned", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onEntryPointAddedId,
           ml_fields.MediaLibrary.clazz,
           "onEntryPointAdded", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onEntryPointRemovedId,
           ml_fields.MediaLibrary.clazz,
           "onEntryPointRemoved", "(Ljava/lang/String;Z)V");
    GET_ID(GetMethodID,
           ml_fields.MediaLibrary.onMediaThumbnailReadyId,
           ml_fields.MediaLibrary.clazz,
           "onMediaThumbnailReady", "(Lorg/videolan/medialibrary/interfaces/media/AbstractMediaWrapper;Z)V");
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
