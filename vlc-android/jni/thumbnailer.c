#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "thumbnailer.h"

#define LOG_TAG "VLC/JNI/thumbnailer"
#include "log.h"

#define THUMBNAIL_POSITION 0.5


/**
 * Thumbnailer main function.
 * return null if the thumbail generation failed.
 **/
jbyteArray Java_vlc_android_LibVLC_getThumbnail(JNIEnv *p_env, jobject thiz,
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
    libvlc_media_player_set_media(p_sys->p_mp, p_m);
    libvlc_media_release(p_m);

    unsigned i_pitch = i_width * 4;
    p_sys->i_dataSize = i_pitch * i_height;

    /* Allocate the memory to store the frames. */
    p_sys->p_frameData = malloc(p_sys->i_dataSize);
    if (p_sys->p_frameData == NULL)
    {
        LOGE("Couldn't allocate the memory to store the frame!");
        goto end;
    }

    /* Allocate the memory to store the thumbnail. */
    p_sys->p_thumbnail = malloc(p_sys->i_dataSize);
    if (p_sys->p_thumbnail == NULL)
    {
        LOGE("Couldn't allocate the memory to store the thumbnail!");
        goto end;
    }

    /* Set the video format and the callbacks. */
    libvlc_video_set_format(p_sys->p_mp, "RGBA", i_width, i_height, i_pitch);
    libvlc_video_set_callbacks(p_sys->p_mp, thumbnailer_lock, thumbnailer_unlock,
                               NULL, (void*)p_sys);

    /* Play the media. */
    libvlc_media_player_play(p_sys->p_mp);
    libvlc_media_player_set_position(p_sys->p_mp, THUMBNAIL_POSITION);

    /* Wait for the thumbnail for being generated. */
    pthread_mutex_lock(&p_sys->doneMutex);
    pthread_cond_wait(&p_sys->doneCondVar, &p_sys->doneMutex);
    pthread_mutex_unlock(&p_sys->doneMutex);

    /* Stop and realease the media player. */
    libvlc_media_player_stop(p_sys->p_mp);
    libvlc_media_player_release(p_sys->p_mp);

    /* Create the Java byte array to return the create thumbnail. */
    byteArray = (*p_env)->NewByteArray(p_env, p_sys->i_dataSize);
    if (byteArray == NULL)
    {
        LOGE("Couldn't allocate the Java byte array to store the frame!");
        goto end;
    }

    (*p_env)->SetByteArrayRegion(p_env, byteArray, 0, p_sys->i_dataSize,
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
    memcpy(p_sys->p_thumbnail, p_sys->p_frameData, p_sys->i_dataSize);

    p_sys->b_hasThumb = 1;

    /* Signal that the thumbnail was created. */
    pthread_mutex_lock(&p_sys->doneMutex);
    pthread_cond_signal(&p_sys->doneCondVar);
    pthread_mutex_unlock(&p_sys->doneMutex);
}
