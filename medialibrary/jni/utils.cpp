
#include "utils.h"
#include <medialibrary/IAlbumTrack.h>
#include <medialibrary/IVideoTrack.h>
#include <medialibrary/IFile.h>
#include <medialibrary/IMedia.h>
#include <medialibrary/IArtist.h>
#include <medialibrary/IHistoryEntry.h>
#include <medialibrary/IGenre.h>
#include <medialibrary/IAlbum.h>
#include <medialibrary/IPlaylist.h>
#include <medialibrary/IMediaLibrary.h>
#include <medialibrary/IMetadata.h>
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
    jstring artist = NULL, genre = NULL, album = NULL, albumArtist = NULL, mrl = NULL, title = NULL, thumbnail = NULL;
    jint trackNumber = 0, discNumber = 0;
    if (p_albumTrack)
    {
        medialibrary::ArtistPtr artistPtr = p_albumTrack->artist();
        medialibrary::GenrePtr genrePtr = p_albumTrack->genre();
        medialibrary::AlbumPtr albumPtr = p_albumTrack->album();
        if (artistPtr != NULL)
            artist = env->NewStringUTF(artistPtr->name().c_str());
        if (genrePtr != NULL)
            genre = env->NewStringUTF(genrePtr->name().c_str());
        if (albumPtr!= NULL) {
            album = env->NewStringUTF(albumPtr->title().c_str());
            medialibrary::ArtistPtr albumArtistPtr = albumPtr->albumArtist();
            if (albumArtistPtr != NULL)
                albumArtist = env->NewStringUTF(albumArtistPtr->name().c_str());
        }
        trackNumber = p_albumTrack->trackNumber();
        discNumber = p_albumTrack->discNumber();
    }
    const medialibrary::IMetadata& metaAudioTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::AudioTrack);
    jint  audioTrack = metaAudioTrack.isSet() ? metaAudioTrack.integer() : -2;
    const medialibrary::IMetadata& metaSpuTrack = mediaPtr->metadata(medialibrary::IMedia::MetadataType::SubtitleTrack);
    jint  spuTrack = metaSpuTrack.isSet() ? metaSpuTrack.integer() : -2;
    title = mediaPtr->title().empty() ? NULL : env->NewStringUTF(mediaPtr->title().c_str());
    mrl = env->NewStringUTF(files.at(0)->mrl().c_str());
    thumbnail = mediaPtr->thumbnail().empty() ? NULL : env->NewStringUTF(mediaPtr->thumbnail().c_str());
    std::vector<medialibrary::VideoTrackPtr> videoTracks = mediaPtr->videoTracks()->all();
    bool hasVideoTracks = !videoTracks.empty();
    unsigned int width = hasVideoTracks ? videoTracks.at(0)->width() : 0;
    unsigned int height = hasVideoTracks ? videoTracks.at(0)->height() : 0;
    int64_t duration = mediaPtr->duration();
    const medialibrary::IMetadata& progressMeta = mediaPtr->metadata( medialibrary::IMedia::MetadataType::Progress );
    int64_t progress = progressMeta.isSet() ? progressMeta.integer() : 0;
    // workaround to convert legacy percentage progress
    if (progress != 0 && progress < 100) progress = duration * ( progress / 100.0 );
    const medialibrary::IMetadata& seenMeta =  mediaPtr->metadata( medialibrary::IMedia::MetadataType::Seen );
    int64_t seen = seenMeta.isSet() ? seenMeta.integer() : 0;

    jobject item = env->NewObject(fields->MediaWrapper.clazz, fields->MediaWrapper.initID,
                          (jlong) mediaPtr->id(), mrl,(jlong) progress, (jlong) duration, type,
                          title, artist, genre, album,
                          albumArtist, width, height, thumbnail,
                          audioTrack, spuTrack, trackNumber, discNumber, (jlong) files.at(0)->lastModificationDate(), seen, mediaPtr->isThumbnailGenerated());
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
    return item;
}

jobject
convertAlbumObject(JNIEnv* env, fields *fields, medialibrary::AlbumPtr const& albumPtr)
{
    jstring title = env->NewStringUTF(albumPtr->title().c_str());
    jstring artworkMrl = env->NewStringUTF(albumPtr->artworkMrl().c_str());
    medialibrary::ArtistPtr artist = albumPtr->albumArtist();
    jlong albumArtistId = artist != nullptr ? albumPtr->albumArtist()->id() : 0;
    jstring artistName = artist != nullptr ? env->NewStringUTF(artist->name().c_str()) : NULL;
    jobject item = env->NewObject(fields->Album.clazz, fields->Album.initID,
                          (jlong) albumPtr->id(), title, albumPtr->releaseYear(), artworkMrl, artistName, albumArtistId, (jint) albumPtr->nbTracks(), (jint) albumPtr->duration());
    env->DeleteLocalRef(title);
    env->DeleteLocalRef(artworkMrl);
    env->DeleteLocalRef(artistName);
    return item;
}

jobject
convertArtistObject(JNIEnv* env, fields *fields, medialibrary::ArtistPtr const& artistPtr)
{
    jstring name = env->NewStringUTF(artistPtr->name().c_str());
    jstring artworkMrl = env->NewStringUTF(artistPtr->artworkMrl().c_str());
    jstring shortBio = env->NewStringUTF(artistPtr->shortBio().c_str());
    jstring musicBrainzId = env->NewStringUTF(artistPtr->musicBrainzId().c_str());
    jobject item = env->NewObject(fields->Artist.clazz, fields->Artist.initID,
                          (jlong) artistPtr->id(), name, shortBio, artworkMrl, musicBrainzId);
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(artworkMrl);
    env->DeleteLocalRef(shortBio);
    env->DeleteLocalRef(musicBrainzId);
    return item;
}

jobject
convertGenreObject(JNIEnv* env, fields *fields, medialibrary::GenrePtr const& genrePtr)
{
    jstring name = env->NewStringUTF(genrePtr->name().c_str());
    jobject item = env->NewObject(fields->Genre.clazz, fields->Genre.initID,
                          (jlong) genrePtr->id(), name);
    env->DeleteLocalRef(name);
    return item;
}

jobject
convertPlaylistObject(JNIEnv* env, fields *fields, medialibrary::PlaylistPtr const& playlistPtr)
{
    jstring name = env->NewStringUTF(playlistPtr->name().c_str());
    jobject item = env->NewObject(fields->Playlist.clazz, fields->Playlist.initID,
                          (jlong) playlistPtr->id(), name, (jint)playlistPtr->media()->count());
    env->DeleteLocalRef(name);
    return item;
}

jobject
convertMediaSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::MediaSearchAggregate const& mediaSearchAggregatePtr)
{
    jobjectArray episodes = (jobjectArray) env->NewObjectArray(mediaSearchAggregatePtr.episodes->count(), fields->MediaWrapper.clazz, NULL);
    int index = -1, epDrops = 0;
    for(medialibrary::MediaPtr const& media : mediaSearchAggregatePtr.episodes->all()) {
        jobject item = mediaToMediaWrapper(env, fields, media);
        if (item != nullptr)
            env->SetObjectArrayElement(episodes, ++index, item);
        else
            ++epDrops;
        env->DeleteLocalRef(item);
    }
    jobjectArray movies = (jobjectArray) env->NewObjectArray(mediaSearchAggregatePtr.movies->count(), fields->MediaWrapper.clazz, NULL);
    index = -1;
    int movieDrops = 0;
    for(medialibrary::MediaPtr const& media : mediaSearchAggregatePtr.movies->all()) {
        jobject item = mediaToMediaWrapper(env, fields, media);
        if (item != nullptr)
            env->SetObjectArrayElement(movies, ++index, item);
        else
            ++movieDrops;
        env->DeleteLocalRef(item);
    }
    jobjectArray others = (jobjectArray) env->NewObjectArray(mediaSearchAggregatePtr.others->count(), fields->MediaWrapper.clazz, NULL);
    index = -1;
    int othersDrops = 0;
    for(medialibrary::MediaPtr const& media : mediaSearchAggregatePtr.others->all()) {
        jobject item = mediaToMediaWrapper(env, fields, media);
        if (item != nullptr)
            env->SetObjectArrayElement(others, ++index, item);
        else
            ++othersDrops;
        env->DeleteLocalRef(item);
    }
    jobjectArray tracks = (jobjectArray) env->NewObjectArray(mediaSearchAggregatePtr.tracks->count(), fields->MediaWrapper.clazz, NULL);
    index = -1;
    int tracksDrops = 0;
    for(medialibrary::MediaPtr const& media : mediaSearchAggregatePtr.tracks->all()) {
        jobject item = mediaToMediaWrapper(env, fields, media);
        if (item != nullptr)
            env->SetObjectArrayElement(tracks, ++index, item);
        else
            ++tracksDrops;
        env->DeleteLocalRef(item);
    }
    return env->NewObject(fields->MediaSearchAggregate.clazz, fields->MediaSearchAggregate.initID,
                          filteredArray(env, episodes, fields->MediaWrapper.clazz, epDrops),
                          filteredArray(env, movies, fields->MediaWrapper.clazz, movieDrops),
                          filteredArray(env, others, fields->MediaWrapper.clazz, othersDrops),
                          filteredArray(env, tracks, fields->MediaWrapper.clazz, tracksDrops));
}

jobject
convertSearchAggregateObject(JNIEnv* env, fields *fields, medialibrary::SearchAggregate const& searchAggregatePtr)
{
    //Albums
    jobjectArray albums = (jobjectArray) env->NewObjectArray(searchAggregatePtr.albums->count(), fields->Album.clazz, NULL);
    int index = -1;
    for(medialibrary::AlbumPtr const& album : searchAggregatePtr.albums->all()) {
        jobject item = convertAlbumObject(env, fields, album);
        env->SetObjectArrayElement(albums, ++index, item);
        env->DeleteLocalRef(item);
    }
    //Artists
    jobjectArray artists = (jobjectArray) env->NewObjectArray(searchAggregatePtr.artists->count(), fields->Artist.clazz, NULL);
    index = -1;
    for(medialibrary::ArtistPtr const& artist : searchAggregatePtr.artists->all()) {
        jobject item = convertArtistObject(env, fields, artist);
        env->SetObjectArrayElement(artists, ++index, item);
        env->DeleteLocalRef(item);
    }
    //Genres
    jobjectArray genres = (jobjectArray) env->NewObjectArray(searchAggregatePtr.genres->count(), fields->Genre.clazz, NULL);
    index = -1;
    for(medialibrary::GenrePtr const& genre : searchAggregatePtr.genres->all()) {
        jobject item = convertGenreObject(env, fields, genre);
        env->SetObjectArrayElement(genres, ++index, item);
        env->DeleteLocalRef(item);
    }
    //Playlists
    jobjectArray playlists = (jobjectArray) env->NewObjectArray(searchAggregatePtr.playlists->count(), fields->Playlist.clazz, NULL);
    index = -1;
    for(medialibrary::PlaylistPtr const& playlist : searchAggregatePtr.playlists->all()) {
        jobject item = convertPlaylistObject(env, fields, playlist);
        env->SetObjectArrayElement(playlists, ++index, item);
        env->DeleteLocalRef(item);
    }
    return env->NewObject(fields->SearchAggregate.clazz, fields->SearchAggregate.initID,
                          albums, artists, genres, convertMediaSearchAggregateObject(env, fields, searchAggregatePtr.media), playlists);
}

jobject
convertHistoryItemObject(JNIEnv* env, fields *fields, medialibrary::HistoryPtr const& historyPtr)
{
    auto media = historyPtr->media().get();
    jstring mrl = env->NewStringUTF(media->files()[0]->mrl().c_str());
    jstring title = env->NewStringUTF(media->title().c_str());
    jobject item = env->NewObject(fields->HistoryItem.clazz, fields->HistoryItem.initID, mrl, title,
                          (jlong) historyPtr->insertionDate(), (jboolean) false);
    env->DeleteLocalRef(mrl);
    env->DeleteLocalRef(title);
    return item;
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
