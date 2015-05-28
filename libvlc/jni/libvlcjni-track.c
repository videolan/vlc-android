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

#include <pthread.h>

#include "libvlcjni-vlcobject.h"

jint
Java_org_videolan_libvlc_MediaPlayer_getAudioTracksCount(JNIEnv *env,
                                                         jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;;

    return (jint) libvlc_audio_get_track_count(p_obj->u.p_mp);
}

/* TODO: Don't return a HASHMAP */
jobject
Java_org_videolan_libvlc_MediaPlayer_getAudioTrackDescription(JNIEnv *env,
                                                              jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;;

    int i_nbTracks = libvlc_audio_get_track_count(p_obj->u.p_mp) - 1;
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

    libvlc_track_description_t *first = libvlc_audio_get_track_description(p_obj->u.p_mp);
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

/* TODO: Don't return a HASHMAP */
jobject
Java_org_videolan_libvlc_MediaPlayer_getStats(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;;

    libvlc_media_t *p_mp = libvlc_media_player_get_media(p_obj->u.p_mp);
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

    libvlc_media_release(p_mp);
    // Clean up local references
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, hashMapClass);
    (*env)->DeleteLocalRef(env, integerCls);
    (*env)->DeleteLocalRef(env, floatCls);

    return statistics;
}

jint
Java_org_videolan_libvlc_MediaPlayer_getAudioTrack(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_audio_get_track(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_setAudioTrack(JNIEnv *env, jobject thiz,
                                                   jint index)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_audio_set_track(p_obj->u.p_mp, index);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getVideoTracksCount(JNIEnv *env,
                                                         jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jint) libvlc_video_get_track_count(p_obj->u.p_mp);
}

/* TODO: Not in VLC API */
jint
Java_org_videolan_libvlc_MediaPlayer_setVideoTrackEnabled(JNIEnv *env,
                                                          jobject thiz,
                                                          jboolean enabled)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    if (!enabled)
        return libvlc_video_set_track(p_obj->u.p_mp, -1);
    else
    {
        int i_id = -1;
        libvlc_track_description_t *tracks, *tracks_itr;

        tracks_itr = tracks = libvlc_video_get_track_description(p_obj->u.p_mp);
        if (!tracks)
            return -1;

        while (tracks_itr)
        {
            if (tracks_itr->i_id != -1)
            {
                i_id = tracks_itr->i_id;
                break;
            }
            tracks_itr = tracks_itr->p_next;
        }
        libvlc_track_description_list_release(tracks);
        return libvlc_video_set_track(p_obj->u.p_mp, i_id);
    }
}

/* TODO: don't return HASHMAP */
jobject
Java_org_videolan_libvlc_MediaPlayer_getSpuTrackDescription(JNIEnv *env,
                                                            jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    int i_nbTracks = libvlc_video_get_spu_count(p_obj->u.p_mp);
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

    libvlc_track_description_t *first = libvlc_video_get_spu_description(p_obj->u.p_mp);
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

jint
Java_org_videolan_libvlc_MediaPlayer_getSpuTracksCount(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jint) libvlc_video_get_spu_count(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getSpuTrack(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_video_get_spu(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_setSpuTrack(JNIEnv *env, jobject thiz,
                                                 jint index)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_video_set_spu(p_obj->u.p_mp, index);
}

jint
Java_org_videolan_libvlc_MediaPlayer_addSubtitleTrack(JNIEnv *env, jobject thiz,
                                                      jstring path)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    const char* psz_path = (*env)->GetStringUTFChars(env, path, NULL);
    jint res = libvlc_video_set_subtitle_file(p_obj->u.p_mp, psz_path);
    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    return res;
}

jint
Java_org_videolan_libvlc_MediaPlayer_setAudioDelay(JNIEnv *env, jobject thiz,
                                                   jlong delay)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_audio_set_delay(p_obj->u.p_mp, (int64_t) delay);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getAudioDelay(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return (jlong) libvlc_audio_get_delay(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_setSpuDelay(JNIEnv *env, jobject thiz,
                                                 jlong delay)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_video_set_spu_delay(p_obj->u.p_mp, (int64_t) delay);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getSpuDelay(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jlong) libvlc_video_get_spu_delay(p_obj->u.p_mp);
}
