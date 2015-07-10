/*****************************************************************************
 * libvlcjni-mediaplayer.c
 *****************************************************************************
 * Copyright © 2010-2015 VLC authors and VideoLAN
 *
 * Authors:     Jean-Baptiste Kempf
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

#define THREAD_NAME "libvlcjni"
JNIEnv *jni_get_env(const char *name);
extern JavaVM *libvlc_get_jvm();

static const libvlc_event_type_t mp_events[] = {
    libvlc_MediaPlayerOpening,
    libvlc_MediaPlayerPlaying,
    libvlc_MediaPlayerPaused,
    libvlc_MediaPlayerStopped,
    libvlc_MediaPlayerEndReached,
    libvlc_MediaPlayerEncounteredError,
    libvlc_MediaPlayerTimeChanged,
    libvlc_MediaPlayerPositionChanged,
    libvlc_MediaPlayerVout,
    libvlc_MediaPlayerESAdded,
    libvlc_MediaPlayerESDeleted,
    -1,
};

struct vlcjni_object_sys
{
    jobject jwindow;
};

static libvlc_equalizer_t *
Equalizer_getInstance(JNIEnv *env, jobject thiz)
{
    intptr_t i_ptr = (intptr_t)
        (*env)->GetLongField(env, thiz,
                             fields.MediaPlayer.Equalizer.mInstanceID);
    if (!i_ptr)
        throw_IllegalStateException(env, "can't get Equalizer instance");
    return (libvlc_equalizer_t*) i_ptr;
}

static void
VLCJniObject_setInstance(JNIEnv *env, jobject thiz, libvlc_equalizer_t *p_eq)
{
    (*env)->SetLongField(env, thiz,
                         fields.MediaPlayer.Equalizer.mInstanceID,
                         (jlong)(intptr_t)p_eq);
}

static bool
MediaPlayer_event_cb(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
                     java_event *p_java_event)
{
    switch (p_ev->type)
    {
        case libvlc_MediaPlayerPositionChanged:
            p_java_event->arg2 = p_ev->u.media_player_position_changed.new_position;
            break;
        case libvlc_MediaPlayerTimeChanged:
            p_java_event->arg1 = p_ev->u.media_player_time_changed.new_time;
            break;
        case libvlc_MediaPlayerVout:
            p_java_event->arg1 = p_ev->u.media_player_vout.new_count;
            break;
        case libvlc_MediaPlayerESAdded:
        case libvlc_MediaPlayerESDeleted:
            p_java_event->arg1 = p_ev->u.media_player_es_changed.i_type;
            break;
    }
    p_java_event->type = p_ev->type;
    return true;
}

static void
MediaPlayer_newCommon(JNIEnv *env, jobject thiz, vlcjni_object *p_obj,
                      jobject jwindow)
{
    p_obj->p_sys = calloc(1, sizeof(vlcjni_object_sys));

    if (!p_obj->u.p_mp || !p_obj->p_sys)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaPlayer instance");
        return;
    }
    p_obj->p_sys->jwindow = (*env)->NewGlobalRef(env, jwindow);
    if (!p_obj->p_sys->jwindow)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaPlayer instance");
        return;
    }
    libvlc_media_player_set_android_context(p_obj->u.p_mp, libvlc_get_jvm(),
                                            p_obj->p_sys->jwindow);

    VLCJniObject_attachEvents(p_obj, MediaPlayer_event_cb,
                              libvlc_media_player_event_manager(p_obj->u.p_mp),
                              mp_events);
}


void
Java_org_videolan_libvlc_MediaPlayer_nativeNewFromLibVlc(JNIEnv *env,
                                                         jobject thiz,
                                                         jobject libvlc,
                                                         jobject jwindow)
{
    vlcjni_object *p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libvlc);
    if (!p_obj)
        return;

    /* Create a media player playing environment */
    p_obj->u.p_mp = libvlc_media_player_new(p_obj->p_libvlc);
    MediaPlayer_newCommon(env, thiz, p_obj, jwindow);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeNewFromMedia(JNIEnv *env,
                                                        jobject thiz,
                                                        jobject jmedia,
                                                        jobject jwindow)
{
    vlcjni_object *p_obj;
    vlcjni_object *p_m_obj = VLCJniObject_getInstance(env, jmedia);

    if (!p_m_obj)
        return;

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_m_obj->p_libvlc);
    if (!p_obj)
        return;
    p_obj->u.p_mp = libvlc_media_player_new_from_media(p_m_obj->u.p_m);
    MediaPlayer_newCommon(env, thiz, p_obj, jwindow);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeRelease(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_release(p_obj->u.p_mp);

    if (p_obj->p_sys && p_obj->p_sys->jwindow)
        (*env)->DeleteGlobalRef(env, p_obj->p_sys->jwindow);

    free(p_obj->p_sys);

    VLCJniObject_release(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeSetMedia(JNIEnv *env,
                                                    jobject thiz,
                                                    jobject jmedia)
{
    libvlc_media_t *p_m = NULL;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    if (jmedia)
    {
        vlcjni_object *p_m_obj = VLCJniObject_getInstance(env, jmedia);

        if (!p_m_obj)
            return;
        p_m = p_m_obj->u.p_m;
    }

    libvlc_media_player_set_media(p_obj->u.p_mp, p_m);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeSetVideoTitleDisplay(JNIEnv *env,
                                                                jobject thiz,
                                                                jint jposition,
                                                                jint jtimeout)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_video_title_display(p_obj->u.p_mp, jposition,
                                                jtimeout);
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_getRate(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0.0f;

    return libvlc_media_player_get_rate(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setRate(JNIEnv *env, jobject thiz,
                                                  jfloat rate)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_rate(p_obj->u.p_mp, rate);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_isPlaying(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return !!libvlc_media_player_is_playing(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_isSeekable(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return !!libvlc_media_player_is_seekable(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativePlay(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_play(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeStop(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_stop(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_pause(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_pause(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getPlayerState(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jint) libvlc_media_player_get_state(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getVolume(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jint) libvlc_audio_get_volume(p_obj->u.p_mp);
}

/* Returns 0 if the volume was set, -1 if it was out of range or error */
jint
Java_org_videolan_libvlc_MediaPlayer_setVolume(JNIEnv *env, jobject thiz,
                                               jint volume)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jint) libvlc_audio_set_volume(p_obj->u.p_mp, (int) volume);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getTime(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_time(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setTime(JNIEnv *env, jobject thiz,
                                             jlong time)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_time(p_obj->u.p_mp, time);
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_getPosition(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jfloat) libvlc_media_player_get_position(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setPosition(JNIEnv *env, jobject thiz,
                                                 jfloat pos)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_position(p_obj->u.p_mp, pos);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getLength(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return (jlong) libvlc_media_player_get_length(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getTitle(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_title(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setTitle(JNIEnv *env, jobject thiz,
                                              jint title)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_title(p_obj->u.p_mp, title);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapter(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_chapter(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setChapter(JNIEnv *env, jobject thiz,
                                                jint chapter)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_set_chapter(p_obj->u.p_mp, chapter);
}

void
Java_org_videolan_libvlc_MediaPlayer_previousChapter(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_previous_chapter(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nextChapter(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_next_chapter(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_navigate(JNIEnv *env, jobject thiz,
                                                    jint navigate)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_navigate(p_obj->u.p_mp, (unsigned) navigate);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetAudioOutput(JNIEnv *env,
                                                          jobject thiz,
                                                          jstring jaout)
{
    const char* psz_aout;
    int i_ret;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    if (!jaout || !(psz_aout = (*env)->GetStringUTFChars(env, jaout, 0)))
    {
        throw_IllegalArgumentException(env, "aout invalid");
        return false;
    }

    i_ret = libvlc_audio_output_set(p_obj->u.p_mp, psz_aout);
    (*env)->ReleaseStringUTFChars(env, jaout, psz_aout);

    return i_ret == 0 ? true : false;
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetAudioOutputDevice(JNIEnv *env,
                                                                jobject thiz,
                                                                jstring jid)
{
    const char* psz_id;
    int i_ret;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    if (!jid || !(psz_id = (*env)->GetStringUTFChars(env, jid, 0)))
    {
        throw_IllegalArgumentException(env, "aout invalid");
        return false;
    }

    libvlc_audio_output_device_set(p_obj->u.p_mp, NULL, psz_id);
    (*env)->ReleaseStringUTFChars(env, jid, psz_id);
    return true;
}

static jobject
mediaplayer_title_to_object(JNIEnv *env, libvlc_title_description_t *p_title)
{
    jstring jname = NULL;

    if (!p_title)
        return NULL;

    if (p_title->psz_name)
        jname = (*env)->NewStringUTF(env, p_title->psz_name);

    return (*env)->CallStaticObjectMethod(env, fields.MediaPlayer.clazz,
                        fields.MediaPlayer.createTitleFromNativeID,
                        p_title->i_duration,
                        jname,
                        p_title->b_menu);
}

jobject
Java_org_videolan_libvlc_MediaPlayer_nativeGetTitles(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    libvlc_title_description_t **pp_titles = NULL;
    int i_nb_titles;
    jobjectArray array;

    if (!p_obj)
        return NULL;

    i_nb_titles = libvlc_media_player_get_full_title_descriptions(p_obj->u.p_mp,
                                                                  &pp_titles);
    if (i_nb_titles <= 0)
        return NULL;

    array = (*env)->NewObjectArray(env, i_nb_titles,
                                   fields.MediaPlayer.Title.clazz, NULL);
    if (!array)
        goto error;

    for (int i = 0; i < i_nb_titles; ++i)
    {
        jobject jtitle = mediaplayer_title_to_object(env, pp_titles[i]);

        if (jtitle)
            (*env)->SetObjectArrayElement(env, array, i, jtitle);
    }

error:
    if (pp_titles)
        libvlc_title_descriptions_release(pp_titles, i_nb_titles);
    return array;
}

static jobject
mediaplayer_chapter_to_object(JNIEnv *env,
                              libvlc_chapter_description_t *p_chapter)
{
    jstring jname = NULL;

    if (!p_chapter)
        return NULL;

    if (p_chapter->psz_name)
        jname = (*env)->NewStringUTF(env, p_chapter->psz_name);

    return (*env)->CallStaticObjectMethod(env, fields.MediaPlayer.clazz,
                        fields.MediaPlayer.createChapterFromNativeID,
                        p_chapter->i_time_offset,
                        p_chapter->i_duration,
                        jname);
}

jobject
Java_org_videolan_libvlc_MediaPlayer_nativeGetChapters(JNIEnv *env,
                                                       jobject thiz,
                                                       jint jtitle)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    libvlc_chapter_description_t **pp_chapters = NULL;
    int i_nb_chapters;
    jobjectArray array;

    if (!p_obj)
        return NULL;

    i_nb_chapters =
        libvlc_media_player_get_full_chapter_descriptions(p_obj->u.p_mp,
                                                          jtitle, &pp_chapters);
    if (i_nb_chapters <= 0)
        return NULL;

    array = (*env)->NewObjectArray(env, i_nb_chapters,
                                   fields.MediaPlayer.Chapter.clazz, NULL);
    if (!array)
        goto error;

    for (int i = 0; i < i_nb_chapters; ++i)
    {
        jobject jchapter = mediaplayer_chapter_to_object(env, pp_chapters[i]);

        if (jchapter)
            (*env)->SetObjectArrayElement(env, array, i, jchapter);
    }

error:
    if (pp_chapters)
        libvlc_chapter_descriptions_release(pp_chapters, i_nb_chapters);
    return array;
}

static jobject
mediaplayer_track_to_object(JNIEnv *env, libvlc_track_description_t *p_track)
{
    jstring jname = NULL;

    if (!p_track)
        return NULL;

    if (p_track->psz_name)
        jname = (*env)->NewStringUTF(env, p_track->psz_name);

    return (*env)->CallStaticObjectMethod(env, fields.MediaPlayer.clazz,
                        fields.MediaPlayer.createTrackDescriptionFromNativeID,
                        p_track->i_id,
                        jname);
}

static jobject
mediaplayer_tracklist_to_object(JNIEnv *env,
                                libvlc_track_description_t *p_first)
{
    int i_idx = 0;
    int i_nb_tracks = 0;
    libvlc_track_description_t *p_track = p_first;
    jobjectArray array;

    if (!p_track)
        return NULL;

    do
        i_nb_tracks++;
    while ((p_track = p_track->p_next) != NULL);

    p_track = p_first;

    array = (*env)->NewObjectArray(env, i_nb_tracks,
                                   fields.MediaPlayer.TrackDescription.clazz,
                                   NULL);
    if (!array)
        goto error;

    do
    {
        jobject jtrack = mediaplayer_track_to_object(env, p_track);

        if (jtrack)
            (*env)->SetObjectArrayElement(env, array, i_idx++, jtrack);
    }
    while ((p_track = p_track->p_next) != NULL);

error:
    libvlc_track_description_list_release(p_first);
    return array;
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetVideoTracksCount(JNIEnv *env,
                                                              jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_video_get_track_count(p_obj->u.p_mp);
}

jobject
Java_org_videolan_libvlc_MediaPlayer_nativeGetVideoTracks(JNIEnv *env,
                                                          jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    return mediaplayer_tracklist_to_object(env,
                libvlc_video_get_track_description(p_obj->u.p_mp));
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetVideoTrack(JNIEnv *env,
                                                         jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -2;

    return libvlc_video_get_track(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetVideoTrack(JNIEnv *env,
                                                         jobject thiz,
                                                         jint index)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_video_set_track(p_obj->u.p_mp, index) == 0 ? true : false;
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetAudioTracksCount(JNIEnv *env,
                                                               jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_audio_get_track_count(p_obj->u.p_mp);
}

jobject
Java_org_videolan_libvlc_MediaPlayer_nativeGetAudioTracks(JNIEnv *env,
                                                          jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    return mediaplayer_tracklist_to_object(env,
                libvlc_audio_get_track_description(p_obj->u.p_mp));
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetAudioTrack(JNIEnv *env,
                                                         jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -2;

    return libvlc_audio_get_track(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetAudioTrack(JNIEnv *env,
                                                         jobject thiz,
                                                         jint index)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_audio_set_track(p_obj->u.p_mp, index) == 0 ? true : false;
}

jlong
Java_org_videolan_libvlc_MediaPlayer_nativeGetAudioDelay(JNIEnv *env,
                                                         jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_audio_get_delay(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetAudioDelay(JNIEnv *env,
                                                         jobject thiz,
                                                         jlong delay)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_audio_set_delay(p_obj->u.p_mp, delay) == 0 ? true : false;
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetSpuTracksCount(JNIEnv *env,
                                                             jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_video_get_spu_count(p_obj->u.p_mp);
}

jobject
Java_org_videolan_libvlc_MediaPlayer_nativeGetSpuTracks(JNIEnv *env,
                                                        jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    return mediaplayer_tracklist_to_object(env,
                libvlc_video_get_spu_description(p_obj->u.p_mp));
}

jint
Java_org_videolan_libvlc_MediaPlayer_nativeGetSpuTrack(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -2;

    return libvlc_video_get_spu(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetSpuTrack(JNIEnv *env,
                                                       jobject thiz, jint index)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_video_set_spu(p_obj->u.p_mp, index) == 0 ? true : false;
}

jlong
Java_org_videolan_libvlc_MediaPlayer_nativeGetSpuDelay(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_video_get_spu_delay(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetSpuDelay(JNIEnv *env,
                                                       jobject thiz,
                                                       jlong delay)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
       return false;

    return libvlc_video_set_spu_delay(p_obj->u.p_mp, delay) == 0 ? true : false;
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetSubtitleFile(JNIEnv *env,
                                                           jobject thiz,
                                                           jstring jpath)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    const char* psz_path;

    if (!jpath || !(psz_path = (*env)->GetStringUTFChars(env, jpath, 0)))
    {
        throw_IllegalArgumentException(env, "path invalid");
        return false;
    }

    if (!p_obj)
        return false;

    jboolean ret = libvlc_video_set_subtitle_file(p_obj->u.p_mp, psz_path);

    (*env)->ReleaseStringUTFChars(env, jpath, psz_path);
    return ret;
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_nativeSetEqualizer(JNIEnv *env,
                                                        jobject thiz,
                                                        jobject jequalizer)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    libvlc_equalizer_t *p_eq = NULL;

    if (!p_obj)
       return false;

    if (jequalizer)
    {
        p_eq = Equalizer_getInstance(env, jequalizer);
        if (!p_eq)
            return false;
    }

    return libvlc_media_player_set_equalizer(p_obj->u.p_mp, p_eq) == 0 ? true: false;
}

jint
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetPresetCount(JNIEnv *env,
                                                                         jobject thiz)
{
    return libvlc_audio_equalizer_get_preset_count();
}

jstring
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetPresetName(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jint index)
{
    const char *psz_name;

    if (index < 0)
    {
        throw_IllegalArgumentException(env, "index invalid");
        return NULL;
    }

    psz_name = libvlc_audio_equalizer_get_preset_name(index);

    return psz_name ? (*env)->NewStringUTF(env, psz_name) : NULL;

}

jint
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetBandCount(JNIEnv *env,
                                                                       jobject thiz)
{
    return libvlc_audio_equalizer_get_band_count();
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetBandFrequency(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jint index)
{
    if (index < 0)
    {
        throw_IllegalArgumentException(env, "index invalid");
        return 0.0;
    }

    return libvlc_audio_equalizer_get_band_frequency(index);
}

void
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeNew(JNIEnv *env,
                                                              jobject thiz)
{
    libvlc_equalizer_t *p_eq = libvlc_audio_equalizer_new();
    if (!p_eq)
        throw_IllegalStateException(env, "can't create Equalizer instance");

    VLCJniObject_setInstance(env, thiz, p_eq);
}

void
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeNewFromPreset(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jint index)
{
    libvlc_equalizer_t *p_eq = libvlc_audio_equalizer_new_from_preset(index);
    if (!p_eq)
        throw_IllegalStateException(env, "can't create Equalizer instance");

    VLCJniObject_setInstance(env, thiz, p_eq);
}

void
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeRelease(JNIEnv *env,
                                                                  jobject thiz)
{
    libvlc_equalizer_t *p_eq = Equalizer_getInstance(env, thiz);
    if (!p_eq)
        return;

    libvlc_audio_equalizer_release(p_eq);
    VLCJniObject_setInstance(env, thiz, NULL);
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetPreAmp(JNIEnv *env,
                                                                    jobject thiz)
{
    libvlc_equalizer_t *p_eq = Equalizer_getInstance(env, thiz);
    if (!p_eq)
        return 0.0;

    return libvlc_audio_equalizer_get_preamp(p_eq);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeSetPreAmp(JNIEnv *env,
                                                                    jobject thiz,
                                                                    jfloat preamp)
{
    libvlc_equalizer_t *p_eq = Equalizer_getInstance(env, thiz);
    if (!p_eq)
        return false;

    return libvlc_audio_equalizer_set_preamp(p_eq, preamp) == 0 ? true : false;
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeGetAmp(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jint index)
{
    libvlc_equalizer_t *p_eq = Equalizer_getInstance(env, thiz);
    if (!p_eq)
        return 0.0;

    return libvlc_audio_equalizer_get_amp_at_index(p_eq, index);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_00024Equalizer_nativeSetAmp(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jint index,
                                                                 jfloat amp)
{
    libvlc_equalizer_t *p_eq = Equalizer_getInstance(env, thiz);
    if (!p_eq)
        return false;

    return libvlc_audio_equalizer_set_amp_at_index(p_eq, amp, index) == 0 ? true : false;
}
