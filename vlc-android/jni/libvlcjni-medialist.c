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

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

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

// data is the MediaList Java object of the media list
static void vlc_media_list_event_callback(const libvlc_event_t *ev, void *data)
{
    jobject eventHandlerInstance = (jobject)(intptr_t)data;
    JNIEnv *env;

    bool isAttached = false;

    if (eventHandlerInstance == NULL)
        return;

    if ((*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2) < 0) {
        if ((*myVm)->AttachCurrentThread(myVm, &env, NULL) < 0)
            return;
        isAttached = true;
    }

    /* Creating the bundle in C allows us to subscribe to more events
     * and get better flexibility for each event. For example, we can
     * have totally different types of data for each event, instead of,
     * for example, only an integer and/or string.
     */
    jclass clsBundle = (*env)->FindClass(env, "android/os/Bundle");
    jmethodID clsCtor = (*env)->GetMethodID(env, clsBundle, "<init>", "()V" );
    jobject bundle = (*env)->NewObject(env, clsBundle, clsCtor);

    jmethodID putInt = (*env)->GetMethodID(env, clsBundle, "putInt", "(Ljava/lang/String;I)V" );
    jmethodID putFloat = (*env)->GetMethodID(env, clsBundle, "putFloat", "(Ljava/lang/String;F)V" );
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );

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

end:
    (*env)->DeleteLocalRef(env, bundle);
    if (isAttached)
        (*myVm)->DetachCurrentThread(myVm);
}

static int expand_media_internal(libvlc_instance_t* p_instance, libvlc_media_list_t* p_mlist, int position) {
    libvlc_media_t* p_md = libvlc_media_list_item_at_index(p_mlist, position);
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
            for(int i = subitem_count - 1; i >= 0; i--) {
                libvlc_media_t* p_subitem_old = libvlc_media_list_item_at_index(p_subitems, i);
                libvlc_media_t* p_subitem = libvlc_media_new_location(p_instance, libvlc_media_get_mrl(p_subitem_old));
                libvlc_media_list_insert_media(p_mlist, p_subitem, position+1);
                libvlc_media_release(p_subitem);
                libvlc_media_release(p_subitem_old);
            }
            libvlc_media_list_remove_index(p_mlist, position);
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

jlong Java_org_videolan_libvlc_MediaList_init(JNIEnv *env, jobject thiz, jobject libvlcJava) {
    libvlc_media_list_t* p_ml = libvlc_media_list_new((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"));
    if(!p_ml) {
        jclass exc = (*env)->FindClass(env, "org/videolan/libvlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to create LibVLC media list");
        return (jlong)(intptr_t)NULL;
    }

    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldID = (*env)->GetFieldID(env, cls, "mEventHandler", "Lorg/videolan/libvlc/EventHandler;");
    jobject eventHandler = (*env)->GetObjectField(env, thiz, fieldID);
    jobject globalRef = getEventHandlerReference(env, thiz, eventHandler);

    setLong(env, thiz, "mEventHanderGlobalRef", (jlong)(intptr_t)globalRef);

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_list_event_manager(p_ml);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaListItemAdded,
        libvlc_MediaListItemDeleted,
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_media_list_event_callback, globalRef);

    return (jlong)(intptr_t)p_ml;
}

void Java_org_videolan_libvlc_MediaList_nativeDestroy(JNIEnv *env, jobject thiz) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_release(p_ml);
    (*env)->DeleteGlobalRef(env, (jobject)(intptr_t)getLong(env, thiz, "mEventHanderGlobalRef"));
}

void Java_org_videolan_libvlc_MediaList_clear(JNIEnv *env, jobject thiz) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_lock(p_ml);
    while (libvlc_media_list_count(p_ml) > 0) {
        libvlc_media_list_remove_index(p_ml, 0);
    }
    libvlc_media_list_unlock(p_ml);
}

jint Java_org_videolan_libvlc_MediaList_expandMedia(JNIEnv *env, jobject thiz, jobject libvlcJava, jint position) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_lock(p_ml);
    jint ret = (jint)expand_media_internal((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"), p_ml, position);
    libvlc_media_list_unlock(p_ml);
    return ret;
}

void Java_org_videolan_libvlc_MediaList_loadPlaylist(JNIEnv *env, jobject thiz, jobject libvlcJava, jstring mrl) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, NULL);

    libvlc_media_t *p_md = libvlc_media_new_location((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"), p_mrl);
    libvlc_media_add_option(p_md, ":demux=playlist,none");
    libvlc_media_add_option(p_md, ":run-time=1");

    struct stopped_monitor* monitor = malloc(sizeof(struct stopped_monitor));
    pthread_mutex_init(&monitor->doneMutex, NULL);
    pthread_cond_init(&monitor->doneCondVar, NULL);
    monitor->stopped = false;
    pthread_mutex_lock(&monitor->doneMutex);

    libvlc_media_player_t* p_mp = libvlc_media_player_new((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"));
    libvlc_event_manager_t* ev = libvlc_media_player_event_manager(p_mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerEndReached, stopped_callback, monitor);
    libvlc_media_player_set_media(p_mp, p_md);
    libvlc_media_player_play(p_mp);

    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 2; /* If "VLC can't open the file", return */
    int mp_alive = 1;
    while(!(monitor->stopped) && mp_alive) {
        pthread_cond_timedwait(&monitor->doneCondVar, &monitor->doneMutex, &deadline);
        mp_alive = libvlc_media_player_will_play(p_mp);
    }
    pthread_mutex_unlock(&monitor->doneMutex);
    pthread_mutex_destroy(&monitor->doneMutex);
    pthread_cond_destroy(&monitor->doneCondVar);
    free(monitor);

    libvlc_media_player_release(p_mp);

    libvlc_media_list_lock(p_ml);
    int pos = libvlc_media_list_count(p_ml);
    libvlc_media_list_add_media(p_ml, p_md);
    expand_media_internal((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"), p_ml, pos);
    libvlc_media_list_unlock(p_ml);

    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);
}

void Java_org_videolan_libvlc_MediaList_remove(JNIEnv *env, jobject thiz, jint position) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_remove_index(p_ml, position);
    libvlc_media_list_unlock(p_ml);
}

void Java_org_videolan_libvlc_MediaList_add(JNIEnv *env, jobject thiz, jobject libvlcInstance, jstring mrl, bool noVideo, bool noOmx) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, NULL);
    libvlc_media_t *p_md = libvlc_media_new_location((libvlc_instance_t*)(intptr_t)getLong(env, libvlcInstance, "mLibVlcInstance"), p_mrl);
    if (!noOmx) {
        jclass cls = (*env)->GetObjectClass(env, libvlcInstance);
        jmethodID methodId = (*env)->GetMethodID(env, cls, "useIOMX", "()Z");
        if ((*env)->CallBooleanMethod(env, libvlcInstance, methodId)) {
            /*
             * Set higher caching values if using iomx decoding, since some omx
             * decoders have a very high latency, and if the preroll data isn't
             * enough to make the decoder output a frame, the playback timing gets
             * started too soon, and every decoded frame appears to be too late.
             * On Nexus One, the decoder latency seems to be 25 input packets
             * for 320x170 H.264, a few packets less on higher resolutions.
             * On Nexus S, the decoder latency seems to be about 7 packets.
             */
            libvlc_media_add_option(p_md, ":file-caching=1500");
            libvlc_media_add_option(p_md, ":network-caching=1500");
            libvlc_media_add_option(p_md, ":codec=mediacodec,iomx,all");
        }
        if (noVideo)
            libvlc_media_add_option(p_md, ":no-video");
    }
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_add_media(p_ml, p_md);
    libvlc_media_list_unlock(p_ml);
    libvlc_media_release(p_md);
    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);
}

void Java_org_videolan_libvlc_MediaList_insert(JNIEnv *env, jobject thiz, jobject libvlcInstance, jint position, jstring mrl) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, NULL);
    libvlc_media_t *p_md = libvlc_media_new_location((libvlc_instance_t*)(intptr_t)getLong(env, libvlcInstance, "mLibVlcInstance"), p_mrl);
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_insert_media(p_ml, p_md, position);
    libvlc_media_list_unlock(p_ml);
    libvlc_media_release(p_md);
    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);
}

jint Java_org_videolan_libvlc_MediaList_size(JNIEnv *env, jobject thiz) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_lock(p_ml);
    int count = libvlc_media_list_count(p_ml);
    libvlc_media_list_unlock(p_ml);
    return count;
}

jstring Java_org_videolan_libvlc_MediaList_getMRL(JNIEnv *env, jobject thiz, jint position) {
    libvlc_media_list_t* p_ml = getMediaListFromJava(env, thiz);
    libvlc_media_list_lock(p_ml);
    libvlc_media_t* p_md = libvlc_media_list_item_at_index(p_ml, position);
    libvlc_media_list_unlock(p_ml);
    if(p_md) {
        char* p_mrl = libvlc_media_get_mrl(p_md);
        libvlc_media_release(p_md);
        return (*env)->NewStringUTF(env, p_mrl);
    } else
        return NULL;
}
