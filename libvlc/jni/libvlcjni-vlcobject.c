/*****************************************************************************
 * libvlcjni-vlcobject.c
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
#include <sys/queue.h>
#include <pthread.h>

#include "libvlcjni-vlcobject.h"

#define THREAD_NAME "VlcObject"
extern JNIEnv *jni_get_env(const char *name);

struct vlcjni_object_owner
{
    jweak weak;
    jobject weakCompat;

    libvlc_event_manager_t *p_event_manager;
    const int *p_events;

    event_cb pf_event_cb;
};

vlcjni_object *
VLCJniObject_getInstance(JNIEnv *env, jobject thiz)
{
    return (vlcjni_object*)(intptr_t) (*env)->GetLongField(env, thiz,
                                                fields.VLCObject.mInstanceID);
}

static void
VLCJniObject_setInstance(JNIEnv *env, jobject thiz, vlcjni_object *p_obj)
{
    (*env)->SetLongField(env, thiz,
                         fields.VLCObject.mInstanceID,
                         (jlong)(intptr_t)p_obj);
}

vlcjni_object *
VLCJniObject_newFromLibVlc(JNIEnv *env, jobject thiz,
                           libvlc_instance_t *p_libvlc,
                           const char **pp_error)
{
    vlcjni_object *p_obj;
    libvlc_event_manager_t *ev;

    p_obj = VLCJniObject_getInstance(env, thiz);
    if (p_obj)
    {
        *pp_error = "VLCObject.mInstanceID already exists";
        return NULL;
    }

    p_obj = calloc(1, sizeof(vlcjni_object));
    if (!p_obj)
    {
        *pp_error = "vlcjni_object calloc failed";
        goto error;
    }

    p_obj->p_owner = calloc(1, sizeof(vlcjni_object_owner));
    if (!p_obj->p_owner)
    {
        *pp_error = "vlcjni_object_owner calloc failed";
        goto error;
    }

    p_obj->p_libvlc = p_libvlc;
    libvlc_retain(p_libvlc);

    if (fields.VLCObject.getWeakReferenceID)
    {
        jobject weakCompat = (*env)->CallObjectMethod(env, thiz,
                                           fields.VLCObject.getWeakReferenceID);
        if (weakCompat)
        {
            p_obj->p_owner->weakCompat = (*env)->NewGlobalRef(env, weakCompat);
            (*env)->DeleteLocalRef(env, weakCompat);
        }
    } else
        p_obj->p_owner->weak = (*env)->NewWeakGlobalRef(env, thiz);
    if (!p_obj->p_owner->weak && !p_obj->p_owner->weakCompat)
    {
        *pp_error = "No VLCObject weak reference";
        goto error;
    }

    VLCJniObject_setInstance(env, thiz, p_obj);

    *pp_error = NULL;
    return p_obj;

error:
    VLCJniObject_release(env, thiz, p_obj);
    return NULL;
}

vlcjni_object *
VLCJniObject_newFromJavaLibVlc(JNIEnv *env, jobject thiz,
                               jobject libVlc, const char **pp_error)
{
    libvlc_instance_t *p_libvlc = getLibVlcInstance(env, libVlc);
    if (!p_libvlc)
    {
        if (libVlc)
            *pp_error = "Can't get mLibVlcInstance from libVlc";
        else
            *pp_error = "libVlc is NULL";
        return NULL;
    }
    return VLCJniObject_newFromLibVlc(env, thiz, p_libvlc, pp_error);
}

void
VLCJniObject_release(JNIEnv *env, jobject thiz, vlcjni_object *p_obj)
{
    if (p_obj)
    {
        if (p_obj->p_libvlc)
            libvlc_release(p_obj->p_libvlc);

        if (p_obj->p_owner)
        {
            if (p_obj->p_owner->weak)
                (*env)->DeleteWeakGlobalRef(env, p_obj->p_owner->weak);
            else if (p_obj->p_owner->weakCompat)
                (*env)->DeleteGlobalRef(env, p_obj->p_owner->weakCompat);
        }

        free(p_obj->p_owner);
        free(p_obj);
        VLCJniObject_setInstance(env, thiz, NULL);
    }
}

static void
VLCJniObject_eventCallback(const libvlc_event_t *ev, void *data)
{
    vlcjni_object *p_obj = data;
    java_event jevent;
    JNIEnv *env = NULL;

    jevent.type = -1;
    jevent.arg1 = jevent.arg2 = 0;

    if (!p_obj->p_owner->pf_event_cb(p_obj, ev, &jevent))
        return;

    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    if (p_obj->p_owner->weak)
        (*env)->CallVoidMethod(env, p_obj->p_owner->weak,
                               fields.VLCObject.dispatchEventFromNativeID,
                               jevent.type, jevent.arg1, jevent.arg2);
    else
        (*env)->CallStaticVoidMethod(env, fields.VLCObject.clazz,
                                     fields.VLCObject.dispatchEventFromWeakNativeID,
                                     p_obj->p_owner->weakCompat,
                                     jevent.type, jevent.arg1, jevent.arg2);
}

void
VLCJniObject_attachEvents(vlcjni_object *p_obj,
                          event_cb pf_event_cb,
                          libvlc_event_manager_t *p_event_manager,
                          const int *p_events)
{
    if (!pf_event_cb || !p_event_manager || !p_events
        || p_obj->p_owner->p_event_manager
        || p_obj->p_owner->p_events)
        return;

    p_obj->p_owner->pf_event_cb = pf_event_cb;

    p_obj->p_owner->p_event_manager = p_event_manager;
    p_obj->p_owner->p_events = p_events;

    for(int i = 0; p_obj->p_owner->p_events[i] != -1; ++i)
        libvlc_event_attach(p_obj->p_owner->p_event_manager,
                            p_obj->p_owner->p_events[i],
                            VLCJniObject_eventCallback, p_obj);
}

void
Java_org_videolan_libvlc_VLCObject_nativeDetachEvents(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj || !p_obj->p_owner->p_event_manager
        || !p_obj->p_owner->p_events)
        return;

    for(int i = 0; p_obj->p_owner->p_events[i] != -1; ++i)
        libvlc_event_detach(p_obj->p_owner->p_event_manager,
                            p_obj->p_owner->p_events[i],
                            VLCJniObject_eventCallback, p_obj);
    p_obj->p_owner->p_event_manager = NULL;
    p_obj->p_owner->p_events = NULL;
}
