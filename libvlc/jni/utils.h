/*****************************************************************************
 * utils.h
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

#ifndef LIBVLCJNI_UTILS_H
#define LIBVLCJNI_UTILS_H

#include <vlc/vlc.h>
#include <vlc/libvlc_media.h>
#include <vlc/libvlc_media_list.h>

#define LOG_TAG "VLC/JNI/VLCObject"
#include "log.h"

struct fields {
    jint SDK_INT;
    struct {
        jclass clazz;
    } IllegalStateException;
    struct {
        jclass clazz;
    } IllegalArgumentException;
    struct {
        jclass clazz;
    } RuntimeException;
    struct {
        jclass clazz;
    } OutOfMemoryError;
    struct {
        jclass clazz;
    } String;
    struct {
        jclass clazz;
        jfieldID descriptorID;
    } FileDescriptor;
    struct {
        jclass clazz;
        jfieldID mInstanceID;
        jmethodID dispatchEventFromNativeID;
    } VLCObject;
    struct {
        struct {
            jclass clazz;
        } Track;

        struct {
            jclass clazz;
        } Slave;

        jclass clazz;
        jmethodID createAudioTrackFromNativeID;
        jmethodID createVideoTrackFromNativeID;
        jmethodID createSubtitleTrackFromNativeID;
        jmethodID createUnknownTrackFromNativeID;
        jmethodID createSlaveFromNativeID;
        jmethodID createStatsFromNativeID;
    } Media;
    struct {
        struct {
            jclass clazz;
        } Title;
        struct {
            jclass clazz;
        } Chapter;
        struct {
            jclass clazz;
        } TrackDescription;
        struct {
            jclass clazz;
            jfieldID mInstanceID;
        } Equalizer;

        jclass clazz;
        jmethodID createTitleFromNativeID;
        jmethodID createChapterFromNativeID;
        jmethodID createTrackDescriptionFromNativeID;
    } MediaPlayer;
    struct {
        struct {
            jclass clazz;
        } Description;
        jclass clazz;
        jmethodID createDescriptionFromNativeID;
    } MediaDiscoverer;
    struct {
        struct {
            jclass clazz;
        } Description;
        jclass clazz;
        jmethodID createDescriptionFromNativeID;
        jmethodID createItemFromNativeID;
    } RendererDiscoverer;
    struct {
        jclass clazz;
        jmethodID displayErrorFromNativeID;
        jmethodID displayLoginFromNativeID;
        jmethodID displayQuestionFromNativeID;
        jmethodID displayProgressFromNativeID;
        jmethodID cancelFromNativeID;
        jmethodID updateProgressFromNativeID;
    } Dialog;
};

static inline jstring vlcNewStringUTF(JNIEnv* env, const char* psz_string)
{
    if (psz_string == NULL)
        return NULL;
    for (int i = 0 ; psz_string[i] != '\0' ; ) {
        uint8_t lead = psz_string[i++];
        uint8_t nbBytes;
        if ((lead & 0x80) == 0)
            continue;
        else if ((lead >> 5) == 0x06)
            nbBytes = 1;
        else if ((lead >> 4) == 0x0E)
            nbBytes = 2;
        else if ((lead >> 3) == 0x1E)
            nbBytes = 3;
        else {
            LOGE("Invalid UTF lead character\n");
            return NULL;
        }
        for (int j = 0 ; j < nbBytes && psz_string[i] != '\0' ; j++) {
            uint8_t byte = psz_string[i++];
            if ((byte & 0x80) == 0) {
                LOGE("Invalid UTF byte\n");
                return NULL;
            }
        }
    }
    return (*env)->NewStringUTF(env, psz_string);
}

extern struct fields fields;

#endif // LIBVLCJNI_UTILS_H
