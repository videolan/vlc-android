/*****************************************************************************
 * libvlcjni-media.c
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
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

#include <stdlib.h>
#include <pthread.h>

#include "libvlcjni-vlcobject.h"

#define META_MAX 25

struct vlcjni_object_sys
{
    pthread_mutex_t lock;
    pthread_cond_t  wait;
    bool b_parsing_sync;
    bool b_parsing_async;
};
static const libvlc_event_type_t m_events[] = {
    libvlc_MediaMetaChanged,
    libvlc_MediaSubItemAdded,
    //libvlc_MediaFreed,
    libvlc_MediaDurationChanged,
    libvlc_MediaStateChanged,
    libvlc_MediaParsedChanged,
    libvlc_MediaSubItemTreeAdded,
    -1,
};

static bool
Media_event_cb(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
               java_event *p_java_event)
{
    vlcjni_object_sys *p_sys = p_obj->p_sys;
    bool b_dispatch = true;

    pthread_mutex_lock(&p_sys->lock);

    if (p_ev->type == libvlc_MediaParsedChanged)
    {
        /* no need to send libvlc_MediaParsedChanged when parsing is synchronous */
        if (p_sys->b_parsing_sync)
            b_dispatch = false;

        p_sys->b_parsing_sync = false;
        p_sys->b_parsing_async = false;
        pthread_cond_signal(&p_sys->wait);
    }

    /* bypass all others events while parsing, since we'll fetch alls info when
     * parsing is done */
    if (p_sys->b_parsing_sync || p_sys->b_parsing_async)
        b_dispatch = false;
    pthread_mutex_unlock(&p_sys->lock);

    if (!b_dispatch)
        return false;

    switch (p_ev->type)
    {
        case libvlc_MediaMetaChanged:

            /* XXX: libvlc_meta_ArtworkURL is sent too many time before, during
             * and after parsing, don't send event since it will be fetched
             * when parsing is done. */
            if (p_ev->u.media_meta_changed.meta_type == libvlc_meta_ArtworkURL)
                return false;
            p_java_event->arg1 = p_ev->u.media_meta_changed.meta_type;
            break;
        case libvlc_MediaDurationChanged:
            p_java_event->arg1 = p_ev->u.media_duration_changed.new_duration;
            break;
        case libvlc_MediaStateChanged:
            p_java_event->arg1 = p_ev->u.media_state_changed.new_state;
        case libvlc_MediaParsedChanged:
            p_java_event->arg1 = p_ev->u.media_parsed_changed.new_status;
    }
    p_java_event->type = p_ev->type;
    return true;
}

static void
Media_nativeNewCommon(JNIEnv *env, jobject thiz, vlcjni_object *p_obj)
{
    p_obj->p_sys = calloc(1, sizeof(vlcjni_object_sys));

    if (!p_obj->u.p_m || !p_obj->p_sys)
    {
        free(p_obj->p_sys);
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create Media instance");
        return;
    }

    pthread_mutex_init(&p_obj->p_sys->lock, NULL);
    pthread_cond_init(&p_obj->p_sys->wait, NULL);

    VLCJniObject_attachEvents(p_obj, Media_event_cb,
                              libvlc_media_event_manager(p_obj->u.p_m),
                              m_events);
}

static void
Media_nativeNewFromCb(JNIEnv *env, jobject thiz, jobject libVlc, jstring jmrl,
                      libvlc_media_t *(*pf)(libvlc_instance_t*, const char *))
{
    vlcjni_object *p_obj;
    const char* p_mrl;

    if (!jmrl || !(p_mrl = (*env)->GetStringUTFChars(env, jmrl, 0)))
    {
        throw_IllegalArgumentException(env, "path or location invalid");
        return;
    }

    p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
    {
        (*env)->ReleaseStringUTFChars(env, jmrl, p_mrl);
        return;
    }

    p_obj->u.p_m = pf(p_obj->p_libvlc, p_mrl);

    (*env)->ReleaseStringUTFChars(env, jmrl, p_mrl);

    Media_nativeNewCommon(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_Media_nativeNewFromPath(JNIEnv *env, jobject thiz,
                                                 jobject libVlc, jstring jpath)
{
    Media_nativeNewFromCb(env, thiz, libVlc, jpath, libvlc_media_new_path);
}

void
Java_org_videolan_libvlc_Media_nativeNewFromLocation(JNIEnv *env, jobject thiz,
                                                     jobject libVlc,
                                                     jstring jlocation)
{
    Media_nativeNewFromCb(env, thiz, libVlc, jlocation,
                          libvlc_media_new_location);
}

void
Java_org_videolan_libvlc_Media_nativeNewFromFd(JNIEnv *env, jobject thiz,
                                               jobject libVlc, jobject jfd)
{
    vlcjni_object *p_obj;
    int fd = (*env)->GetIntField(env, jfd, fields.FileDescriptor.descriptorID);

    if ((*env)->ExceptionOccurred(env))
    {
        (*env)->ExceptionClear(env);
        fd = -1;
    }
    if (fd == -1)
    {
        throw_IllegalArgumentException(env, "fd invalid");
        return;
    }

    p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
        return;

    p_obj->u.p_m = libvlc_media_new_fd(p_obj->p_libvlc, fd);

    Media_nativeNewCommon(env, thiz, p_obj);
}

/* MediaList must be locked */
void
Java_org_videolan_libvlc_Media_nativeNewFromMediaList(JNIEnv *env, jobject thiz,
                                                      jobject ml, jint index)
{
    vlcjni_object *p_ml_obj = VLCJniObject_getInstance(env, ml);
    vlcjni_object *p_obj;

    if (!p_ml_obj)
        return;

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_ml_obj->p_libvlc);
    if (!p_obj)
        return;

    p_obj->u.p_m = libvlc_media_list_item_at_index(p_ml_obj->u.p_ml, index);

    Media_nativeNewCommon(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_Media_nativeRelease(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    vlcjni_object_sys *p_sys;

    if (!p_obj)
        return;

    p_sys = p_obj->p_sys;

    libvlc_media_release(p_obj->u.p_m);

    pthread_mutex_destroy(&p_obj->p_sys->lock);
    pthread_cond_destroy(&p_obj->p_sys->wait);
    free(p_obj->p_sys);

    VLCJniObject_release(env, thiz, p_obj);
}

jstring
Java_org_videolan_libvlc_Media_nativeGetMrl(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    const char *psz_mrl;

    if (!p_obj)
        return NULL;

    psz_mrl = libvlc_media_get_mrl(p_obj->u.p_m);
    if (psz_mrl)
        return (*env)->NewStringUTF(env, psz_mrl);

    return NULL;
}

jint
Java_org_videolan_libvlc_Media_nativeGetState(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return libvlc_Error;

    return libvlc_media_get_state(p_obj->u.p_m);
}

jstring
Java_org_videolan_libvlc_Media_nativeGetMeta(JNIEnv *env, jobject thiz, jint id)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    jstring jmeta = NULL;

    if (!p_obj)
        return NULL;

    if (id >= 0 && id < META_MAX) {
        char *psz_media = libvlc_media_get_meta(p_obj->u.p_m, id);
        if (psz_media) {
            jmeta = (*env)->NewStringUTF(env, psz_media);
            free(psz_media);
        }
    }
    return jmeta;
}

static jobject
media_track_to_object(JNIEnv *env, libvlc_media_track_t *p_tracks)
{
    const char *psz_desc;
    jstring jcodec = NULL;
    jstring joriginalCodec = NULL;
    jstring jlanguage = NULL;
    jstring jdescription = NULL;

    psz_desc = libvlc_media_get_codec_description(p_tracks->i_type,
                                                  p_tracks->i_codec);
    if (psz_desc)
        jcodec = (*env)->NewStringUTF(env, psz_desc);

    psz_desc = libvlc_media_get_codec_description(p_tracks->i_type,
                                                  p_tracks->i_original_fourcc);
    if (psz_desc)
        joriginalCodec = (*env)->NewStringUTF(env, psz_desc);

    if (p_tracks->psz_language)
        jlanguage = (*env)->NewStringUTF(env, p_tracks->psz_language);

    if (p_tracks->psz_description)
        jdescription = (*env)->NewStringUTF(env, p_tracks->psz_description);

    switch (p_tracks->i_type)
    {
        case libvlc_track_audio:
            return (*env)->CallStaticObjectMethod(env, fields.Media.clazz,
                                fields.Media.createAudioTrackFromNativeID,
                                jcodec,
                                joriginalCodec,
                                (jint)p_tracks->i_id,
                                (jint)p_tracks->i_profile,
                                (jint)p_tracks->i_level,
                                (jint)p_tracks->i_bitrate,
                                jlanguage,
                                jdescription,
                                (jint)p_tracks->audio->i_channels,
                                (jint)p_tracks->audio->i_rate);
        case libvlc_track_video:
            return (*env)->CallStaticObjectMethod(env, fields.Media.clazz,
                                fields.Media.createVideoTrackFromNativeID,
                                jcodec,
                                joriginalCodec,
                                (jint)p_tracks->i_id,
                                (jint)p_tracks->i_profile,
                                (jint)p_tracks->i_level,
                                (jint)p_tracks->i_bitrate,
                                jlanguage,
                                jdescription,
                                (jint)p_tracks->video->i_height,
                                (jint)p_tracks->video->i_width,
                                (jint)p_tracks->video->i_sar_num,
                                (jint)p_tracks->video->i_sar_den,
                                (jint)p_tracks->video->i_frame_rate_num,
                                (jint)p_tracks->video->i_frame_rate_den);
        case libvlc_track_text: {
            jstring jencoding = NULL;

            if (p_tracks->subtitle->psz_encoding)
                jencoding = (*env)->NewStringUTF(env, p_tracks->subtitle->psz_encoding);

            return (*env)->CallStaticObjectMethod(env, fields.Media.clazz,
                                fields.Media.createSubtitleTrackFromNativeID,
                                jcodec,
                                joriginalCodec,
                                (jint)p_tracks->i_id,
                                (jint)p_tracks->i_profile,
                                (jint)p_tracks->i_level,
                                (jint)p_tracks->i_bitrate,
                                jlanguage,
                                jdescription,
                                jencoding);
        }
        case libvlc_track_unknown:
            return (*env)->CallStaticObjectMethod(env, fields.Media.clazz,
                                fields.Media.createUnknownTrackFromNativeID,
                                jcodec,
                                joriginalCodec,
                                (jint)p_tracks->i_id,
                                (jint)p_tracks->i_profile,
                                (jint)p_tracks->i_level,
                                (jint)p_tracks->i_bitrate,
                                jlanguage,
                                jdescription);
    }
}

jobject
Java_org_videolan_libvlc_Media_nativeGetTracks(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    libvlc_media_track_t **pp_tracks = NULL;
    unsigned int i_nb_tracks = 0;
    jobjectArray array;

    if (!p_obj)
        return NULL;

    i_nb_tracks = libvlc_media_tracks_get(p_obj->u.p_m, &pp_tracks);
    if (!i_nb_tracks)
        return NULL;

    array = (*env)->NewObjectArray(env, i_nb_tracks, fields.Media.Track.clazz,
                                   NULL);
    if (!array)
        goto error;

    for (int i = 0; i < i_nb_tracks; ++i)
    {
        jobject jtrack = media_track_to_object(env, pp_tracks[i]);

        (*env)->SetObjectArrayElement(env, array, i, jtrack);
    }

error:
    if (pp_tracks)
        libvlc_media_tracks_release(pp_tracks, i_nb_tracks);
    return array;
}

jboolean
Java_org_videolan_libvlc_Media_nativeParseAsync(JNIEnv *env, jobject thiz,
                                                jint flags)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    pthread_mutex_lock(&p_obj->p_sys->lock);
    p_obj->p_sys->b_parsing_async = true;
    pthread_mutex_unlock(&p_obj->p_sys->lock);

    return libvlc_media_parse_with_options(p_obj->u.p_m, flags) == 0 ? true : false;
}

jboolean
Java_org_videolan_libvlc_Media_nativeParse(JNIEnv *env, jobject thiz, jint flags)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    pthread_mutex_lock(&p_obj->p_sys->lock);
    p_obj->p_sys->b_parsing_sync = true;
    pthread_mutex_unlock(&p_obj->p_sys->lock);

    if (libvlc_media_parse_with_options(p_obj->u.p_m, flags) != 0)
        return false;

    pthread_mutex_lock(&p_obj->p_sys->lock);
    while (p_obj->p_sys->b_parsing_sync)
        pthread_cond_wait(&p_obj->p_sys->wait, &p_obj->p_sys->lock);
    pthread_mutex_unlock(&p_obj->p_sys->lock);

    return true;
}

jlong
Java_org_videolan_libvlc_Media_nativeGetDuration(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_media_get_duration(p_obj->u.p_m);
}

jint
Java_org_videolan_libvlc_Media_nativeGetType(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_media_get_type(p_obj->u.p_m);
}

void
Java_org_videolan_libvlc_Media_nativeAddOption(JNIEnv *env, jobject thiz,
                                               jstring joption)
{
    const char* p_option;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    if (!joption || !(p_option = (*env)->GetStringUTFChars(env, joption, 0)))
    {
        throw_IllegalArgumentException(env, "option invalid");
        return;
    }

    libvlc_media_add_option(p_obj->u.p_m, p_option);

    (*env)->ReleaseStringUTFChars(env, joption, p_option);
}

void
Java_org_videolan_libvlc_Media_nativeAddSlave(JNIEnv *env, jobject thiz,
                                              jint type, jint priority,
                                              jstring juri)
{
    const char *psz_uri;
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    if (!juri || !(psz_uri = (*env)->GetStringUTFChars(env, juri, 0)))
    {
        throw_IllegalArgumentException(env, "uri invalid");
        return;
    }

    int i_ret = libvlc_media_slaves_add(p_obj->u.p_m, type, priority, psz_uri);

    (*env)->ReleaseStringUTFChars(env, juri, psz_uri);
    if (i_ret != 0)
        throw_IllegalStateException(env, "can't add slaves to libvlc_media");
}

void
Java_org_videolan_libvlc_Media_nativeClearSlaves(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_slaves_clear(p_obj->u.p_m);
}

unsigned int libvlc_media_slaves_get( libvlc_media_t *p_md,
                                      libvlc_media_slave_t ***ppp_slaves );
void libvlc_media_slaves_release( libvlc_media_slave_t **pp_slaves,
                                  unsigned int i_count );
jobject
Java_org_videolan_libvlc_Media_nativeGetSlaves(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    libvlc_media_slave_t **pp_slaves;
    unsigned int i_slaves;
    jobjectArray array;

    if (!p_obj)
        return NULL;

    i_slaves = libvlc_media_slaves_get(p_obj->u.p_m, &pp_slaves);
    if (i_slaves == 0)
        return NULL;

    array = (*env)->NewObjectArray(env, i_slaves, fields.Media.Slave.clazz, NULL);
    if (array == NULL)
        goto error;

    for (unsigned int i = 0; i < i_slaves; ++i)
    {
        libvlc_media_slave_t *p_slave = pp_slaves[i];
        jstring juri = (*env)->NewStringUTF(env, p_slave->psz_uri);

        jobject jslave =
            (*env)->CallStaticObjectMethod(env, fields.Media.clazz,
                                           fields.Media.createSlaveFromNativeID,
                                           p_slave->i_type, p_slave->i_priority,
                                           juri);
        (*env)->SetObjectArrayElement(env, array, i, jslave);
    }

error:
    if (pp_slaves)
        libvlc_media_slaves_release(pp_slaves, i_slaves);
    return array;
}
