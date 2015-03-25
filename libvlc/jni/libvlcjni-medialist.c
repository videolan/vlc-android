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

struct vlcjni_object_sys
{
    pthread_mutex_t lock;
    libvlc_media_t **pp_medias;
    unsigned int i_medias_size;
    unsigned int i_medias_max;
};

static const libvlc_event_type_t ml_events[] = {
    libvlc_MediaListItemAdded,
    //libvlc_MediaListWillAddItem,
    libvlc_MediaListItemDeleted,
    //libvlc_MediaListWillDeleteItem,
    libvlc_MediaListEndReached,
    -1,
};

static int
MediaList_add_media(vlcjni_object *p_obj, int index, libvlc_media_t *p_m)
{
    vlcjni_object_sys *p_sys = p_obj->p_sys;
    unsigned int i_new_medias_size;

    pthread_mutex_lock(&p_sys->lock);

    i_new_medias_size = p_sys->i_medias_size + 1;

    // realloc
    if (i_new_medias_size > p_sys->i_medias_max)
    {
        libvlc_media_t **pp_new_medias;
        unsigned int i_new_medias_max = p_sys->i_medias_max + MEDIAS_INIT_SIZE;

        pp_new_medias = realloc(p_sys->pp_medias,
                                i_new_medias_max * sizeof(libvlc_media_t *));
        if (!pp_new_medias)
        {
            pthread_mutex_unlock(&p_sys->lock);
            return -1;
        }
        p_sys->i_medias_max = i_new_medias_max;
        p_sys->pp_medias = pp_new_medias;
    }

    // move in case of insert
    if (index != p_sys->i_medias_size)
    {
        memmove(&p_sys->pp_medias[index + 1],
                &p_sys->pp_medias[index],
                p_sys->i_medias_size - index * sizeof(libvlc_media_t *));
    }
    p_sys->pp_medias[index] = p_m;
    p_sys->i_medias_size = i_new_medias_size;

    pthread_mutex_unlock(&p_sys->lock);
    return 0;
}

static void
MediaList_remove_media(vlcjni_object *p_obj, int index, libvlc_media_t *p_md)
{
    vlcjni_object_sys *p_sys = p_obj->p_sys;

    pthread_mutex_lock(&p_sys->lock);
    if (index < p_sys->i_medias_size - 1)
    {
        memmove(&p_sys->pp_medias[index],
                &p_sys->pp_medias[index + 1],
                (p_sys->i_medias_size - index - 1) * sizeof(libvlc_media_t *));
    }
    p_sys->i_medias_size--;
    pthread_mutex_unlock(&p_sys->lock);
}

libvlc_media_t *
MediaList_get_media(vlcjni_object *p_obj, int index)
{
    vlcjni_object_sys *p_sys = p_obj->p_sys;
    libvlc_media_t *p_m = NULL;

    pthread_mutex_lock(&p_sys->lock);
    if (index >= 0 && index < p_sys->i_medias_size)
    {
        p_m = p_sys->pp_medias[index];
        libvlc_media_retain(p_m);
    }
    pthread_mutex_unlock(&p_sys->lock);
    return p_m;
}

static bool
MediaList_event_cb(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
                   java_event *p_java_event)
{
    int index;
    switch (p_ev->type)
    {
        case libvlc_MediaListItemAdded:
            index = p_ev->u.media_list_item_added.index;
            if (MediaList_add_media(p_obj, index,
                                    p_ev->u.media_list_item_added.item) == -1)
                return false;
            p_java_event->arg1 = index;
            break;
        case libvlc_MediaListItemDeleted:
            index = p_ev->u.media_list_item_deleted.index;
            MediaList_remove_media(p_obj, index,
                                   p_ev->u.media_list_item_deleted.item);
            p_java_event->arg1 = index;
            break;
    }
    p_java_event->type = p_ev->type;
    return true;
}

static void
MediaList_nativeNewCommon(JNIEnv *env, jobject thiz, vlcjni_object *p_obj)
{
    p_obj->p_sys = calloc(1, sizeof(vlcjni_object_sys));

    if (!p_obj->u.p_ml || !p_obj->p_sys)
    {
        free(p_obj->p_sys);
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaList instance");
        return;
    }
    pthread_mutex_init(&p_obj->p_sys->lock, NULL);

    VLCJniObject_attachEvents(p_obj, MediaList_event_cb,
                              libvlc_media_list_event_manager(p_obj->u.p_ml),
                              ml_events);
}

void
Java_org_videolan_libvlc_MediaList_nativeNewFromLibVlc(JNIEnv *env,
                                                       jobject thiz,
                                                       jobject libVlc)
{
    const char *p_error;

    vlcjni_object *p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc,
                                                          &p_error);

    if (!p_obj)
    {
        throw_IllegalStateException(env, p_error);
        return;
    }

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
    const char *p_error;

    if (!p_md_obj)
    {
        throw_IllegalStateException(env, "can't get MediaDiscoverer instance");
        return;
    }

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_md_obj->p_libvlc, &p_error);
    if (!p_obj)
    {
        throw_IllegalStateException(env, p_error);
        return;
    }

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
    const char *p_error;

    if (!p_m_obj)
    {
        throw_IllegalStateException(env, "can't get Media instance");
        return;
    }

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, p_m_obj->p_libvlc, &p_error);
    if (!p_obj)
    {
        throw_IllegalStateException(env, p_error);
        return;
    }

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

    pthread_mutex_destroy(&p_obj->p_sys->lock);
    free(p_obj->p_sys->pp_medias);
    free(p_obj->p_sys);

    VLCJniObject_release(env, thiz, p_obj);
}

jint
Java_org_videolan_libvlc_MediaList_nativeGetCount(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    jint count;

    if (!p_obj)
    {
        throw_IllegalStateException(env, "can't get MediaList instance");
        return 0;
    }

    libvlc_media_list_lock(p_obj->u.p_ml);
    count = libvlc_media_list_count(p_obj->u.p_ml);
    libvlc_media_list_unlock(p_obj->u.p_ml);
    return count;
}
