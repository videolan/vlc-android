/*****************************************************************************
 * libvlcjni-medialist.c
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
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
#include <jni.h>
#include <vlc/vlc.h>
#include <vlc/libvlc_media_list.h>
#include <stdlib.h>
#include <pthread.h>

#include "utils.h"
#define LOG_TAG "VLC/JNI/MediaList"
#include "log.h"

struct stopped_monitor {
    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
    bool stopped;
};

static void stopped_callback(const libvlc_event_t *ev, void *data)
{
    struct stopped_monitor* monitor = data;
    pthread_mutex_lock(&monitor->doneMutex);
    monitor->stopped = true;
    pthread_cond_signal(&monitor->doneCondVar);
    pthread_mutex_unlock(&monitor->doneMutex);
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

jint Java_org_videolan_libvlc_LibVLC_expandMedia(JNIEnv *env, jobject thiz, jobject children) {
    return (jint)expand_media_internal(env,
        getLibVlcInstance(env, thiz),
        children,
        (libvlc_media_t*)libvlc_media_player_get_media(getMediaPlayer(env, thiz)));
}
