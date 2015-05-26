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

#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <vlc/vlc.h>
#include <vlc_common.h>
#include <vlc_url.h>

#include <jni.h>

#include <android/api-level.h>

#include "vout.h"
#include "utils.h"
#include "std_logger.h"

#define LOG_TAG "VLC/JNI/mediaplayer"
#include "log.h"

#define VLC_JNI_VERSION JNI_VERSION_1_2

#define THREAD_NAME "libvlcjni"
JNIEnv *jni_get_env(const char *name);

libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_player_t*)(intptr_t)getLong(env, thiz, "mInternalMediaPlayerInstance");
}


void releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t* p_mp = getMediaPlayer(env, thiz);
    if (p_mp)
    {
        libvlc_media_player_stop(p_mp);
        libvlc_media_player_release(p_mp);
        setLong(env, thiz, "mInternalMediaPlayerInstance", 0);
    }
}

static jobject eventHandlerInstance = NULL;

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

void Java_org_videolan_libvlc_MediaPlayer_playMRL(JNIEnv *env, jobject thiz,
                                             jstring mrl, jobjectArray mediaOptions)
{
    jclass cls;
    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    libvlc_instance_t *p_instance = getLibVlcInstance(env, thiz);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new(p_instance);
    libvlc_media_player_set_video_title_display(mp, libvlc_position_disable, 0);
    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz); // freed in aout_close

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
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

    /* Keep a pointer to this media player */
    setLong(env, thiz, "mInternalMediaPlayerInstance", (jlong)(intptr_t)mp);

    cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodID = (*env)->GetMethodID(env, cls, "applyEqualizer", "()V");
    (*env)->CallVoidMethod(env, thiz, methodID);

    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, 0);

    libvlc_media_t* p_md = libvlc_media_new_location(p_instance, p_mrl);
    /* media options */
    if (mediaOptions != NULL)
        add_media_options(p_md, env, mediaOptions);

    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);

    /* Connect the media event manager. */
    libvlc_event_manager_t *ev_media = libvlc_media_event_manager(p_md);
    static const libvlc_event_type_t mp_media_events[] = {
        libvlc_MediaParsedChanged,
        libvlc_MediaMetaChanged,
    };
    for(int i = 0; i < (sizeof(mp_media_events) / sizeof(*mp_media_events)); i++)
        libvlc_event_attach(ev_media, mp_media_events[i], vlc_event_callback, NULL);

    libvlc_media_player_set_media(mp, p_md);
    libvlc_media_player_play(mp);
}

jfloat Java_org_videolan_libvlc_MediaPlayer_getRate(JNIEnv *env, jobject thiz) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        return libvlc_media_player_get_rate(mp);
    else
        return 1.00;
}

void Java_org_videolan_libvlc_MediaPlayer_setRate(JNIEnv *env, jobject thiz, jfloat rate) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        libvlc_media_player_set_rate(mp, rate);
}

jboolean Java_org_videolan_libvlc_MediaPlayer_isPlaying(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_playing(mp);
    else
        return 0;
}

jboolean Java_org_videolan_libvlc_MediaPlayer_isSeekable(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_seekable(mp);
    return 0;
}

void Java_org_videolan_libvlc_MediaPlayer_play(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_play(mp);
}

void Java_org_videolan_libvlc_MediaPlayer_pause(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_pause(mp);
}

void Java_org_videolan_libvlc_MediaPlayer_stop(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_stop(mp);
}

jint Java_org_videolan_libvlc_MediaPlayer_getPlayerState(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_media_player_get_state(mp);
    return -1;
}

jint Java_org_videolan_libvlc_MediaPlayer_getVolume(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_volume(mp);
    return -1;
}

jint Java_org_videolan_libvlc_MediaPlayer_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume(mp, (int) volume);
    return -1;
}

jlong Java_org_videolan_libvlc_MediaPlayer_getTime(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_time(mp);
    return -1;
}

void Java_org_videolan_libvlc_MediaPlayer_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_time(mp, time);
}

jfloat Java_org_videolan_libvlc_MediaPlayer_getPosition(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jfloat) libvlc_media_player_get_position(mp);
    return -1;
}

void Java_org_videolan_libvlc_MediaPlayer_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_position(mp, pos);
}

jlong Java_org_videolan_libvlc_MediaPlayer_getLength(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_media_player_get_length(mp);
    return -1;
}

jstring Java_org_videolan_libvlc_MediaPlayer_getMeta(JNIEnv *env, jobject thiz, int meta)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    char *psz_meta;
    jstring string = NULL;
    if (!mp)
        return NULL;

    libvlc_media_t *p_mp = libvlc_media_player_get_media(mp);
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

jint Java_org_videolan_libvlc_MediaPlayer_getTitle(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_title(mp);
    return -1;
}

void Java_org_videolan_libvlc_MediaPlayer_setTitle(JNIEnv *env, jobject thiz, jint title)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_title(mp, title);
}

jint Java_org_videolan_libvlc_MediaPlayer_getChapterCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_chapter_count(mp);
    return -1;
}

jint Java_org_videolan_libvlc_MediaPlayer_getChapterCountForTitle(JNIEnv *env, jobject thiz, jint title)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_chapter_count_for_title(mp, title);
    return -1;
}

jint Java_org_videolan_libvlc_MediaPlayer_getChapter(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_chapter(mp);
    return -1;
}

jstring Java_org_videolan_libvlc_MediaPlayer_getChapterDescription(JNIEnv *env, jobject thiz, jint title)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    libvlc_track_description_t *description;
    jstring string = NULL;
    if (!mp)
        return NULL;
    description = libvlc_video_get_chapter_description(mp, title);
    if (description) {
        string = (*env)->NewStringUTF(env, description->psz_name);
        free(description);
    }
    return string;
}

void Java_org_videolan_libvlc_MediaPlayer_setChapter(JNIEnv *env, jobject thiz, jint chapter)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_chapter(mp, chapter);
}

void Java_org_videolan_libvlc_MediaPlayer_previousChapter(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_previous_chapter(mp);
}

void Java_org_videolan_libvlc_MediaPlayer_nextChapter(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_next_chapter(mp);
}

jint Java_org_videolan_libvlc_MediaPlayer_getTitleCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_title_count(mp);
    return -1;
}

void Java_org_videolan_libvlc_MediaPlayer_playerNavigate(JNIEnv *env, jobject thiz, jint navigate)
{
    unsigned nav = navigate;
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_navigate(mp, (unsigned) nav);
}

static int expand_media_internal(JNIEnv *env, libvlc_instance_t* p_instance, jobject arrayList, libvlc_media_t* p_md) {
    if(!p_md) {
        return -1;
    }
    libvlc_media_list_t* p_subitems = libvlc_media_subitems(p_md);
    libvlc_media_release(p_md);
    if(p_subitems) {
        // Expand any subitems if needed
        int subitem_count = libvlc_media_list_count(p_subitems);
        if(subitem_count > 0) {
            LOGD("Found %d subitems, expanding", subitem_count);
            jclass arrayListClass; jmethodID methodAdd;
            arrayListGetIDs(env, &arrayListClass, &methodAdd, NULL);

            for(int i = subitem_count - 1; i >= 0; i--) {
                libvlc_media_t* p_subitem = libvlc_media_list_item_at_index(p_subitems, i);
                char* p_subitem_uri = libvlc_media_get_mrl(p_subitem);
                arrayListStringAdd(env, arrayListClass, methodAdd, arrayList, p_subitem_uri);
                free(p_subitem_uri);
            }
        }
        libvlc_media_list_release(p_subitems);
        if(subitem_count > 0) {
            return 0;
        } else {
            return -1;
        }
    } else {
        return -1;
    }
}

jint Java_org_videolan_libvlc_MediaPlayer_expandMedia(JNIEnv *env, jobject thiz, jobject children) {
    jint ret;
    libvlc_media_t *p_md = libvlc_media_player_get_media(getMediaPlayer(env, thiz));

    if (!p_md)
        return -1;
    ret = (jint)expand_media_internal(env,
        getLibVlcInstance(env, thiz),
        children,
        p_md);
    libvlc_media_release(p_md);
    return ret;
}
