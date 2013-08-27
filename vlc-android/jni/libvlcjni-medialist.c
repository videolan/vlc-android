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

#include "utils.h"
#define LOG_TAG "VLC/JNI/MediaList"
#include "log.h"

libvlc_media_list_t* getMediaList(JNIEnv *env, jobject thiz) {
    return (libvlc_media_list_t*)(intptr_t)getLong(env, thiz, "mMediaListInstance");
}

jlong Java_org_videolan_libvlc_MediaList_init(JNIEnv *env, jobject thiz, jobject libvlcJava) {
    libvlc_media_list_t* p_ml = libvlc_media_list_new((libvlc_instance_t*)(intptr_t)getLong(env, libvlcJava, "mLibVlcInstance"));
    if(!p_ml) {
        jclass exc = (*env)->FindClass(env, "org/videolan/libvlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to create LibVLC media list");
        return (jlong)(intptr_t)NULL;
    }
    return (jlong)(intptr_t)p_ml;
}

void Java_org_videolan_libvlc_MediaList_nativeDestroy(JNIEnv *env, jobject thiz) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
    libvlc_media_list_release(p_ml);
}

void Java_org_videolan_libvlc_MediaList_remove(JNIEnv *env, jobject thiz, jint position) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_remove_index(p_ml, position);
    libvlc_media_list_unlock(p_ml);
}

void Java_org_videolan_libvlc_MediaList_add(JNIEnv *env, jobject thiz, jobject libvlcInstance, jstring mrl) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, NULL);
    libvlc_media_t *p_md = libvlc_media_new_location((libvlc_instance_t*)(intptr_t)getLong(env, libvlcInstance, "mLibVlcInstance"), p_mrl);
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_add_media(p_ml, p_md);
    libvlc_media_list_unlock(p_ml);
    libvlc_media_release(p_md);
    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);
}

void Java_org_videolan_libvlc_MediaList_insert(JNIEnv *env, jobject thiz, jobject libvlcInstance, jint position, jstring mrl) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, NULL);
    libvlc_media_t *p_md = libvlc_media_new_location((libvlc_instance_t*)(intptr_t)getLong(env, libvlcInstance, "mLibVlcInstance"), p_mrl);
    libvlc_media_list_lock(p_ml);
    libvlc_media_list_insert_media(p_ml, p_md, position);
    libvlc_media_list_unlock(p_ml);
    libvlc_media_release(p_md);
    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);
}

jint Java_org_videolan_libvlc_MediaList_size(JNIEnv *env, jobject thiz) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
    libvlc_media_list_lock(p_ml);
    int count = libvlc_media_list_count(p_ml);
    libvlc_media_list_unlock(p_ml);
    return count;
}

jstring Java_org_videolan_libvlc_MediaList_getMRL(JNIEnv *env, jobject thiz, jint position) {
    libvlc_media_list_t* p_ml = getMediaList(env, thiz);
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
