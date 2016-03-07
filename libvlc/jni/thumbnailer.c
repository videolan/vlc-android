/*****************************************************************************
 * thumbnailer.c
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

#include <assert.h>
#include <jni.h>
#include <vlc/vlc.h>
#include <pthread.h>
#include <stdbool.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>

#include "libvlcjni-vlcobject.h"
#include "utils.h"

#define THUMBNAIL_POSITION 0.5
#define PIXEL_SIZE 4 /* RGBA */
#define THUMBNAIL_MIN_WIDTH 32
#define THUMBNAIL_MAX_WIDTH 4096
#define THUMBNAIL_MIN_HEIGHT 32
#define THUMBNAIL_MAX_HEIGHT 2304


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

enum {
    THUMB_INIT,
    THUMB_SEEKED = 0x1,
    THUMB_VOUT   = 0x2,
    THUMB_DONE   = 0x4,
};

typedef struct
{
    int state;

    char *thumbData;

    char *frameData;
    unsigned frameSize;

    unsigned blackBorders;
    unsigned frameWidth;
    unsigned frameHeight;
    unsigned thumbHeight;
    unsigned thumbPitch;

    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
} thumbnailer_sys_t;

static unsigned
thumbnailer_setup(void **opaque, char *chroma,
                  unsigned *width, unsigned *height,
                  unsigned *pitches,
                  unsigned *lines)
{
    thumbnailer_sys_t *sys = *opaque;

    unsigned videoWidth = *width, videoHeight = *height;
    strcpy(chroma, "RGBA");

    LOGD("Video dimensions: %ix%i.\n", videoWidth, videoHeight );

    /* VLC could not tell us the size */
    if( videoWidth == 0 || videoHeight == 0 )
    {
        LOGE("Could not find the video dimensions.\n");
        goto end;
    }

    if( videoWidth < THUMBNAIL_MIN_WIDTH || videoHeight < THUMBNAIL_MIN_HEIGHT
        || videoWidth > THUMBNAIL_MAX_WIDTH || videoHeight > THUMBNAIL_MAX_HEIGHT )
    {
        LOGE("Wrong video dimensions.\n");
        goto end;
    }

    /* Compute the size parameters of the frame to generate. */
    unsigned thumbWidth  = sys->frameWidth;
    unsigned thumbHeight = sys->frameHeight;
    const float inputAR = (float)videoWidth / videoHeight;
    const float screenAR = (float)sys->frameWidth / sys->frameHeight;

    /* Most of the cases, video is wider than tall */
    if (screenAR < inputAR)
    {
        thumbHeight = (float)sys->frameWidth / inputAR + 1;
        sys->blackBorders = ( (sys->frameHeight - thumbHeight) / 2 ) * sys->frameWidth;
    }
    else
    {
        LOGD("Weird aspect Ratio.\n");
        thumbWidth = (float)sys->frameHeight * inputAR;
        sys->blackBorders = (sys->frameWidth - thumbWidth) / 2;
    }

    sys->thumbPitch  = thumbWidth * PIXEL_SIZE;
    sys->thumbHeight = thumbHeight;

    /* Allocate the memory to store the frames. */
    size_t thumbSize = sys->thumbPitch * (sys->thumbHeight+1);
    sys->thumbData = malloc(thumbSize);
    if (sys->thumbData == NULL)
    {
        LOGE("Could not allocate the memory to store the frame!");
        goto end;
    }

    *width = thumbWidth;
    *height = thumbHeight;
    pitches[0] = sys->thumbPitch;
    *lines = thumbHeight;

    pthread_mutex_lock(&sys->doneMutex);
    sys->state |= THUMB_VOUT;
    pthread_cond_signal(&sys->doneCondVar);
    pthread_mutex_unlock(&sys->doneMutex);

    return 1;
end:
    pthread_mutex_lock(&sys->doneMutex);
    sys->state |= THUMB_DONE;
    pthread_cond_signal(&sys->doneCondVar);
    pthread_mutex_unlock(&sys->doneMutex);
    return 0;
}

static void
thumbnailer_event(const libvlc_event_t *ev, void *opaque)
{
    thumbnailer_sys_t *sys = opaque;
    if (ev->u.media_player_position_changed.new_position >= THUMBNAIL_POSITION)
    {
        pthread_mutex_lock(&sys->doneMutex);
        sys->state |= THUMB_SEEKED;
        pthread_cond_signal(&sys->doneCondVar);
        pthread_mutex_unlock(&sys->doneMutex);
    }
}

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
    (void) opaque;
    (void) picture;
    (void) pixels;
}

static void thumbnailer_display(void *opaque, void *picture)
{
    thumbnailer_sys_t *sys = opaque;

    /* If we have already received a thumbnail, or we are still seeking,
     * we skip this frame. */
    pthread_mutex_lock(&sys->doneMutex);
    if ((sys->state & THUMB_SEEKED|THUMB_VOUT) != (THUMB_SEEKED|THUMB_VOUT))
    {
        pthread_mutex_unlock(&sys->doneMutex);
        return;
    }
    pthread_mutex_unlock(&sys->doneMutex);

    /* Allocate the memory to store the thumbnail. */
    sys->frameSize = sys->frameWidth * sys->frameHeight * PIXEL_SIZE;
    sys->frameData = calloc(sys->frameSize, 1);
    if (sys->frameData == NULL)
    {
        LOGE("Could not allocate the memory to store the thumbnail!");
        goto end;
    }

    /* we have received our first thumbnail and we can exit. */
    const char *dataSrc = sys->thumbData;
    char *dataDest = sys->frameData + sys->blackBorders * PIXEL_SIZE;

    /* Copy the thumbnail. */
    for (unsigned i = 0; i < sys->thumbHeight; ++i)
    {
        memcpy(dataDest, dataSrc, sys->thumbPitch);
        dataDest += sys->frameWidth * PIXEL_SIZE;
        dataSrc += sys->thumbPitch;
    }

end:
    /* Signal that the thumbnail was created. */
    pthread_mutex_lock(&sys->doneMutex);
    sys->state |= THUMB_DONE;
    pthread_cond_signal(&sys->doneCondVar);
    pthread_mutex_unlock(&sys->doneMutex);
}

/**
 * Thumbnailer main function.
 * return null if the thumbail generation failed.
 **/
jbyteArray
Java_org_videolan_libvlc_util_VLCUtil_nativeGetThumbnail(JNIEnv *env,
                                                         jobject thiz,
                                                         jobject jmedia,
                                                         const jint frameWidth,
                                                         const jint frameHeight)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, jmedia);
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
    libvlc_media_player_t *mp = libvlc_media_player_new_from_media(p_obj->u.p_m);
    if (!mp)
        goto end;
    libvlc_media_player_set_video_title_display(mp, libvlc_position_disable, 0);

    sys->frameWidth = frameWidth;
    sys->frameHeight = frameHeight;
    /* Set the video format and the callbacks. */
    libvlc_video_set_callbacks(mp, thumbnailer_lock, thumbnailer_unlock,
                               thumbnailer_display, (void*)sys);
    libvlc_video_set_format_callbacks(mp, thumbnailer_setup, NULL);

    libvlc_event_attach(libvlc_media_player_event_manager(mp),
                        libvlc_MediaPlayerPositionChanged,
                        thumbnailer_event, sys);

    /* Play the media. */
    libvlc_media_player_play(mp);
    libvlc_media_player_set_position(mp, THUMBNAIL_POSITION);

    /* Wait for the thumbnail to be generated. */
    pthread_mutex_lock(&sys->doneMutex);
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 3;

    /* Wait for a VOUT for 3 seconds, some input format like *.TS make some time
     * to initialize a VOUT */
    int ret = 0;
    while (!(sys->state & THUMB_VOUT) && ret != ETIMEDOUT)
        ret = pthread_cond_timedwait(&sys->doneCondVar, &sys->doneMutex, &deadline);

    if (sys->state & THUMB_VOUT)
    {
        ret = 0;
        /* Wait an additional 7 seconds for a frame */
        deadline.tv_sec += 7;
        while (!(sys->state & THUMB_DONE) && ret != ETIMEDOUT)
            ret = pthread_cond_timedwait(&sys->doneCondVar, &sys->doneMutex, &deadline);
    }
    else
        LOGE("media has not VOUT");
    pthread_mutex_unlock(&sys->doneMutex);

    /* Stop and release the media player. */
    libvlc_media_player_stop(mp);
    libvlc_event_detach(libvlc_media_player_event_manager(mp),
                        libvlc_MediaPlayerPositionChanged,
                        thumbnailer_event, sys);
    libvlc_media_player_release(mp);

    if ((sys->state & THUMB_DONE) && sys->frameData) {
        /* Create the Java byte array to return the create thumbnail. */
        byteArray = (*env)->NewByteArray(env, sys->frameSize);
        if (byteArray == NULL)
        {
            LOGE("Could not allocate the Java byte array to store the frame!");
            goto end;
        }

        (*env)->SetByteArrayRegion(env, byteArray, 0, sys->frameSize,
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
