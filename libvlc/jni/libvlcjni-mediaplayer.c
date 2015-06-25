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

extern jobject eventHandlerInstance;

struct vlcjni_object_sys
{
    jobject jwindow;
};

/* TODO REMOVE */
static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;

    if (eventHandlerInstance == NULL)
        return;

    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    /* Creating the bundle in C allows us to subscribe to more events
     * and get better flexibility for each event. For example, we can
     * have totally different types of data for each event, instead of,
     * for example, only an integer and/or string.
     */
    jclass clsBundle = (*env)->FindClass(env, "android/os/Bundle");
    jmethodID clsCtor = (*env)->GetMethodID(env, clsBundle, "<init>", "()V" );
    jobject bundle = (*env)->NewObject(env, clsBundle, clsCtor);

    jmethodID putInt = (*env)->GetMethodID(env, clsBundle, "putInt", "(Ljava/lang/String;I)V" );
    jmethodID putLong = (*env)->GetMethodID(env, clsBundle, "putLong", "(Ljava/lang/String;J)V" );
    jmethodID putFloat = (*env)->GetMethodID(env, clsBundle, "putFloat", "(Ljava/lang/String;F)V" );
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );
    (*env)->DeleteLocalRef(env, clsBundle);

    if (ev->type == libvlc_MediaPlayerPositionChanged) {
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putFloat, sData, ev->u.media_player_position_changed.new_position);
        (*env)->DeleteLocalRef(env, sData);
    } else if (ev->type == libvlc_MediaPlayerTimeChanged) {
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putLong, sData, ev->u.media_player_time_changed.new_time);
        (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaPlayerVout) {
        /* For determining the vout/ES track change */
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putInt, sData, ev->u.media_player_vout.new_count);
        (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaListItemAdded ||
              ev->type == libvlc_MediaListItemDeleted ) {
        jstring item_uri = (*env)->NewStringUTF(env, "item_uri");
        jstring item_index = (*env)->NewStringUTF(env, "item_index");
        char* mrl = libvlc_media_get_mrl(
            ev->type == libvlc_MediaListItemAdded ?
            ev->u.media_list_item_added.item :
            ev->u.media_list_item_deleted.item
            );
        jstring item_uri_value = (*env)->NewStringUTF(env, mrl);
        jint item_index_value;
        if(ev->type == libvlc_MediaListItemAdded)
            item_index_value = ev->u.media_list_item_added.index;
        else
            item_index_value = ev->u.media_list_item_deleted.index;

        (*env)->CallVoidMethod(env, bundle, putString, item_uri, item_uri_value);
        (*env)->CallVoidMethod(env, bundle, putInt, item_index, item_index_value);

        (*env)->DeleteLocalRef(env, item_uri);
        (*env)->DeleteLocalRef(env, item_uri_value);
        (*env)->DeleteLocalRef(env, item_index);
        free(mrl);
    } else if(ev->type == libvlc_MediaPlayerESAdded ||
              ev->type == libvlc_MediaPlayerESDeleted ) {
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putInt, sData, ev->u.media_player_es_changed.i_type);
        (*env)->DeleteLocalRef(env, sData);
    }

    /* Get the object class */
    jclass cls = (*env)->GetObjectClass(env, eventHandlerInstance);
    if (!cls) {
        LOGE("EventHandler: failed to get class reference");
        goto end;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (methodID) {
        (*env)->CallVoidMethod(env, eventHandlerInstance, methodID, ev->type, bundle);
    } else {
        LOGE("EventHandler: failed to get the callback method");
    }
    (*env)->DeleteLocalRef(env, cls);

end:
    (*env)->DeleteLocalRef(env, bundle);
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
    /*
    VLCJniObject_attachEvents(p_obj, MediaPlayer_event_cb,
                              libvlc_media_event_manager(p_obj->u.p_mp),
                              m_events);
    */

    /* TODO NOT HERE */
    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(p_obj->u.p_mp);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaPlayerPlaying,
        libvlc_MediaPlayerPaused,
        libvlc_MediaPlayerEndReached,
        libvlc_MediaPlayerStopped,
        libvlc_MediaPlayerVout,
        libvlc_MediaPlayerPositionChanged,
        libvlc_MediaPlayerTimeChanged,
        libvlc_MediaPlayerEncounteredError,
        libvlc_MediaPlayerESAdded,
        libvlc_MediaPlayerESDeleted,
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, NULL);
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

    /* TODO: REMOVE */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(p_obj->u.p_mp);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaPlayerPlaying,
        libvlc_MediaPlayerPaused,
        libvlc_MediaPlayerEndReached,
        libvlc_MediaPlayerStopped,
        libvlc_MediaPlayerVout,
        libvlc_MediaPlayerPositionChanged,
        libvlc_MediaPlayerTimeChanged,
        libvlc_MediaPlayerEncounteredError,
        libvlc_MediaPlayerESAdded,
        libvlc_MediaPlayerESDeleted,
    };

    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_detach(ev, mp_events[i], vlc_event_callback, NULL);
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

    /* Connect the media event manager. */
    /* TODO use VlcObject events */
    libvlc_media_t* p_md = libvlc_media_player_get_media(p_obj->u.p_mp);
    libvlc_event_manager_t *ev_media = libvlc_media_event_manager(p_md);
    static const libvlc_event_type_t mp_media_events[] = {
        libvlc_MediaParsedChanged,
        libvlc_MediaMetaChanged,
    };
    for(int i = 0; i < (sizeof(mp_media_events) / sizeof(*mp_media_events)); i++)
        libvlc_event_attach(ev_media, mp_media_events[i], vlc_event_callback, NULL);
    libvlc_media_release(p_md);

    libvlc_media_player_play(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeStop(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    /* TODO: REMOVE */
    libvlc_media_t* p_md = libvlc_media_player_get_media(p_obj->u.p_mp);
    if (p_md)
    {
        libvlc_event_manager_t *ev_media = libvlc_media_event_manager(p_md);
        static const libvlc_event_type_t mp_media_events[] = {
            libvlc_MediaParsedChanged,
            libvlc_MediaMetaChanged,
        };
        for(int i = 0; i < (sizeof(mp_media_events) / sizeof(*mp_media_events)); i++)
            libvlc_event_detach(ev_media, mp_media_events[i], vlc_event_callback, NULL);
        libvlc_media_release(p_md);
        libvlc_media_player_set_media(p_obj->u.p_mp, NULL);
    }

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

/* TODO Remove: use MediaPlayer.GetMedia().GetMeta instead */
jstring
Java_org_videolan_libvlc_MediaPlayer_getMeta(JNIEnv *env, jobject thiz,
                                             int meta)
{
    char *psz_meta;
    jstring string = NULL;

    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return NULL;

    libvlc_media_t *p_mp = libvlc_media_player_get_media(p_obj->u.p_mp);
    if (!p_mp)
        return NULL;

    psz_meta = libvlc_media_get_meta(p_mp, meta);
    if (psz_meta) {
        string = (*env)->NewStringUTF(env, psz_meta);
        free(psz_meta);
    }
    libvlc_media_release(p_mp);
    return string;
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
Java_org_videolan_libvlc_MediaPlayer_playerNavigate(JNIEnv *env, jobject thiz,
                                                    jint navigate)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_player_navigate(p_obj->u.p_mp, (unsigned) navigate);
}
