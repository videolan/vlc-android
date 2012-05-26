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

#define LOG_TAG "VLC/JNI/thumbnailer"
#include "log.h"

#include "utils.h"

#define THUMBNAIL_POSITION 0.5
#define PIXEL_SIZE 4 /* RGBA */

typedef struct
{
    libvlc_media_player_t *mp;

    bool hasThumb;

    char *frameData;
    char *thumbnail;

    unsigned thumbnailOffset;
    unsigned lineSize;
    unsigned nbLines;
    unsigned picPitch;

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
    *pixels = sys->frameData;
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

    sys->nbReceivedFrames++;

    if (libvlc_media_player_get_position(sys->mp) < THUMBNAIL_POSITION / 2
        // Arbitrary choice to work around broken files.
        && libvlc_media_player_get_length(sys->mp) > 1000
        && sys->nbReceivedFrames < 10)
    {
        return;
    }

    /* Else we have received our first thumbnail and we can exit. */
    const char *dataSrc = sys->frameData;
    char *dataDest = sys->thumbnail + sys->thumbnailOffset;
    /* Copy the thumbnail. */
    for (unsigned i = 0; i < sys->nbLines; ++i)
    {
        memcpy(dataDest, dataSrc, sys->picPitch);
        dataDest += sys->lineSize;
        dataSrc += sys->picPitch;
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
                                                     jint instance, jstring filePath,
                                                     jint width, jint height)
{
    libvlc_instance_t *libvlc = (libvlc_instance_t *)instance;
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
    libvlc_media_add_option( m, ":no-audio" );
    libvlc_media_add_option( m, ":no-spu" );
    libvlc_media_add_option( m, ":no-osd" );

    libvlc_media_player_set_media(sys->mp, m);
    libvlc_media_release(m);

    /* Get the size of the video with the tracks information of the media. */
    libvlc_media_track_info_t *tracks;
    libvlc_media_parse(m);
    int nbTracks = libvlc_media_get_tracks_info(m, &tracks);

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

    /* Abord if we have not found a video track. */
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
    unsigned picWidth  = width;
    unsigned picHeight = height;
    float videoAR = (float)videoWidth / videoHeight;
    float screenAR = (float)width / height;
    if (screenAR < videoAR)
    {
        picHeight = (float)width / videoAR;
        sys->thumbnailOffset = (height - picHeight) / 2 * width * PIXEL_SIZE;
    }
    else
    {
        picWidth = (float)height * videoAR;
        sys->thumbnailOffset = (width - picWidth) / 2 * PIXEL_SIZE;
    }

    sys->picPitch = picWidth * PIXEL_SIZE;
    sys->lineSize = width * PIXEL_SIZE;
    sys->nbLines = picHeight;

    /* Allocate the memory to store the frames. */
    unsigned picSize = sys->picPitch * picHeight;
    sys->frameData = malloc(picSize);
    if (sys->frameData == NULL)
    {
        LOGE("Could not allocate the memory to store the frame!");
        goto end;
    }

    /* Allocate the memory to store the thumbnail. */
    unsigned thumbnailSize = width * height * PIXEL_SIZE;
    sys->thumbnail = calloc(thumbnailSize, 1);
    if (sys->thumbnail == NULL)
    {
        LOGE("Could not allocate the memory to store the thumbnail!");
        goto end;
    }

    /* Set the video format and the callbacks. */
    libvlc_video_set_format(sys->mp, "RGBA", picWidth, picHeight, sys->picPitch);
    libvlc_video_set_callbacks(sys->mp, thumbnailer_lock, thumbnailer_unlock,
                               NULL, (void*)sys);

    /* Play the media. */
    libvlc_media_player_play(sys->mp);
    libvlc_media_player_set_position(sys->mp, THUMBNAIL_POSITION);

    /* Wait for the thumbnail to be generated. */
    pthread_mutex_lock(&sys->doneMutex);
    while (!sys->hasThumb)
        pthread_cond_wait(&sys->doneCondVar, &sys->doneMutex);
    pthread_mutex_unlock(&sys->doneMutex);

    /* Stop and realease the media player. */
    libvlc_media_player_stop(sys->mp);
    libvlc_media_player_release(sys->mp);

    /* Create the Java byte array to return the create thumbnail. */
    byteArray = (*env)->NewByteArray(env, thumbnailSize);
    if (byteArray == NULL)
    {
        LOGE("Could not allocate the Java byte array to store the frame!");
        goto end;
    }

    (*env)->SetByteArrayRegion(env, byteArray, 0, thumbnailSize,
                                 (jbyte *)sys->thumbnail);

end:
    pthread_mutex_destroy(&sys->doneMutex);
    pthread_cond_destroy(&sys->doneCondVar);
    free(sys->thumbnail);
    free(sys->frameData);
    free(sys);

    return byteArray;
}
