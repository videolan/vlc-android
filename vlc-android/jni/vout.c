#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "vout.h"
#include "log.h"

extern JavaVM *myVm;
extern jobject myJavaLibVLC;


unsigned vout_format(void **opaque, char *chroma,
                     unsigned *width, unsigned *height,
                     unsigned *pitches,
                     unsigned *lines)
{
    vout_sys_t **pp_sys = (vout_sys_t **)opaque;

    *pp_sys = malloc(sizeof(vout_sys_t));
    if (*pp_sys == NULL)
        return 0;

    vout_sys_t *p_sys = *pp_sys;

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
    LOGI("Display\n");
    vout_sys_t *p_sys = (vout_sys_t *)opaque;
    static char b_attached = 0;
    static jmethodID methodIdDisplay = 0;
    static JNIEnv *env;

    if (!b_attached)
    {
        if ((*myVm)->AttachCurrentThread(myVm, &env, NULL) != 0)
        {
            LOGE("Couldn't attach the display thread to the JVM !\n");
            return;
        }

        jclass cls = (*env)->GetObjectClass(env, myJavaLibVLC);

        jmethodID methodIdSetVoutSize =
                (*env)->GetMethodID(env, cls, "setVoutSize", "(II)V");

        if(methodIdSetVoutSize == 0)
        {
            LOGE("Method setVoutParams not found !\n");
            return;
        }

        // Transmit to Java the vout size.
        (*env)->CallVoidMethod(env, myJavaLibVLC, methodIdSetVoutSize,
                               p_sys->i_frameWidth, p_sys->i_frameHeight);

        methodIdDisplay = (*env)->GetMethodID(env, cls, "displayCallback", "([B)V");

        if (methodIdDisplay == 0)
        {
            LOGE("Method displayCallback not found !\n");
            return;
        }

        b_attached = 1;
    }

    // Fill the image buffer for debug purpose.
    //memset(p_sys->p_imageData, 255, p_sys->i_texSize / 2);

    jbyteArray jb = (*env)->NewByteArray(env, p_sys->i_frameSize);

    (*env)->SetByteArrayRegion(env, jb, 0, p_sys->i_frameSize,
                               (jbyte *)p_sys->p_frameData);

    (*env)->CallVoidMethod(env, myJavaLibVLC, methodIdDisplay, jb);

    (*env)->DeleteLocalRef(env, jb);
}
