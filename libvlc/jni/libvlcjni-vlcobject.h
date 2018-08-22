/*****************************************************************************
 * libvlcjni-vlcobject.h
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

#ifndef LIBVLCJNI_VLCOBJECT_H
#define LIBVLCJNI_VLCOBJECT_H

#include <stdbool.h>

#include <jni.h>
#include <vlc/vlc.h>
#include <vlc/libvlc_media_list.h>
#include <vlc/libvlc_media_discoverer.h>
#include <vlc/libvlc_renderer_discoverer.h>
#include <vlc/libvlc_version.h>

#include "utils.h"
#define LOG_TAG "VLC/JNI/VLCObject"
#include "log.h"

typedef struct vlcjni_object vlcjni_object;
typedef struct vlcjni_object_owner vlcjni_object_owner;
typedef struct vlcjni_object_sys vlcjni_object_sys;
typedef struct java_event java_event;

struct vlcjni_object
{
    /* Pointer to parent libvlc: NULL if the VLCObject is a LibVLC */
    libvlc_instance_t *p_libvlc;

    /* Current pointer to native vlc object */
    union {
        libvlc_instance_t *p_libvlc;
        libvlc_media_t *p_m;
        libvlc_media_list_t *p_ml;
        libvlc_media_discoverer_t *p_md;
        libvlc_media_player_t *p_mp;
        libvlc_renderer_discoverer_t *p_rd;
        libvlc_renderer_item_t *p_r;
    } u;
    /* Used by vlcobject */
    vlcjni_object_owner *p_owner;
    /* Used by media, medialist, mediadiscoverer... */
    vlcjni_object_sys *p_sys;
};

struct java_event
{
    jint type;
    jlong arg1;
    jlong arg2;
    jfloat argf1;
};

/* event manager callback dispatched to native struct implementing a
 * vlcjni_object. If the callback returns true, the event is dispatched to Java
 * */
typedef bool (*event_cb)(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
                         java_event *p_java_event);


vlcjni_object *VLCJniObject_getInstance(JNIEnv *env, jobject thiz);

vlcjni_object *VLCJniObject_newFromJavaLibVlc(JNIEnv *env, jobject thiz,
                                              jobject libVlc);

vlcjni_object *VLCJniObject_newFromLibVlc(JNIEnv *env, jobject thiz,
                                          libvlc_instance_t *p_libvlc);

void VLCJniObject_release(JNIEnv *env, jobject thiz, vlcjni_object *p_obj);

void VLCJniObject_attachEvents(vlcjni_object *p_obj, event_cb pf_event_cb,
                               libvlc_event_manager_t *p_event_manager,
                               const int *p_events);

enum vlcjni_exception
{
    VLCJNI_EX_ILLEGAL_STATE,
    VLCJNI_EX_ILLEGAL_ARGUMENT,
    VLCJNI_EX_RUNTIME,
    VLCJNI_EX_OUT_OF_MEMORY,
};

static inline void throw_Exception(JNIEnv *env, enum vlcjni_exception type,
                                   const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);

    char *error;
    if (vasprintf(&error, fmt, args) == -1)
        error = NULL;

    jclass clazz;
    switch (type)
    {
        case VLCJNI_EX_ILLEGAL_STATE:
            clazz = fields.IllegalStateException.clazz;
            break;
        case VLCJNI_EX_RUNTIME:
            clazz = fields.RuntimeException.clazz;
            break;
        case VLCJNI_EX_OUT_OF_MEMORY:
            clazz = fields.OutOfMemoryError.clazz;
            break;
        case VLCJNI_EX_ILLEGAL_ARGUMENT:
        default:
            clazz = fields.IllegalArgumentException.clazz;
            break;
    }
    (*env)->ThrowNew(env, clazz, error ? error : fmt);

    free(error);
    va_end(args);
}

#endif // LIBVLCJNI_VLCOBJECT_H
