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
Java_org_videolan_libvlc_MediaPlayer_getChapterCount(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_chapter_count(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapterCountForTitle(JNIEnv *env,
                                                             jobject thiz,
                                                             jint title)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_chapter_count_for_title(p_obj->u.p_mp, title);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapter(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_chapter(p_obj->u.p_mp);
}

jstring
Java_org_videolan_libvlc_MediaPlayer_getChapterDescription(JNIEnv *env,
                                                           jobject thiz,
                                                           jint title)
{
    libvlc_track_description_t *description;
    jstring string = NULL;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    description = libvlc_video_get_chapter_description(p_obj->u.p_mp, title);
    if (description) {
        string = (*env)->NewStringUTF(env, description->psz_name);
        free(description);
    }
    return string;
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

jint
Java_org_videolan_libvlc_MediaPlayer_getTitleCount(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return -1;

    return libvlc_media_player_get_title_count(p_obj->u.p_mp);
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
