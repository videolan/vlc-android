/*****************************************************************************
 * thumbnailer.c
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <assert.h>
#include <jni.h>
#include <vlc/vlc.h>
#include <pthread.h>
#include <stdbool.h>
#include <time.h>
#include <errno.h>

#define LOG_TAG "VLC/JNI/thumbnailer"
#include "log.h"

#include "utils.h"

#define THUMBNAIL_POSITION 0.5
#define PIXEL_SIZE 4 /* RGBA */


/*
   Frame is:   thumbnail + black borders
   frameData = frameWidth * frameHeight (values given by Java UI)

   ┌————————————————————————————————————————————————————┐
   │                                                    │
   │                 Black Borders                      │
   │                                                    │
   ├————————————————————————————————————————————————————┤
   │                                                    │
   │                 Thumbnail Data                     │
   │                                                    │
   │             thumbHeight x thumbWidth               │
   │             thumbPitch = thumbWidth * 4            │
   │                                                    │
   ├————————————————————————————————————————————————————┤
   │                                                    │
   │                                                    │
   │                                                    │
   └————————————————————————————————————————————————————┘
*/

typedef struct
{
    libvlc_media_player_t *mp;

    bool hasThumb;

    char *thumbData;
    char *frameData;

    unsigned blackBorders;
    unsigned frameWidth;
    unsigned thumbHeight;
    unsigned thumbPitch;

    unsigned nbReceivedFrames;

    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
} thumbnailer_sys_t;


/**
 * Thumbnailer vout lock
 **/
static void *thumbnailer_lock(void *opaque, void **pixels)
{
    thumbnailer_sys_t *sys = opaque;
    *pixels = sys->thumbData;
    return NULL;
}


/**
 * Thumbnailer vout unlock
 **/
static void thumbnailer_unlock(void *opaque, void *picture, void *const *pixels)
{
    thumbnailer_sys_t *sys = opaque;

    /* If we have already received a thumbnail, we skip this frame. */
    pthread_mutex_lock(&sys->doneMutex);
    bool hasThumb = sys->hasThumb;
    pthread_mutex_unlock(&sys->doneMutex);
    if (hasThumb)
        return;

    if (++sys->nbReceivedFrames < 6)
        return;

    /* Else we have received our first thumbnail and we can exit. */
    const char *dataSrc = sys->thumbData;
    char *dataDest = sys->frameData + sys->blackBorders * PIXEL_SIZE;

    /* Copy the thumbnail. */
    for (unsigned i = 0; i < sys->thumbHeight; ++i)
    {
        memcpy(dataDest, dataSrc, sys->thumbPitch);
        dataDest += sys->frameWidth * PIXEL_SIZE;
        dataSrc += sys->thumbPitch;
    }

    /* Signal that the thumbnail was created. */
    pthread_mutex_lock(&sys->doneMutex);
    sys->hasThumb = true;
    pthread_cond_signal(&sys->doneCondVar);
    pthread_mutex_unlock(&sys->doneMutex);
}


/**
 * Thumbnailer main function.
 * return null if the thumbail generation failed.
 **/
jbyteArray Java_org_videolan_vlc_LibVLC_getThumbnail(JNIEnv *env, jobject thiz,
                                                     jlong instance, jstring filePath,
                                                     const jint frameWidth, const jint frameHeight)
{
    libvlc_instance_t *libvlc = (libvlc_instance_t *)(intptr_t)instance;
    jbyteArray byteArray = NULL;

    /* Create the thumbnailer data structure */
    thumbnailer_sys_t *sys = calloc(1, sizeof(thumbnailer_sys_t));
    if (sys == NULL)
    {
        LOGE("Could not create the thumbnailer data structure!");
        return NULL;
    }

    /* Initialize the barrier. */
    pthread_mutex_init(&sys->doneMutex, NULL);
    pthread_cond_init(&sys->doneCondVar, NULL);

    /* Create a media player playing environment */
    sys->mp = libvlc_media_player_new(libvlc);

    libvlc_media_t *m = new_media(instance, env, thiz, filePath, true, false);
    if (m == NULL)
    {
        LOGE("Could not create the media to play!");
        goto end;
    }

    /* Fast and no options */
    libvlc_media_add_option( m, ":no-audio" );
    libvlc_media_add_option( m, ":no-spu" );
    libvlc_media_add_option( m, ":no-osd" );

    libvlc_media_player_set_media(sys->mp, m);

    /* Get the size of the video with the tracks information of the media. */
    libvlc_media_track_info_t *tracks;
    libvlc_media_parse(m);
    int nbTracks = libvlc_media_get_tracks_info(m, &tracks);
    libvlc_media_release(m);

    /* Parse the results */
    unsigned videoWidth, videoHeight;
    bool hasVideoTrack = false;
    for (unsigned i = 0; i < nbTracks; ++i)
        if (tracks[i].i_type == libvlc_track_video)
        {
            videoWidth = tracks[i].u.video.i_width;
            videoHeight = tracks[i].u.video.i_height;
            hasVideoTrack = true;
            break;
        }

    free(tracks);

    /* Abort if we have not found a video track. */
    if (!hasVideoTrack)
    {
        LOGE("Could not find any video track in this file.\n");
        goto end;
    }

    /* VLC could not tell us the size */
    if( videoWidth == 0 || videoHeight == 0 )
    {
        LOGE("Could not find the video dimensions.\n");
        goto end;
    }

    /* Compute the size parameters of the frame to generate. */
    unsigned thumbWidth  = frameWidth;
    unsigned thumbHeight = frameHeight;
    const float inputAR = (float)videoWidth / videoHeight;
    const float screenAR = (float)frameWidth / frameHeight;

    /* Most of the cases, video is wider than tall */
    if (screenAR < inputAR)
    {
        thumbHeight = (float)frameWidth / inputAR + 1;
        sys->blackBorders = ( (frameHeight - thumbHeight) / 2 ) * frameWidth;
    }
    else
    {
        LOGD("Weird aspect Ratio.\n");
        thumbWidth = (float)frameHeight * inputAR;
        sys->blackBorders = (frameWidth - thumbWidth) / 2;
    }

    sys->thumbPitch  = thumbWidth * PIXEL_SIZE;
    sys->thumbHeight = thumbHeight;
    sys->frameWidth  = frameWidth;

    /* Allocate the memory to store the frames. */
    size_t thumbSize = sys->thumbPitch * (sys->thumbHeight+1);
    sys->thumbData = malloc(thumbSize);
    if (sys->thumbData == NULL)
    {
        LOGE("Could not allocate the memory to store the frame!");
        goto end;
    }

    /* Allocate the memory to store the thumbnail. */
    unsigned frameSize = frameWidth * frameHeight * PIXEL_SIZE;
    sys->frameData = calloc(frameSize, 1);
    if (sys->frameData == NULL)
    {
        LOGE("Could not allocate the memory to store the thumbnail!");
        goto end;
    }

    /* Set the video format and the callbacks. */
    libvlc_video_set_format(sys->mp, "RGBA", thumbWidth, thumbHeight, sys->thumbPitch);
    libvlc_video_set_callbacks(sys->mp, thumbnailer_lock, thumbnailer_unlock,
                               NULL, (void*)sys);

    /* Play the media. */
    libvlc_media_player_play(sys->mp);
    libvlc_media_player_set_position(sys->mp, THUMBNAIL_POSITION);

    /* Wait for the thumbnail to be generated. */
    pthread_mutex_lock(&sys->doneMutex);
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 10; /* amount of seconds before we abort thumbnailer */
    while (!sys->hasThumb) {
        int ret = pthread_cond_timedwait(&sys->doneCondVar, &sys->doneMutex, &deadline);
        if (ret == ETIMEDOUT)
            break;
    }
    pthread_mutex_unlock(&sys->doneMutex);

    /* Stop and release the media player. */
    libvlc_media_player_stop(sys->mp);
    libvlc_media_player_release(sys->mp);

    if (sys->hasThumb) {
        /* Create the Java byte array to return the create thumbnail. */
        byteArray = (*env)->NewByteArray(env, frameSize);
        if (byteArray == NULL)
        {
            LOGE("Could not allocate the Java byte array to store the frame!");
            goto end;
        }

        (*env)->SetByteArrayRegion(env, byteArray, 0, frameSize,
                (jbyte *)sys->frameData);
    }

end:
    pthread_mutex_destroy(&sys->doneMutex);
    pthread_cond_destroy(&sys->doneCondVar);
    free(sys->frameData);
    free(sys->thumbData);
    free(sys);

    return byteArray;
}
