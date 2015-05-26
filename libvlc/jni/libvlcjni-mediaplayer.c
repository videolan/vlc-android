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

static jobject eventHandlerInstance = NULL;

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

void
Java_org_videolan_libvlc_MediaPlayer_nativeNewFromLibVlc(JNIEnv *env,
                                                         jobject thiz,
                                                         jobject libvlc)
{
    const char *p_error;
    vlcjni_object *p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libvlc,
                                                          &p_error);

    if (!p_obj)
    {
        throw_IllegalStateException(env, p_error);
        return;
    }

    /* Create a media player playing environment */
    p_obj->u.p_mp = libvlc_media_player_new(p_obj->p_libvlc);
    if (!p_obj->u.p_mp)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaPlayer instance");
        return;
    }
    libvlc_media_player_set_video_title_display(p_obj->u.p_mp,
                                                libvlc_position_disable, 0);

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

/* TODO: NOT IN VLC API */
void
Java_org_videolan_libvlc_MediaPlayer_nativePlayMRL(JNIEnv *env, jobject thiz,
                                                   jstring mrl,
                                                   jobjectArray mediaOptions)
{
    GET_INSTANCE(p_obj)

    /* New Media */
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, 0);
    libvlc_media_t* p_md = libvlc_media_new_location(p_obj->p_libvlc, p_mrl);

    /* media options */
    if (mediaOptions != NULL)
        add_media_options(p_md, env, mediaOptions);

    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);

    /* Connect the media event manager. */
    /* TODO use VlcObject events */
    libvlc_event_manager_t *ev_media = libvlc_media_event_manager(p_md);
    static const libvlc_event_type_t mp_media_events[] = {
        libvlc_MediaParsedChanged,
        libvlc_MediaMetaChanged,
    };
    for(int i = 0; i < (sizeof(mp_media_events) / sizeof(*mp_media_events)); i++)
        libvlc_event_attach(ev_media, mp_media_events[i], vlc_event_callback, NULL);

    libvlc_media_player_set_media(p_obj->u.p_mp, p_md);
    libvlc_media_release(p_md);

    libvlc_media_player_play(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nativeRelease(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_release(p_obj->u.p_mp);

    VLCJniObject_release(env, thiz, p_obj);
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_getRate(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, 0.0f)

    return libvlc_media_player_get_rate(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setRate(JNIEnv *env, jobject thiz,
                                                  jfloat rate)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_set_rate(p_obj->u.p_mp, rate);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_isPlaying(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, false)

    return !!libvlc_media_player_is_playing(p_obj->u.p_mp);
}

jboolean
Java_org_videolan_libvlc_MediaPlayer_isSeekable(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, false)

    return !!libvlc_media_player_is_seekable(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_play(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_play(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_pause(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_pause(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_stop(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_stop(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getPlayerState(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return (jint) libvlc_media_player_get_state(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getVolume(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return (jint) libvlc_audio_get_volume(p_obj->u.p_mp);
}

/* Returns 0 if the volume was set, -1 if it was out of range or error */
jint
Java_org_videolan_libvlc_MediaPlayer_setVolume(JNIEnv *env, jobject thiz,
                                               jint volume)
{
    GET_INSTANCE_RET(p_obj, -1)

    return (jint) libvlc_audio_set_volume(p_obj->u.p_mp, (int) volume);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getTime(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_time(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setTime(JNIEnv *env, jobject thiz,
                                             jlong time)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_set_time(p_obj->u.p_mp, time);
}

jfloat
Java_org_videolan_libvlc_MediaPlayer_getPosition(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return (jfloat) libvlc_media_player_get_position(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setPosition(JNIEnv *env, jobject thiz,
                                                 jfloat pos)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_set_position(p_obj->u.p_mp, pos);
}

jlong
Java_org_videolan_libvlc_MediaPlayer_getLength(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return (jlong) libvlc_media_player_get_length(p_obj->u.p_mp);
}

/* TODO Remove: use MediaPlayer.GetMedia().GetMeta instead */
jstring
Java_org_videolan_libvlc_MediaPlayer_getMeta(JNIEnv *env, jobject thiz,
                                             int meta)
{
    char *psz_meta;
    jstring string = NULL;

    GET_INSTANCE_RET(p_obj, NULL)

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
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_title(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_setTitle(JNIEnv *env, jobject thiz,
                                              jint title)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_set_title(p_obj->u.p_mp, title);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapterCount(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_chapter_count(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapterCountForTitle(JNIEnv *env,
                                                             jobject thiz,
                                                             jint title)
{
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_chapter_count_for_title(p_obj->u.p_mp, title);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getChapter(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_chapter(p_obj->u.p_mp);
}

jstring
Java_org_videolan_libvlc_MediaPlayer_getChapterDescription(JNIEnv *env,
                                                           jobject thiz,
                                                           jint title)
{
    libvlc_track_description_t *description;
    jstring string = NULL;

    GET_INSTANCE_RET(p_obj, NULL)

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
    GET_INSTANCE(p_obj)

    libvlc_media_player_set_chapter(p_obj->u.p_mp, chapter);
}

void
Java_org_videolan_libvlc_MediaPlayer_previousChapter(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_previous_chapter(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_nextChapter(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_next_chapter(p_obj->u.p_mp);
}

jint
Java_org_videolan_libvlc_MediaPlayer_getTitleCount(JNIEnv *env, jobject thiz)
{
    GET_INSTANCE_RET(p_obj, -1)

    return libvlc_media_player_get_title_count(p_obj->u.p_mp);
}

void
Java_org_videolan_libvlc_MediaPlayer_playerNavigate(JNIEnv *env, jobject thiz,
                                                    jint navigate)
{
    GET_INSTANCE(p_obj)

    libvlc_media_player_navigate(p_obj->u.p_mp, (unsigned) navigate);
}

/* TODO REMOVE */
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

/* TODO REMOVE */
jint
Java_org_videolan_libvlc_MediaPlayer_expandMedia(JNIEnv *env, jobject thiz,
                                                 jobject children)
{
    jint ret;
    libvlc_media_t *p_md;

    GET_INSTANCE_RET(p_obj, -1)

    p_md = libvlc_media_player_get_media(p_obj->u.p_mp);
    if (!p_md)
        return -1;

    ret = (jint)expand_media_internal(env,
        p_obj->p_libvlc,
        children,
        p_md);
    libvlc_media_release(p_md);
    return ret;
}
