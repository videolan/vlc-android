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
    } String;
    struct {
        jclass clazz;
        jfieldID descriptorID;
    } FileDescriptor;
    struct {
        jclass clazz;
        jmethodID onNativeCrashID;
    } LibVLC;
    struct {
        jclass clazz;
        jfieldID mInstanceID;
        jmethodID dispatchEventFromNativeID;
        jmethodID getWeakReferenceID;
        jmethodID dispatchEventFromWeakNativeID;
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
        jclass clazz;
        jmethodID displayErrorFromNativeID;
        jmethodID displayLoginFromNativeID;
        jmethodID displayQuestionFromNativeID;
        jmethodID displayProgressFromNativeID;
        jmethodID cancelFromNativeID;
        jmethodID updateProgressFromNativeID;
    } Dialog;
};

extern struct fields fields;

#endif // LIBVLCJNI_UTILS_H
