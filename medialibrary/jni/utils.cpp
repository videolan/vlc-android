
#include "utils.h"
#define LOG_TAG "VLC/JNI/Utils"
#include "log.h"

jobject
mediaToMediaWrapper(JNIEnv* env, fields *fields, medialibrary::MediaPtr const& mediaPtr)
{
    if (mediaPtr == nullptr)
        return nullptr;
    const std::vector<medialibrary::FilePtr> files = mediaPtr->files();
    if (files.empty())
        return nullptr;
    //TODO get track, audio & spu track numbers
    jint type;
    switch (mediaPtr->type()) {
    case medialibrary::IMedia::Type::Audio:
        type = 1; //MediaWrapper.TYPE_AUDIO
        break;
    case medialibrary::IMedia::Type::Video:
        type = 0; //MediaWrapper.TYPE_VIDEO
        break;
    default:
        type = -1; //MediaWrapper.TYPE_ALL
        break;
    }
    medialibrary::AlbumTrackPtr p_albumTrack = mediaPtr->albumTrack();
    jstring artist = NULL, genre = NULL, album = NULL, albumArtist = NULL, mrl = NULL, title = NULL, thumbnail = NULL, filename = NULL;
    jint trackNumber = 0, discNumber = 0;

    const bool isPresent = mediaPtr->isPresent();
    if (p_albumTrack)
    {
        medialibrary::ArtistPtr artistPtr = p_albumTrack->artist();
        medialibrary::GenrePtr genrePtr = p_albumTrack->genre();
        medialibrary::AlbumPtr albumPtr = p_albumTrack->album();
        if (artistPtr != NULL)
            artist = vlcNewStringUTF(env, artistPtr->name().c_str());
        if (genrePtr != NULL)
            genre = vlcNewStringUTF(env, genrePtr->name().c_str());
        if (albumPtr!= NULL) {
            album = vlcNewStringUTF(env, albumPtr->title().c_str());
            medialibrary::ArtistPtr albumArtistPtr = albumPtr->albumArtist();
            if (albumArtistPtr != NULL)
                albumArtist = vlcNewStringUTF(env, albumArtistPtr->name().c_str());
        }
        trackNumber = p_albumTrack->trackNumber();
        discNumber = p_albumTrack->discNumber();
    }
    const medialibrary::IMetadata& metaAudioTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::AudioTrack);
    jint  audioTrack = metaAudioTrack.isSet() ? metaAudioTrack.asInt() : -2;
    const medialibrary::IMetadata& metaSpuTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::SubtitleTrack);
    jint  spuTrack = metaSpuTrack.isSet() ? metaSpuTrack.asInt() : -2;
    title = mediaPtr->title().empty() ? NULL : vlcNewStringUTF(env, mediaPtr->title().c_str());
    filename = mediaPtr->fileName().empty() ? NULL : vlcNewStringUTF(env, mediaPtr->fileName().c_str());
    try {
        mrl = vlcNewStringUTF(env, files.at(0)->mrl().c_str());
    } catch(const medialibrary::fs::errors::DeviceRemoved&) {
        return nullptr;
    }
    thumbnail = mediaPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).empty() ? NULL : vlcNewStringUTF(env, mediaPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).c_str());
    std::vector<medialibrary::VideoTrackPtr> videoTracks = mediaPtr->videoTracks()->all();
    bool hasVideoTracks = !videoTracks.empty();
    unsigned int width = hasVideoTracks ? videoTracks.at(0)->width() : 0;
    unsigned int height = hasVideoTracks ? videoTracks.at(0)->height() : 0;
    int64_t duration = mediaPtr->duration();

    auto hasThumbnail = mediaPtr->thumbnailStatus(medialibrary::ThumbnailSizeType::Thumbnail) == medialibrary::ThumbnailStatus::Available;
    jobject item = env->NewObject(fields->MediaWrapper.clazz, fields->MediaWrapper.initID,
                          (jlong) mediaPtr->id(), mrl, (jlong) mediaPtr->lastTime(), (jfloat) mediaPtr->lastPosition(), (jlong) duration, type,
                          title, filename, artist, genre, album,
                          albumArtist, width, height, thumbnail,
                          audioTrack, spuTrack, trackNumber, discNumber, (jlong) files.at(0)->lastModificationDate(),
                          (jlong) mediaPtr->playCount(), hasThumbnail, mediaPtr->releaseDate(), isPresent);
    if (artist != NULL)
        env->DeleteLocalRef(artist);
    if (genre != NULL)
        env->DeleteLocalRef(genre);
    if (album != NULL)
        env->DeleteLocalRef(album);
    if (albumArtist != NULL)
        env->DeleteLocalRef(albumArtist);
    if (title != NULL)
        env->DeleteLocalRef(title);
    if (mrl != NULL)
        env->DeleteLocalRef(mrl);
    if (thumbnail != NULL)
        env->DeleteLocalRef(thumbnail);
    if (filename != NULL)
        env->DeleteLocalRef(filename);
    return item;
}

jobject
convertAlbumObject(JNIEnv* env, fields *fields, medialibrary::AlbumPtr const& albumPtr)
{
    jstring title = vlcNewStringUTF(env, albumPtr->title().c_str());
    jstring thumbnailMrl = vlcNewStringUTF(env, albumPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).c_str());
    medialibrary::ArtistPtr artist = albumPtr->albumArtist();
    jlong albumArtistId = artist != nullptr ? albumPtr->albumArtist()->id() : 0;
    jstring artistName = artist != nullptr ? vlcNewStringUTF(env, artist->name().c_str()) : NULL;
    jobject item = env->NewObject(fields->Album.clazz, fields->Album.initID,
                          (jlong) albumPtr->id(), title, albumPtr->releaseYear(), thumbnailMrl, artistName, albumArtistId, (jint) albumPtr->nbTracks(), (jint) albumPtr->nbPresentTracks(), albumPtr->duration());
    env->DeleteLocalRef(title);
    env->DeleteLocalRef(thumbnailMrl);
    env->DeleteLocalRef(artistName);
    return item;
}

jobject
convertArtistObject(JNIEnv* env, fields *fields, medialibrary::ArtistPtr const& artistPtr)
{
    jstring name = vlcNewStringUTF(env, artistPtr->name().c_str());
    jstring thumbnailMrl = vlcNewStringUTF(env, artistPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).c_str());
    jstring shortBio = vlcNewStringUTF(env, artistPtr->shortBio().c_str());
    jstring musicBrainzId = vlcNewStringUTF(env, artistPtr->musicBrainzId().c_str());
    jobject item = env->NewObject(fields->Artist.clazz, fields->Artist.initID,
                          (jlong) artistPtr->id(), name, shortBio, thumbnailMrl, musicBrainzId, (jint) artistPtr->nbAlbums(), (jint) artistPtr->nbTracks(), (jint) artistPtr->nbPresentTracks());
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(thumbnailMrl);
    env->DeleteLocalRef(shortBio);
    env->DeleteLocalRef(musicBrainzId);
    return item;
}

jobject
convertGenreObject(JNIEnv* env, fields *fields, medialibrary::GenrePtr const& genrePtr)
{
    jstring name = vlcNewStringUTF(env, genrePtr->name().c_str());
    jobject item = env->NewObject(fields->Genre.clazz, fields->Genre.initID,
                          (jlong) genrePtr->id(), name, (jint) genrePtr->nbTracks(), (jint) genrePtr->nbPresentTracks());
    env->DeleteLocalRef(name);
    return item;
}

jobject
convertPlaylistObject(JNIEnv* env, fields *fields, medialibrary::PlaylistPtr const& playlistPtr, jboolean includeMissing)
{
    jstring name = vlcNewStringUTF(env, playlistPtr->name().c_str());
     medialibrary::QueryParameters params {
           medialibrary::SortingCriteria::Default,
           false,
           static_cast<bool>( includeMissing )
        };
    jobject item = env->NewObject(fields->Playlist.clazz, fields->Playlist.initID,
                          (jlong) playlistPtr->id(), name, (jint)playlistPtr->media(&params)->count());
    env->DeleteLocalRef(name);
    return item;
}

jobject
convertFolderObject(JNIEnv* env, fields *fields, medialibrary::FolderPtr const& folderPtr, int count)
{
    jstring name = vlcNewStringUTF(env, folderPtr->name().c_str());
    jstring mrl = vlcNewStringUTF(env, folderPtr->mrl().c_str());
    jobject item = env->NewObject(fields->Folder.clazz, fields->Folder.initID,
                          (jlong) folderPtr->id(), name, mrl, (jint) count);
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(mrl);
    return item;
}

jobject
convertVideoGroupObject(JNIEnv* env, fields *fields, medialibrary::MediaGroupPtr const& videogroupPtr)
{
    jstring name = vlcNewStringUTF(env, videogroupPtr->name().c_str());
    jobject item = env->NewObject(fields->VideoGroup.clazz, fields->VideoGroup.initID,
                          (jlong) videogroupPtr->id(), name, (jint)videogroupPtr->nbVideo(), (jint)videogroupPtr->nbPresentVideo());
    env->DeleteLocalRef(name);
    return item;
}

jobject
convertBookmarkObject(JNIEnv* env, fields *fields, medialibrary::BookmarkPtr const& bookmarkPtr)
{
    jstring name = vlcNewStringUTF(env, bookmarkPtr->name().c_str());
    jstring description = vlcNewStringUTF(env, bookmarkPtr->description().c_str());
    jobject item = env->NewObject(fields->Bookmark.clazz, fields->Bookmark.initID,
                          (jlong) bookmarkPtr->id(), name, description, (jlong) bookmarkPtr->mediaId(), (jlong) bookmarkPtr->time());
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(description);
    return item;
}

jobject
convertSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::SearchAggregate const& searchAggregatePtr, jboolean includeMissing)
{
    //Albums
    jobjectArray albums = nullptr;
    int index = -1;
    if (searchAggregatePtr.albums != nullptr) {
        albums = (jobjectArray) env->NewObjectArray(searchAggregatePtr.albums->count(), fields->Album.clazz, NULL);
        for(medialibrary::AlbumPtr const& album : searchAggregatePtr.albums->all()) {
            jobject item = convertAlbumObject(env, fields, album);
            env->SetObjectArrayElement(albums, ++index, item);
            env->DeleteLocalRef(item);
        }
    }
    //Artists
    jobjectArray artists = nullptr;
    if (searchAggregatePtr.artists != nullptr)  {
        index = -1;
        artists = (jobjectArray) env->NewObjectArray(searchAggregatePtr.artists->count(), fields->Artist.clazz, NULL);
        for(medialibrary::ArtistPtr const& artist : searchAggregatePtr.artists->all()) {
            jobject item = convertArtistObject(env, fields, artist);
            env->SetObjectArrayElement(artists, ++index, item);
            env->DeleteLocalRef(item);
        }
    }
    //Genres
    jobjectArray genres = nullptr;
    if (searchAggregatePtr.genres != nullptr)  {
        index = -1;
        genres = (jobjectArray) env->NewObjectArray(searchAggregatePtr.genres->count(), fields->Genre.clazz, NULL);
        for(medialibrary::GenrePtr const& genre : searchAggregatePtr.genres->all()) {
            jobject item = convertGenreObject(env, fields, genre);
            env->SetObjectArrayElement(genres, ++index, item);
            env->DeleteLocalRef(item);
        }
    }
    //Playlists
    jobjectArray playlists = nullptr;
    if (searchAggregatePtr.playlists != nullptr) {
        index = -1;
        playlists = (jobjectArray) env->NewObjectArray(searchAggregatePtr.playlists->count(), fields->Playlist.clazz, NULL);
        for(medialibrary::PlaylistPtr const& playlist : searchAggregatePtr.playlists->all()) {
            jobject item = convertPlaylistObject(env, fields, playlist, includeMissing);
            env->SetObjectArrayElement(playlists, ++index, item);
            env->DeleteLocalRef(item);
        }
    }
    //Media
    std::vector<medialibrary::MediaPtr> videos = {};
    std::vector<medialibrary::MediaPtr> tracks = {};
    jobjectArray videoList = nullptr;
    jobjectArray tracksList = nullptr;
    if (searchAggregatePtr.media != nullptr) {
        for(medialibrary::MediaPtr const& media : searchAggregatePtr.media->all()) {
            if (media->subType() == medialibrary::IMedia::SubType::AlbumTrack) tracks.push_back(media);
            else videos.push_back(media);
        }
        videoList = (jobjectArray) env->NewObjectArray(videos.size(), fields->MediaWrapper.clazz, NULL);
        index = -1;
        for(medialibrary::MediaPtr const& media : videos) {
            jobject item = mediaToMediaWrapper(env, fields, media);
            env->SetObjectArrayElement(videoList, ++index, item);
            env->DeleteLocalRef(item);
        }
        tracksList = (jobjectArray) env->NewObjectArray(tracks.size(), fields->MediaWrapper.clazz, NULL);
        index = -1;
        for(medialibrary::MediaPtr const& media : tracks) {
            jobject item = mediaToMediaWrapper(env, fields, media);
            env->SetObjectArrayElement(tracksList, ++index, item);
            env->DeleteLocalRef(item);
        }
    }
    return env->NewObject(fields->SearchAggregate.clazz, fields->SearchAggregate.initID,
                          albums, artists, genres, videoList, tracksList, playlists);
}

jlongArray
idArray(JNIEnv* env, std::set<int64_t> ids)
{
    jlongArray results;
    int i = 0;
    results = (jlongArray)env->NewLongArray(ids.size());
    jlong fill[ids.size()];
    for (auto id : ids) {
        fill[i] = id;
        i++;
    }
    env->SetLongArrayRegion(results, 0, ids.size(), fill);
    return results;
}

jobjectArray
filteredArray(JNIEnv* env, jobjectArray array, jclass clazz, int removalCount)
{
    int size = -1, index = -1;
    if (removalCount == -1)
    {
        removalCount = 0;
        size = env->GetArrayLength(array);
        for (int i = 0; i<size; ++i)
        {
            jobject item = env->GetObjectArrayElement(array, i);
            if (item == nullptr)
                ++removalCount;
            env->DeleteLocalRef(item);
        }
    }
    if (removalCount == 0)
        return array;
    if (size == -1)
        size = env->GetArrayLength(array);
    jobjectArray mediaRefs = (jobjectArray) env->NewObjectArray(size-removalCount, clazz, NULL);
    for (int i = 0; i<size; ++i)
    {
        jobject item = env->GetObjectArrayElement(array, i);
        if (item != nullptr)
        {
            env->SetObjectArrayElement(mediaRefs, ++index, item);
            --removalCount;
        }
        env->DeleteLocalRef(item);
    }
    env->DeleteLocalRef(array);
    return mediaRefs;
}

jstring
vlcNewStringUTF(JNIEnv* env, const char* psz_string)
{
    for (int i = 0 ; psz_string[i] != '\0' ; ) {
        uint8_t lead = psz_string[i++];
        uint8_t nbBytes;
        if ((lead & 0x80) == 0)
            continue;
        else if ((lead >> 5) == 0x06)
            nbBytes = 1;
        else if ((lead >> 4) == 0x0E)
            nbBytes = 2;
        else if ((lead >> 3) == 30)
            nbBytes = 3;
        else {
            LOGE("Invalid UTF lead character\n");
            return NULL;
        }
        for (int j = 0 ; j < nbBytes && psz_string[i] != '\0' ; j++) {
            uint8_t byte = psz_string[i++];
            if ((byte & 0x80) == 0) {
                LOGE("Invalid UTF byte\n");
                return NULL;
            }
        }
    }
    return env->NewStringUTF(psz_string);
}

