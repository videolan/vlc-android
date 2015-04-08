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
                                                       jstring fileLocation)
{
    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = new_media(env, thiz, fileLocation, false, false);
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

jobject Java_org_videolan_libvlc_LibVLC_getStats(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (!mp)
        return NULL;

    libvlc_media_t *p_mp = libvlc_media_player_get_media(mp);
    if (!p_mp)
        return NULL;

    libvlc_media_stats_t p_stats;
    libvlc_media_get_stats(p_mp, &p_stats);

    jclass mapClass = (*env)->FindClass(env, "java/util/Map");
    jclass hashMapClass = (*env)->FindClass(env, "java/util/HashMap");
    jmethodID mapPut = (*env)->GetMethodID(env, mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    /* We need a concrete map to start */
    jmethodID mapInit = (*env)->GetMethodID(env, hashMapClass, "<init>", "()V");
    jclass integerCls = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID integerConstructor = (*env)->GetMethodID(env, integerCls, "<init>", "(I)V");
    jclass floatCls = (*env)->FindClass(env, "java/lang/Float");
    jmethodID floatConstructor = (*env)->GetMethodID(env, floatCls, "<init>", "(F)V");

    jobject statistics = (*env)->NewObject(env, hashMapClass, mapInit);
    jobject value = (*env)->NewObject(env, floatCls, floatConstructor, p_stats.f_demux_bitrate);
    jstring name = (*env)->NewStringUTF(env, "demuxBitrate");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, floatCls, floatConstructor, p_stats.f_input_bitrate);
    name = (*env)->NewStringUTF(env, "inputBitrate");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, floatCls, floatConstructor, p_stats.f_send_bitrate);
    name = (*env)->NewStringUTF(env, "sendBitrate");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_decoded_audio);
    name = (*env)->NewStringUTF(env, "decodedAudio");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_decoded_video);
    name = (*env)->NewStringUTF(env, "decodedVideo");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_demux_corrupted);
    name = (*env)->NewStringUTF(env, "demuxCorrupted");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_demux_discontinuity);
    name = (*env)->NewStringUTF(env, "demuxDiscontinuity");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_demux_read_bytes);
    name = (*env)->NewStringUTF(env, "demuxReadBytes");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_displayed_pictures);
    name = (*env)->NewStringUTF(env, "displayedPictures");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_lost_abuffers);
    name = (*env)->NewStringUTF(env, "lostAbuffers");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_lost_pictures);
    name = (*env)->NewStringUTF(env, "lostPictures");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_played_abuffers);
    name = (*env)->NewStringUTF(env, "playedAbuffers");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_read_bytes);
    name = (*env)->NewStringUTF(env, "readBytes");
    (*env)->CallObjectMethod(env, statistics, mapPut, value, name);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_sent_bytes);
    name = (*env)->NewStringUTF(env, "sentBytes");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    value = (*env)->NewObject(env, integerCls, integerConstructor, p_stats.i_sent_packets);
    name = (*env)->NewStringUTF(env, "sentPackets");
    (*env)->CallObjectMethod(env, statistics, mapPut, name, value);

    // Clean up local references
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, hashMapClass);
    (*env)->DeleteLocalRef(env, integerCls);
    (*env)->DeleteLocalRef(env, floatCls);

    return statistics;
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

jint Java_org_videolan_libvlc_LibVLC_setVideoTrack(JNIEnv *env, jobject thiz, jint index)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_set_track(mp, index);
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

jint Java_org_videolan_libvlc_LibVLC_setAudioDelay(JNIEnv *env, jobject thiz, jlong delay)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_audio_set_delay(mp, (int64_t) delay);
    return -1;
}

jlong Java_org_videolan_libvlc_LibVLC_getAudioDelay(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_audio_get_delay(mp);
    return 0;
}

jint Java_org_videolan_libvlc_LibVLC_setSpuDelay(JNIEnv *env, jobject thiz, jlong delay)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_set_spu_delay(mp, (int64_t) delay);
    return -1;
}

jlong Java_org_videolan_libvlc_LibVLC_getSpuDelay(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_video_get_spu_delay(mp);
    return 0;
}
