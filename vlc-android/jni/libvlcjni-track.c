/*****************************************************************************
 * libvlcjni-track.c
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <vlc/vlc.h>
#include <vlc_common.h>
#include <vlc_fourcc.h>

#include <jni.h>

#include "utils.h"

#define LOG_TAG "VLC/JNI/track"
#include "log.h"

struct length_change_monitor {
    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
    bool length_changed;
};

static void length_changed_callback(const libvlc_event_t *ev, void *data)
{
    struct length_change_monitor *monitor = data;
    pthread_mutex_lock(&monitor->doneMutex);
    monitor->length_changed = true;
    pthread_cond_signal(&monitor->doneCondVar);
    pthread_mutex_unlock(&monitor->doneMutex);
}

jboolean Java_org_videolan_libvlc_LibVLC_hasVideoTrack(JNIEnv *env, jobject thiz,
                                                       jlong i_instance, jstring fileLocation)
{
    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = new_media(i_instance, env, thiz, fileLocation, false, false);
    if (p_m == NULL)
    {
        LOGE("Could not create the media!");
        return JNI_FALSE;
    }

    /* Get the tracks information of the media. */
    libvlc_media_parse(p_m);

    libvlc_media_player_t* p_mp = libvlc_media_player_new_from_media(p_m);
    libvlc_media_player_set_video_title_display(p_mp, libvlc_position_disable, 0);

    struct length_change_monitor* monitor;
    monitor = malloc(sizeof(struct length_change_monitor));
    if (!monitor) return 0;

    /* Initialize pthread variables. */
    pthread_mutex_init(&monitor->doneMutex, NULL);
    pthread_cond_init(&monitor->doneCondVar, NULL);
    monitor->length_changed = false;

    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(p_mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_player_play( p_mp );

    pthread_mutex_lock(&monitor->doneMutex);

    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 2; /* If "VLC can't open the file", return */
    int mp_alive = 1;
    while( !monitor->length_changed && mp_alive ) {
        pthread_cond_timedwait(&monitor->doneCondVar, &monitor->doneMutex, &deadline);
        mp_alive = libvlc_media_player_will_play(p_mp);
    }
    pthread_mutex_unlock(&monitor->doneMutex);

    int i_nbTracks;
    if( mp_alive )
        i_nbTracks = libvlc_video_get_track_count(p_mp);
    else
        i_nbTracks = -1;
    LOGI("Number of video tracks: %d",i_nbTracks);

    libvlc_event_detach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_player_stop(p_mp);
    libvlc_media_player_release(p_mp);
    libvlc_media_release(p_m);

    pthread_mutex_destroy(&monitor->doneMutex);
    pthread_cond_destroy(&monitor->doneCondVar);
    free(monitor);

    if(i_nbTracks > 0)
        return JNI_TRUE;
    else if(i_nbTracks < 0)
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "VLC can't open the file");
    else
        return JNI_FALSE;
}

jobjectArray read_track_info_internal(JNIEnv *env, jobject thiz, libvlc_media_t* p_m)
{
    /* get java class */
    jclass cls = (*env)->FindClass( env, "org/videolan/libvlc/TrackInfo" );
    if ( !cls )
    {
        LOGE("Failed to load class (org/videolan/libvlc/TrackInfo)" );
        return NULL;
    }

    /* get java class contructor */
    jmethodID clsCtor = (*env)->GetMethodID( env, cls, "<init>", "()V" );
    if ( !clsCtor )
    {
        LOGE("Failed to find class constructor (org/videolan/libvlc/TrackInfo)" );
        return NULL;
    }

    /* Get the tracks information of the media. */
    libvlc_media_track_t **p_tracks;

    int i_nbTracks = libvlc_media_tracks_get(p_m, &p_tracks);
    jobjectArray array = (*env)->NewObjectArray(env, i_nbTracks + 1, cls, NULL);

    unsigned i;
    if (array != NULL)
    {
        for (i = 0; i <= i_nbTracks; ++i)
        {
            jobject item = (*env)->NewObject(env, cls, clsCtor);
            if (item == NULL)
                continue;
            (*env)->SetObjectArrayElement(env, array, i, item);

            // use last track for metadata
            if (i == i_nbTracks)
            {
                setInt(env, item, "Type", 3 /* TYPE_META */);
                setLong(env, item, "Length", libvlc_media_get_duration(p_m));
                setString(env, item, "Title", libvlc_media_get_meta(p_m, libvlc_meta_Title));
                setString(env, item, "Artist", libvlc_media_get_meta(p_m, libvlc_meta_Artist));
                setString(env, item, "Album", libvlc_media_get_meta(p_m, libvlc_meta_Album));
                setString(env, item, "Genre", libvlc_media_get_meta(p_m, libvlc_meta_Genre));
                setString(env, item, "ArtworkURL", libvlc_media_get_meta(p_m, libvlc_meta_ArtworkURL));
                continue;
            }

            setInt(env, item, "Id", p_tracks[i]->i_id);
            setInt(env, item, "Type", p_tracks[i]->i_type);
            setString(env, item, "Codec", (const char*)vlc_fourcc_GetDescription(0,p_tracks[i]->i_codec));
            setString(env, item, "Language", p_tracks[i]->psz_language);

            if (p_tracks[i]->i_type == libvlc_track_video)
            {
                setInt(env, item, "Height", p_tracks[i]->video->i_height);
                setInt(env, item, "Width", p_tracks[i]->video->i_width);
                setFloat(env, item, "Framerate", (float)p_tracks[i]->video->i_frame_rate_num / p_tracks[i]->video->i_frame_rate_den);
            }
            if (p_tracks[i]->i_type == libvlc_track_audio)
            {
                setInt(env, item, "Channels", p_tracks[i]->audio->i_channels);
                setInt(env, item, "Samplerate", p_tracks[i]->audio->i_rate);
            }
        }
    }

    libvlc_media_tracks_release(p_tracks, i_nbTracks);
    return array;
}

jobjectArray Java_org_videolan_libvlc_LibVLC_readTracksInfo(JNIEnv *env, jobject thiz,
                                                            jlong instance, jstring mrl)
{
    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = new_media(instance, env, thiz, mrl, false, false);
    if (p_m == NULL)
    {
        LOGE("Could not create the media!");
        return NULL;
    }

    libvlc_media_parse(p_m);
    jobjectArray jar = read_track_info_internal(env, thiz, p_m);
    libvlc_media_release(p_m);
    return jar;
}


jobjectArray Java_org_videolan_libvlc_LibVLC_readTracksInfoInternal(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t* p_mp = getMediaPlayer(env, thiz);
    if (p_mp == NULL) {
        LOGE("No media player!");
        return NULL;
    }
    libvlc_media_t *p_m = libvlc_media_player_get_media(p_mp);
    if (p_m == NULL) {
        LOGE("Could not load internal media!");
        return NULL;
    } else
        return read_track_info_internal(env, thiz, p_m);
}

jint Java_org_videolan_libvlc_LibVLC_getAudioTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_track_count(mp);
    return -1;
}

jobject Java_org_videolan_libvlc_LibVLC_getAudioTrackDescription(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (!mp)
        return NULL;

    int i_nbTracks = libvlc_audio_get_track_count(mp) - 1;
    if (i_nbTracks < 0)
        i_nbTracks = 0;
    jclass mapClass = (*env)->FindClass(env, "java/util/Map");
    jclass hashMapClass = (*env)->FindClass(env, "java/util/HashMap");
    jmethodID mapPut = (*env)->GetMethodID(env, mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    /*
     * "What are you building? Lay your hand on it. Where is it?"
     * We need a concrete map to start
     */
    jmethodID mapInit = (*env)->GetMethodID(env, hashMapClass, "<init>", "()V");
    jclass integerCls = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID integerConstructor = (*env)->GetMethodID(env, integerCls, "<init>", "(I)V");

    jobject audioTrackMap = (*env)->NewObject(env, hashMapClass, mapInit);

    libvlc_track_description_t *first = libvlc_audio_get_track_description(mp);
    libvlc_track_description_t *desc = first != NULL ? first->p_next : NULL;
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
    {
        // store audio track ID and name in a map as <ID, Track Name>
        jobject track_id = (*env)->NewObject(env, integerCls, integerConstructor, desc->i_id);
        jstring name = (*env)->NewStringUTF(env, desc->psz_name);
        (*env)->CallObjectMethod(env, audioTrackMap, mapPut, track_id, name);
        desc = desc->p_next;
    }
    libvlc_track_description_list_release(first);

    // Clean up local references
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, hashMapClass);
    (*env)->DeleteLocalRef(env, integerCls);

    return audioTrackMap;
}

jint Java_org_videolan_libvlc_LibVLC_getAudioTrack(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_audio_get_track(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_setAudioTrack(JNIEnv *env, jobject thiz, jint index)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_audio_set_track(mp, index);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_getVideoTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_video_get_track_count(mp);
    return -1;
}

jobject Java_org_videolan_libvlc_LibVLC_getSpuTrackDescription(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (!mp)
        return NULL;

    int i_nbTracks = libvlc_video_get_spu_count(mp);
    jclass mapClass = (*env)->FindClass(env, "java/util/Map");
    jclass hashMapClass = (*env)->FindClass(env, "java/util/HashMap");
    jmethodID mapPut = (*env)->GetMethodID(env, mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    /*
     * "What are you building? Lay your hand on it. Where is it?"
     * We need a concrete map to start
     */
    jmethodID mapInit = (*env)->GetMethodID(env, hashMapClass, "<init>", "()V");
    jclass integerCls = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID integerConstructor = (*env)->GetMethodID(env, integerCls, "<init>", "(I)V");

    jobject spuTrackMap = (*env)->NewObject(env, hashMapClass, mapInit);

    libvlc_track_description_t *first = libvlc_video_get_spu_description(mp);
    libvlc_track_description_t *desc = first;
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
    {
        // store audio track ID and name in a map as <ID, Track Name>
        jobject track_id = (*env)->NewObject(env, integerCls, integerConstructor, desc->i_id);
        jstring name = (*env)->NewStringUTF(env, desc->psz_name);
        (*env)->CallObjectMethod(env, spuTrackMap, mapPut, track_id, name);
        desc = desc->p_next;
    }
    libvlc_track_description_list_release(first);

    // Clean up local references
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, hashMapClass);
    (*env)->DeleteLocalRef(env, integerCls);

    return spuTrackMap;
}

jint Java_org_videolan_libvlc_LibVLC_getSpuTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_video_get_spu_count(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_getSpuTrack(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_get_spu(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_setSpuTrack(JNIEnv *env, jobject thiz, jint index)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_set_spu(mp, index);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_addSubtitleTrack(JNIEnv *env, jobject thiz, jstring path)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp) {
        jboolean isCopy;
        const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);
        jint res = libvlc_video_set_subtitle_file(mp, psz_path);
        (*env)->ReleaseStringUTFChars(env, path, psz_path);
        return res;
    } else {
        return -1;
    }
}
