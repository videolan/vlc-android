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
    jfloat arg2;
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

static inline void throw_IllegalStateException(JNIEnv *env, const char *p_error)
{
    (*env)->ThrowNew(env, fields.IllegalStateException.clazz, p_error);
}

static inline void throw_IllegalArgumentException(JNIEnv *env, const char *p_error)
{
    (*env)->ThrowNew(env, fields.IllegalArgumentException.clazz, p_error);
}

#endif // LIBVLCJNI_VLCOBJECT_H
