
#include "utils.h"
#define LOG_TAG "VLC/JNI/Utils"
#include "log.h"

utils::jni::object
mediaToMediaWrapper(JNIEnv* env, fields *fields, medialibrary::MediaPtr const& mediaPtr)
{
    if (mediaPtr == nullptr)
        return {};
    const std::vector<medialibrary::FilePtr> files = mediaPtr->files();
    if (files.empty())
        return {};
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
    utils::jni::string artist, genre, album, albumArtist, mrl, title, thumbnail, filename;
    jint trackNumber = 0, discNumber = 0;

    const bool isPresent = mediaPtr->isPresent();
    medialibrary::ArtistPtr artistPtr = mediaPtr->artist();
    medialibrary::GenrePtr genrePtr = mediaPtr->genre();
    medialibrary::AlbumPtr albumPtr = mediaPtr->album();
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
    trackNumber = mediaPtr->trackNumber();
    discNumber = mediaPtr->discNumber();
    const medialibrary::IMetadata& metaAudioTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::AudioTrack);
    jint  audioTrack = metaAudioTrack.isSet() ? metaAudioTrack.asInt() : -2;
    const medialibrary::IMetadata& metaSpuTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::SubtitleTrack);
    jint  spuTrack = metaSpuTrack.isSet() ? metaSpuTrack.asInt() : -2;
    if (!mediaPtr->title().empty())
        title = vlcNewStringUTF(env, mediaPtr->title().c_str());
    if (!mediaPtr->fileName().empty())
        filename = vlcNewStringUTF(env, mediaPtr->fileName().c_str());
    try {
        mrl = vlcNewStringUTF(env, files.at(0)->mrl().c_str());
    } catch(const medialibrary::fs::errors::DeviceRemoved&) {
        mrl = vlcNewStringUTF(env, "missing://");
    }
    auto thumbnailStr = mediaPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail);
    if (!thumbnailStr.empty())
        thumbnail = vlcNewStringUTF(env, thumbnailStr.c_str());
    std::vector<medialibrary::VideoTrackPtr> videoTracks = mediaPtr->videoTracks()->all();
    bool hasVideoTracks = !videoTracks.empty();
    unsigned int width = hasVideoTracks ? videoTracks.at(0)->width() : 0;
    unsigned int height = hasVideoTracks ? videoTracks.at(0)->height() : 0;
    int64_t duration = mediaPtr->duration();

    auto hasThumbnail = mediaPtr->thumbnailStatus(medialibrary::ThumbnailSizeType::Thumbnail) == medialibrary::ThumbnailStatus::Available;
    auto isFavorite = mediaPtr->isFavorite();
    return { env, env->NewObject(fields->MediaWrapper.clazz, fields->MediaWrapper.initID,
                          (jlong) mediaPtr->id(), mrl.get(), (jlong) mediaPtr->lastTime(), (jfloat) mediaPtr->lastPosition(), (jlong) duration, type,
                          title.get(), filename.get(), artist.get(), genre.get(), album.get(),
                          albumArtist.get(), width, height, thumbnail.get(),
                          audioTrack, spuTrack, trackNumber, discNumber, (jlong) files.at(0)->lastModificationDate(),
                          (jlong) mediaPtr->playCount(), hasThumbnail, isFavorite, mediaPtr->releaseDate(), isPresent, (jlong) mediaPtr->insertionDate())
    };
}

utils::jni::object
convertAlbumObject(JNIEnv* env, fields *fields, medialibrary::AlbumPtr const& albumPtr)
{
    auto title = vlcNewStringUTF(env, albumPtr->title().c_str());
    auto thumbnailMrl = vlcNewStringUTF(env, albumPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).c_str());
    medialibrary::ArtistPtr artist = albumPtr->albumArtist();
    jlong albumArtistId = artist != nullptr ? albumPtr->albumArtist()->id() : 0;
    utils::jni::string artistName;
    auto isFavorite = albumPtr->isFavorite();
    if ( artist != nullptr )
        artistName = vlcNewStringUTF(env, artist->name().c_str());
    return utils::jni::object{ env, env->NewObject(fields->Album.clazz, fields->Album.initID,
                          (jlong) albumPtr->id(), title.get(), albumPtr->releaseYear(),
                                  thumbnailMrl.get(), artistName.get(), albumArtistId, (jint) albumPtr->nbTracks(), (jint) albumPtr->nbPresentTracks(), albumPtr->duration(), isFavorite)
    };
}

utils::jni::object
convertArtistObject(JNIEnv* env, fields *fields, medialibrary::ArtistPtr const& artistPtr)
{
    auto name = vlcNewStringUTF(env, artistPtr->name().c_str());
    auto thumbnailMrl = vlcNewStringUTF(env, artistPtr->thumbnailMrl(medialibrary::ThumbnailSizeType::Thumbnail).c_str());
    auto shortBio = vlcNewStringUTF(env, artistPtr->shortBio().c_str());
    auto isFavorite = artistPtr->isFavorite();
    auto musicBrainzId = vlcNewStringUTF(env, artistPtr->musicBrainzId().c_str());
    return utils::jni::object{ env, env->NewObject(fields->Artist.clazz, fields->Artist.initID,
                          (jlong) artistPtr->id(), name.get(), shortBio.get(), thumbnailMrl.get(),
                                  musicBrainzId.get(), (jint) artistPtr->nbAlbums(), (jint) artistPtr->nbTracks(), (jint) artistPtr->nbPresentTracks(), isFavorite)
    };
}

utils::jni::object
convertGenreObject(JNIEnv* env, fields *fields, medialibrary::GenrePtr const& genrePtr)
{
    auto name = vlcNewStringUTF(env, genrePtr->name().c_str());
    auto isFavorite = genrePtr->isFavorite();
    return utils::jni::object{ env, env->NewObject(fields->Genre.clazz, fields->Genre.initID,
                          (jlong) genrePtr->id(), name.get(), (jint) genrePtr->nbTracks(), (jint) genrePtr->nbPresentTracks(), isFavorite)
    };
}

utils::jni::object
convertPlaylistObject(JNIEnv* env, fields *fields, medialibrary::PlaylistPtr const& playlistPtr, jboolean includeMissing, jboolean onlyFavorites)
{
    auto name = vlcNewStringUTF(env, playlistPtr->name().c_str());
    return utils::jni::object{ env, env->NewObject(fields->Playlist.clazz, fields->Playlist.initID,
                          (jlong) playlistPtr->id(), name.get(), (jint)playlistPtr->nbMedia(), (jlong)playlistPtr->duration(), (jint)playlistPtr->nbVideo(), (jint)playlistPtr->nbAudio(), (jint)playlistPtr->nbUnknown(), (jint)playlistPtr->nbDurationUnknown(), (jboolean)playlistPtr->isFavorite())
    };
}

utils::jni::object
convertFolderObject(JNIEnv* env, fields *fields, medialibrary::FolderPtr const& folderPtr, int count)
{
    auto name = vlcNewStringUTF(env, folderPtr->name().c_str());
    utils::jni::string mrl;
    try
    {
        mrl = vlcNewStringUTF(env, folderPtr->mrl().c_str());
    }
    catch( const medialibrary::fs::errors::DeviceRemoved& )
    {
        mrl = vlcNewStringUTF(env, "missing://");
    }
    return utils::jni::object{ env, env->NewObject(fields->Folder.clazz, fields->Folder.initID,
                          (jlong) folderPtr->id(), name.get(), mrl.get(), (jint) count, (jboolean)folderPtr->isFavorite())
    };
}

utils::jni::object
convertVideoGroupObject(JNIEnv* env, fields *fields, medialibrary::MediaGroupPtr const& videogroupPtr)
{
    auto name = vlcNewStringUTF(env, videogroupPtr->name().c_str());
    return utils::jni::object{ env, env->NewObject(fields->VideoGroup.clazz, fields->VideoGroup.initID,
                          (jlong) videogroupPtr->id(), name.get(), (jint)videogroupPtr->nbVideo(), (jint)videogroupPtr->nbPresentVideo(), (jint)videogroupPtr->nbPresentSeen(), (jboolean)videogroupPtr->isFavorite())
    };
}

utils::jni::object
convertBookmarkObject(JNIEnv* env, fields *fields, medialibrary::BookmarkPtr const& bookmarkPtr)
{
    auto name = vlcNewStringUTF(env, bookmarkPtr->name().c_str());
    auto description = vlcNewStringUTF(env, bookmarkPtr->description().c_str());
    return utils::jni::object{ env, env->NewObject(fields->Bookmark.clazz, fields->Bookmark.initID,
                          (jlong) bookmarkPtr->id(), name.get(), description.get(), (jlong) bookmarkPtr->mediaId(), (jlong) bookmarkPtr->time())
    };
}

utils::jni::object
convertSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::SearchAggregate const& searchAggregatePtr, jboolean includeMissing, jboolean onlyFavorites)
{
    //Albums
    utils::jni::objectArray albums;
    int index = -1;
    if (searchAggregatePtr.albums != nullptr) {
        albums = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(searchAggregatePtr.albums->count(), fields->Album.clazz, NULL) };
        for(medialibrary::AlbumPtr const& album : searchAggregatePtr.albums->all()) {
            auto item = convertAlbumObject(env, fields, album);
            env->SetObjectArrayElement(albums.get(), ++index, item.get());
        }
    }
    //Artists
    utils::jni::objectArray artists;
    if (searchAggregatePtr.artists != nullptr)  {
        index = -1;
        artists = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(searchAggregatePtr.artists->count(), fields->Artist.clazz, NULL) };
        for(medialibrary::ArtistPtr const& artist : searchAggregatePtr.artists->all()) {
            auto item = convertArtistObject(env, fields, artist);
            env->SetObjectArrayElement(artists.get(), ++index, item.get());
        }
    }
    //Genres
    utils::jni::objectArray genres;
    if (searchAggregatePtr.genres != nullptr)  {
        index = -1;
        genres = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(searchAggregatePtr.genres->count(), fields->Genre.clazz, NULL) };
        for(medialibrary::GenrePtr const& genre : searchAggregatePtr.genres->all()) {
            auto item = convertGenreObject(env, fields, genre);
            env->SetObjectArrayElement(genres.get(), ++index, item.get());
        }
    }
    //Playlists
    utils::jni::objectArray playlists;
    if (searchAggregatePtr.playlists != nullptr) {
        index = -1;
        playlists = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(searchAggregatePtr.playlists->count(), fields->Playlist.clazz, NULL) };
        for(medialibrary::PlaylistPtr const& playlist : searchAggregatePtr.playlists->all()) {
            auto item = convertPlaylistObject(env, fields, playlist, includeMissing, onlyFavorites);
            env->SetObjectArrayElement(playlists.get(), ++index, item.get());
        }
    }
    //Media
    std::vector<medialibrary::MediaPtr> videos = {};
    std::vector<medialibrary::MediaPtr> tracks = {};
    utils::jni::objectArray videoList;
    utils::jni::objectArray tracksList;
    if (searchAggregatePtr.media != nullptr) {
        for(medialibrary::MediaPtr const& media : searchAggregatePtr.media->all()) {
            if (media->subType() == medialibrary::IMedia::SubType::AlbumTrack) tracks.push_back(media);
            else videos.push_back(media);
        }
        videoList = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(videos.size(), fields->MediaWrapper.clazz, NULL) };
        index = -1;
        for(medialibrary::MediaPtr const& media : videos) {
            auto item = mediaToMediaWrapper(env, fields, media);
            env->SetObjectArrayElement(videoList.get(), ++index, item.get());
        }
        tracksList = utils::jni::objectArray{ env, (jobjectArray) env->NewObjectArray(tracks.size(), fields->MediaWrapper.clazz, NULL) };
        index = -1;
        for(medialibrary::MediaPtr const& media : tracks) {
            auto item = mediaToMediaWrapper(env, fields, media);
            env->SetObjectArrayElement(tracksList.get(), ++index, item.get());
        }
    }
    return { env, env->NewObject(fields->SearchAggregate.clazz, fields->SearchAggregate.initID,
                          albums.get(), artists.get(), genres.get(), videoList.get(), tracksList.get(), playlists.get())
    };
}
utils::jni::object
convertSubscriptionObject(JNIEnv* env, fields *fields, medialibrary::SubscriptionPtr const& subsPtr)
{
    auto name = vlcNewStringUTF(env, subsPtr->name().c_str());
    return utils::jni::object{ env, env->NewObject(fields->Subscription.clazz, fields->Subscription.initID,
            (jlong) subsPtr->id(), (jint) subsPtr->service(), name.get())
    };
}

utils::jni::object
convertServiceObject(JNIEnv* env, fields *fields, medialibrary::ServicePtr const& servicePtr)
{
    return utils::jni::object{ env, env->NewObject(fields->Service.clazz,
            fields->Service.initID, (jint) servicePtr->type())
    };
}

utils::jni::longArray
idArray(JNIEnv* env, std::set<int64_t> ids)
{
    int i = 0;
    utils::jni::longArray results{ env, (jlongArray)env->NewLongArray(ids.size()) };
    jlong fill[ids.size()];
    for (auto id : ids) {
        fill[i] = id;
        i++;
    }
    env->SetLongArrayRegion(results.get(), 0, ids.size(), fill);
    return results;
}

utils::jni::objectArray
filteredArray(JNIEnv* env, utils::jni::objectArray array, jclass clazz, int removalCount)
{
    int size = -1, index = -1;
    if (removalCount == -1)
    {
        removalCount = 0;
        size = env->GetArrayLength(array.get());
        for (int i = 0; i<size; ++i)
        {
            utils::jni::object item{ env, env->GetObjectArrayElement(array.get(), i) };
            if (item == nullptr)
                ++removalCount;
        }
    }
    if (removalCount == 0)
        return array;
    if (size == -1)
        size = env->GetArrayLength(array.get());
    utils::jni::objectArray mediaRefs{ env, (jobjectArray) env->NewObjectArray(size-removalCount, clazz, NULL) };
    for (int i = 0; i<size; ++i)
    {
        utils::jni::object item{ env, env->GetObjectArrayElement(array.get(), i) };
        if (item != nullptr)
        {
            env->SetObjectArrayElement(mediaRefs.get(), ++index, item.get());
            --removalCount;
        }
    }
    return mediaRefs;
}

utils::jni::string
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
            return {};
        }
        for (int j = 0 ; j < nbBytes && psz_string[i] != '\0' ; j++) {
            uint8_t byte = psz_string[i++];
            if ((byte & 0x80) == 0) {
                LOGE("Invalid UTF byte\n");
                return {};
            }
        }
    }
    return utils::jni::string{ env, env->NewStringUTF(psz_string) };
}
