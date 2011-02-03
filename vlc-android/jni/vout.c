#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "vout.h"
#include "log.h"

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

unsigned vout_format(void **opaque, char *chroma,
                     unsigned *width, unsigned *height,
                     unsigned *pitches,
                     unsigned *lines)
{
    /* So far, *opaque is a pointer to the libvlc instance */
    jobject libVlc = (jobject) *opaque;
    assert (libVlc != NULL);

    /* Let's replace opaque by p_sys and store the pointer to libVlc */
    vout_sys_t **pp_sys = (vout_sys_t **) opaque;

    *pp_sys = calloc(1, sizeof(vout_sys_t));
    if (*pp_sys == NULL)
        return 0;

    vout_sys_t *p_sys = *pp_sys;

    p_sys->j_libVlc = libVlc;
    p_sys->i_frameWidth = *width;
    p_sys->i_frameHeight = *height;
    p_sys->i_frameSize = p_sys->i_frameWidth * p_sys->i_frameHeight * 2;

    p_sys->p_frameData = calloc(1, p_sys->i_frameSize);
    if (p_sys->p_frameData == NULL)
        return 0;

    strcpy(chroma, "RV16");
    *pitches = p_sys->i_frameWidth * 2;
    *lines = p_sys->i_frameHeight;

    return 1;
}


void vout_cleanup(void *opaque)
{
    vout_sys_t *p_sys = opaque;
    if (p_sys->byteArray != NULL)
        (*p_sys->p_env)->DeleteLocalRef(p_sys->p_env, p_sys->byteArray);
    free(p_sys);
}


void *vout_lock(void *opaque, void **pixels)
{
    vout_sys_t *p_sys = (vout_sys_t *)opaque;
    *pixels = p_sys->p_frameData;
    return NULL;
}


void vout_unlock(void *opaque, void *picture, void *const *p_pixels)
{
    // Nothing to do here until now.
}


void vout_display(void *opaque, void *picture)
{
    vout_sys_t *p_sys = (vout_sys_t *)opaque;
    static char b_attached = 0;
    static jmethodID methodIdDisplay = 0;
    JNIEnv *p_env = p_sys->p_env;

    if (!b_attached)
    {
        if ((*myVm)->AttachCurrentThread(myVm, &p_env, NULL) != 0)
        {
            LOGE("Couldn't attach the display thread to the JVM !\n");
            return;
        }
        // Save the environment refernce.
        p_sys->p_env = p_env;

        jclass cls = (*p_env)->GetObjectClass(p_env, p_sys->j_libVlc);

        jmethodID methodIdSetVoutSize =
                (*p_env)->GetMethodID(p_env, cls, "setVoutSize", "(II)V");

        if(methodIdSetVoutSize == 0)
        {
            LOGE("Method setVoutParams not found !\n");
            return;
        }

        // Transmit to Java the vout size.
        (*p_env)->CallVoidMethod(p_env, p_sys->j_libVlc, methodIdSetVoutSize,
                                 p_sys->i_frameWidth, p_sys->i_frameHeight);

        methodIdDisplay = (*p_env)->GetMethodID(p_env, cls, "displayCallback",
                                                "([B)V");

        if (methodIdDisplay == 0)
        {
            LOGE("Method displayCallback not found !\n");
            return;
        }

        /* Create a new byte array to store the frame data. */
        jbyteArray byteArray = (*p_env)->NewByteArray(p_env, p_sys->i_frameSize);
        if (byteArray == NULL)
        {
            LOGE("Couldn't allocate the Java byte array to store the frame !\n");
            return;
        }

        /* Use a global reference to not reallocate memory each time we run
           the display function. */
        p_sys->byteArray = (*p_env)->NewGlobalRef(p_env, byteArray);
        if (byteArray == NULL)
        {
            LOGE("Couldn't create the global reference !\n");
            return;
        }

        /* The local reference is no longer useful. */
        (*p_env)->DeleteLocalRef(p_env, byteArray);

        b_attached = 1;
    }

    // Fill the image buffer for debug purpose.
    //memset(p_sys->p_imageData, 255, p_sys->i_texSize / 2);

    (*p_env)->SetByteArrayRegion(p_env, p_sys->byteArray, 0,
                                 p_sys->i_frameSize,
                                 (jbyte *)p_sys->p_frameData);

    (*p_env)->CallVoidMethod(p_env, p_sys->j_libVlc,
                             methodIdDisplay, p_sys->byteArray);
}
