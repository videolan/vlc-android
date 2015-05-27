/*****************************************************************************
 * libvlcjni-mediadiscoverer.c
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

#include "libvlcjni-vlcobject.h"

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeNew(JNIEnv *env,
                                                   jobject thiz, jobject libVlc,
                                                   jstring jname)
{
    vlcjni_object *p_obj;
    const char* p_name;

    if (!jname || !(p_name = (*env)->GetStringUTFChars(env, jname, 0)))
    {
        throw_IllegalArgumentException(env, "jname invalid");
        return;
    }

    p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
    {
        (*env)->ReleaseStringUTFChars(env, jname, p_name);
        return;
    }

    p_obj->u.p_md = libvlc_media_discoverer_new(p_obj->p_libvlc, p_name);

    (*env)->ReleaseStringUTFChars(env, jname, p_name);

    if (!p_obj->u.p_md)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaDiscoverer instance");
        return;
    }
}

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeRelease(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_discoverer_release(p_obj->u.p_md);

    VLCJniObject_release(env, thiz, p_obj);
}

jboolean
Java_org_videolan_libvlc_MediaDiscoverer_nativeStart(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_media_discoverer_start(p_obj->u.p_md) == 0 ? true : false;
}

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeStop(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_discoverer_stop(p_obj->u.p_md);
}
