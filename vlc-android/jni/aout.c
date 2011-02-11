#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <jni.h>
#include <vlc/vlc.h>

#include "aout.h"
#include "log.h"

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

void aout_open(void **opaque, unsigned int *rate, unsigned int *nb_channels, unsigned int *fourCCFormat, unsigned int *nb_samples)
{
    LOGI("Opening the JNI Aout\n");

    // Replace opaque by p_sys and keep the libvlc pointe
    aout_sys_t *p_sys = malloc(sizeof(*p_sys));
    if(!p_sys)
        return;

    p_sys->j_libVlc = (jobject)*opaque;

    // Attach the thread to the VM
    JNIEnv *p_env;
    if ((*myVm)->AttachCurrentThread(myVm, &p_env, NULL) != 0)
    {
        LOGE("Couldn't attach the display thread to the JVM !\n");
        return;
    }
    LOGI("Attached to the VM thread\n");

    p_sys->j_libVlc = (jobject)*opaque;
    p_sys->p_env = p_env;
    *(aout_sys_t**)opaque = p_sys;

    // Call the init functions
    jclass cls = (*p_env)->GetObjectClass(p_env, p_sys->j_libVlc);
    jmethodID methodIdInitAout =
                (*p_env)->GetMethodID(p_env, cls, "initAout", "(III)V");
    if(methodIdInitAout == 0)
    {
        LOGE("Method initAout() cannot be found\n");
        return;
    }
    (*p_env)->CallVoidMethod(p_env, p_sys->j_libVlc, methodIdInitAout,
                             *rate, *nb_channels, *nb_samples);

    // Get the play methodId
    p_sys->play = (*p_env)->GetMethodID(p_env, cls, "playAudio", "()V");
}

void aout_play(void *opaque, unsigned char *buffer, size_t bufferSize, unsigned int nb_samples)
{
    aout_sys_t *p_sys = (aout_sys_t*)opaque;
    LOGI("%i", bufferSize);
    (*p_sys->p_env)->CallVoidMethod(p_sys->p_env, p_sys->j_libVlc, p_sys->play);
}

void aout_close(void *opaque)
{
    LOGI("closing the jni part\n");
}

