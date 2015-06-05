/*****************************************************************************
 * libvlcjni-medialist.c
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

#include <pthread.h>

#include "libvlcjni-vlcobject.h"

#define MEDIAS_INIT_SIZE 32

static const libvlc_event_type_t ml_events[] = {
    libvlc_MediaListItemAdded,
    //libvlc_MediaListWillAddItem,
    libvlc_MediaListItemDeleted,
    //libvlc_MediaListWillDeleteItem,
    libvlc_MediaListEndReached,
    -1,
};

static bool
MediaList_event_cb(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
                   java_event *p_java_event)
{
    switch (p_ev->type)
    {
        case libvlc_MediaListItemAdded:
            p_java_event->arg1 = p_ev->u.media_list_item_added.index;
            break;
        case libvlc_MediaListItemDeleted:
            p_java_event->arg1 = p_ev->u.media_list_item_deleted.index;
            break;
    }
    p_java_event->type = p_ev->type;
    return true;
}

static void
MediaList_nativeNewCommon(JNIEnv *env, jobject thiz, vlcjni_object *p_obj)
{
    VLCJniObject_attachEvents(p_obj, MediaList_event_cb,
                              libvlc_media_list_event_manager(p_obj->u.p_ml),
                              ml_events);
}

void
Java_org_videolan_libvlc_MediaList_nativeNewFromLibVlc(JNIEnv *env,
                                                       jobject thiz,
                                                       jobject libVlc)
{
    vlcjni_object *p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
        return;

    p_obj->u.p_ml = libvlc_media_list_new(p_obj->p_libvlc);

    MediaList_nativeNewCommon(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_MediaList_nativeNewFromMediaDiscoverer(JNIEnv *env,
                                                                jobject thiz,
                                                                jobject md)
{
    vlcjni_object *p_md_obj = VLCJniObject_getInstance(env, md);
    vlcjni_object *p_obj;

    if (!p_md_obj)
        return;

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_md_obj->p_libvlc);
    if (!p_obj)
        return;

    p_obj->u.p_ml = libvlc_media_discoverer_media_list(p_md_obj->u.p_md);

    MediaList_nativeNewCommon(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_MediaList_nativeNewFromMedia(JNIEnv *env,
                                                      jobject thiz,
                                                      jobject m)
{
    vlcjni_object *p_m_obj = VLCJniObject_getInstance(env, m);
    vlcjni_object *p_obj;

    if (!p_m_obj)
        return;

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_m_obj->p_libvlc);
    if (!p_obj)
        return;

    p_obj->u.p_ml = libvlc_media_subitems(p_m_obj->u.p_m);

    MediaList_nativeNewCommon(env, thiz, p_obj);
}

void
Java_org_videolan_libvlc_MediaList_nativeRelease(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_list_release(p_obj->u.p_ml);

    VLCJniObject_release(env, thiz, p_obj);
}

jint
Java_org_videolan_libvlc_MediaList_nativeGetCount(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return 0;

    return libvlc_media_list_count(p_obj->u.p_ml);
}

void
Java_org_videolan_libvlc_MediaList_nativeLock(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;
    libvlc_media_list_lock(p_obj->u.p_ml);
}

void
Java_org_videolan_libvlc_MediaList_nativeUnlock(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;
    libvlc_media_list_unlock(p_obj->u.p_ml);
}
