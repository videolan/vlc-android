#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "thumbnailer.h"

#define LOG_TAG "VLC/JNI/thumbnailer"
#include "log.h"

#define THUMBNAIL_POSITION 0.5
#define PIXEL_SIZE 4


/**
 * Thumbnailer main function.
 * return null if the thumbail generation failed.
 **/
jbyteArray Java_org_videolan_vlc_android_LibVLC_getThumbnail(JNIEnv *p_env, jobject thiz,
                                                             jint i_instance, jstring filePath,
                                                             jint i_width, jint i_height)
{
    libvlc_instance_t *p_instance = (libvlc_instance_t *)i_instance;
    jboolean isCopy;
    jbyteArray byteArray = NULL;
    const char *psz_filePath = (*p_env)->GetStringUTFChars(p_env, filePath,
                                                           &isCopy);

    /* Create the thumbnailer data structure */
    thumbnailer_sys_t *p_sys = malloc(sizeof(thumbnailer_sys_t));
    if (p_sys == NULL)
    {
        LOGE("Couldn't create the thumbnailer data structure!");
        return;
    }
    p_sys->b_hasThumb = 0;
    p_sys->i_nbReceivedFrames = 0;
    p_sys->p_frameData = NULL;

    /* Initialize the barrier. */
    pthread_mutex_init(&p_sys->doneMutex, NULL);
    pthread_cond_init(&p_sys->doneCondVar, NULL);

    /* Create a media player playing environment */
    p_sys->p_mp = libvlc_media_player_new(p_instance);

    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = libvlc_media_new_path(p_instance, psz_filePath);
    if (p_m == NULL)
    {
        LOGE("Couldn't create the media to play!");
        goto end;
    }
    libvlc_media_add_option( p_m, ":no-audio" );

    libvlc_media_player_set_media(p_sys->p_mp, p_m);
    libvlc_media_release(p_m);

    /* Get the size of the video with the tracks information of the media. */
    libvlc_media_track_info_t *p_tracks;
    libvlc_media_parse(p_m);
    int i_nbTracks = libvlc_media_get_tracks_info(p_m, &p_tracks);

    unsigned i, i_videoWidth, i_videoHeight;
    float f_videoAR;
    int b_hasVideoTrack = 0;
    for (i = 0; i < i_nbTracks; ++i)
    {
        if (p_tracks[i].i_type == libvlc_track_video)
        {
            i_videoWidth = p_tracks[i].u.video.i_width;
            i_videoHeight = p_tracks[i].u.video.i_height;
            f_videoAR = (float)i_videoWidth / i_videoHeight;
            b_hasVideoTrack = 1;
            break;
        }
    }

    free(p_tracks);

    /* Abord if we have not found a video track. */
    if (b_hasVideoTrack == 0)
    {
        LOGE("Could not find a video track in this file.\n");
        goto end;
    }

    /* Compute the size parameters of the frame to generate. */
    unsigned i_picWidth, i_picHeight;
    float f_thumbnailAR = (float)i_width / i_height;
    if (f_videoAR < f_thumbnailAR)
    {
        i_picHeight = i_height / f_videoAR;
        i_picWidth = i_width;
        p_sys->i_picPitch = i_picWidth * PIXEL_SIZE;
        p_sys->i_thumbnailOffset = (i_picHeight - i_height) / 2 * p_sys->i_picPitch;
    }
    else
    {
        i_picHeight = i_height;
        i_picWidth = i_width * f_videoAR;
        p_sys->i_picPitch = i_picWidth * PIXEL_SIZE;
        p_sys->i_thumbnailOffset = (i_picWidth - i_width) / 2 * PIXEL_SIZE;
    }

    p_sys->i_lineSize = i_width * PIXEL_SIZE;
    p_sys->i_nbLines = i_height;

    /* Allocate the memory to store the frames. */
    unsigned i_picSize = p_sys->i_picPitch * i_picHeight;
    p_sys->p_frameData = malloc(i_picSize);
    if (p_sys->p_frameData == NULL)
    {
        LOGE("Couldn't allocate the memory to store the frame!");
        goto end;
    }

    /* Allocate the memory to store the thumbnail. */
    unsigned i_thumbnailSize = i_width * i_height * PIXEL_SIZE;
    p_sys->p_thumbnail = malloc(i_thumbnailSize);
    if (p_sys->p_thumbnail == NULL)
    {
        LOGE("Couldn't allocate the memory to store the thumbnail!");
        goto end;
    }

    /* Set the video format and the callbacks. */
    libvlc_video_set_format(p_sys->p_mp, "RGBA", i_picWidth, i_picHeight, p_sys->i_picPitch);
    libvlc_video_set_callbacks(p_sys->p_mp, thumbnailer_lock, thumbnailer_unlock,
                               NULL, (void*)p_sys);

    /* Play the media. */
    libvlc_media_player_play(p_sys->p_mp);
    libvlc_media_player_set_position(p_sys->p_mp, THUMBNAIL_POSITION);

    /* Wait for the thumbnail to be generated. */
    pthread_mutex_lock(&p_sys->doneMutex);
    pthread_cond_wait(&p_sys->doneCondVar, &p_sys->doneMutex);
    pthread_mutex_unlock(&p_sys->doneMutex);

    /* Stop and realease the media player. */
    libvlc_media_player_stop(p_sys->p_mp);
    libvlc_media_player_release(p_sys->p_mp);

    /* Create the Java byte array to return the create thumbnail. */
    byteArray = (*p_env)->NewByteArray(p_env, i_thumbnailSize);
    if (byteArray == NULL)
    {
        LOGE("Couldn't allocate the Java byte array to store the frame!");
        free(p_sys->p_thumbnail);
        goto end;
    }

    (*p_env)->SetByteArrayRegion(p_env, byteArray, 0, i_thumbnailSize,
                                 (jbyte *)p_sys->p_thumbnail);

    (*p_env)->DeleteLocalRef(p_env, byteArray);
    (*p_env)->ReleaseStringUTFChars(p_env, filePath, psz_filePath);

end:
    /* Free the memory. */
    pthread_mutex_destroy(&p_sys->doneMutex);
    pthread_cond_destroy(&p_sys->doneCondVar);
    free(p_sys->p_frameData);
    free(p_sys);

    return byteArray;
}


/**
 * Thumbnailer vout lock
 **/
void *thumbnailer_lock(void *opaque, void **pixels)
{
    thumbnailer_sys_t *p_sys = (thumbnailer_sys_t *)opaque;
    *pixels = p_sys->p_frameData;
    return NULL;
}


/**
 * Thumbnailer vout unlock
 **/
void thumbnailer_unlock(void *opaque, void *picture, void *const *p_pixels)
{
    thumbnailer_sys_t *p_sys = (thumbnailer_sys_t *)opaque;

    /* If we have already received a thumbnail, we skip this frame. */
    if (p_sys->b_hasThumb == 1)
        return;

    p_sys->i_nbReceivedFrames++;

    if (libvlc_media_player_get_position(p_sys->p_mp) < THUMBNAIL_POSITION / 2
        // Arbitrary choice to work around broken files.
        && libvlc_media_player_get_length(p_sys->p_mp) > 1000
        && p_sys->i_nbReceivedFrames < 10)
    {
        return;
    }

    /* Else we have received our first thumbnail
       and we can exit the thumbnailer. */

    unsigned i;
    char *p_dataSrc = p_sys->p_frameData + p_sys->i_thumbnailOffset;
    char *p_dataDest = p_sys->p_thumbnail;
    /* Copy the thumbnail. */
    for (i = 0; i < p_sys->i_nbLines; ++i)
    {
        memcpy(p_dataDest, p_dataSrc, p_sys->i_lineSize);
        p_dataDest += p_sys->i_lineSize;
        p_dataSrc += p_sys->i_picPitch;
    }

    p_sys->b_hasThumb = 1;

    /* Signal that the thumbnail was created. */
    pthread_mutex_lock(&p_sys->doneMutex);
    pthread_cond_signal(&p_sys->doneCondVar);
    pthread_mutex_unlock(&p_sys->doneMutex);
}
