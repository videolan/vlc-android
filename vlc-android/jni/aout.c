#include <stdio.h>
#include <assert.h>
#include <string.h>
#include <stdint.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "aout.h"

#define LOG_TAG "VLC/JNI/aout"
#include "log.h"

// An audio frame will contain FRAME_SIZE samples
#define FRAME_SIZE (4096*2)

typedef struct
{
    jobject j_libVlc;        /// Pointer to the LibVLC Java object
    jmethodID play;          /// Java method to play audio buffers
    jbyteArray byteArray;    /// Raw audio data to be played
    JNIEnv *p_env;           ///< Main thread environment: this is NOT the
                             ///  play thread! See comments in aout_play()
} aout_sys_t;

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

//void aout_open (void **opaque, unsigned int *rate, unsigned int *nb_channels, unsigned int *fourCCFormat, unsigned int *nb_samples)
int aout_open(void **opaque, char *format, unsigned *rate, unsigned *nb_channels)
{
    LOGI("Opening the JNI audio output");
    aout_sys_t *p_sys;
    JNIEnv *p_env;

    // Replace opaque by p_sys and keep the libvlc pointe
    p_sys = calloc (1, sizeof (*p_sys));
    if (!p_sys)
    {
        *opaque = NULL;
        return -1; // OOM
    }

    // Keep a reference to our Java object and return aout_sys_t
    p_sys->j_libVlc = (jobject) *opaque;
    *opaque         = (void*) p_sys;

    LOGI ("Parameters: %u channels, FOURCC '%4.4s', "
          "sample rate: %uHz",
          *nb_channels, format, *rate);

    // Attach the thread to the VM. Keep this JNIEnv for aout_close()
    if ((*myVm)->AttachCurrentThread (myVm, &p_env, NULL) != 0)
    {
        LOGE("Couldn't attach the display thread to the JVM !");
        return -1;
    }
    p_sys->p_env = p_env;

    // Call the init function.
    jclass cls = (*p_env)->GetObjectClass (p_env, p_sys->j_libVlc);
    jmethodID methodIdInitAout = (*p_env)->GetMethodID (p_env, cls,
                                                        "initAout", "(III)V");
    if (!methodIdInitAout)
    {
        LOGE ("Method initAout() could not be found!");
        (*myVm)->DetachCurrentThread (myVm);
        *opaque = NULL;
        free (p_sys);
        return -1;
    }

    LOGV ("Fixed number of channels to 2, number of samples to %d", FRAME_SIZE);
    *nb_channels = 2;

    (*p_env)->CallVoidMethod (p_env, p_sys->j_libVlc, methodIdInitAout,
                              *rate, *nb_channels, FRAME_SIZE);
    if ((*p_env)->ExceptionCheck (p_env))
    {
        LOGE ("Unable to create audio player!");
#ifndef NDEBUG
        (*p_env)->ExceptionDescribe (p_env);
#endif
        (*p_env)->ExceptionClear (p_env);
        *opaque = NULL;
        free (p_sys);
        return -1;
    }

    /* Create a new byte array to store the audio data. */
    jbyteArray byteArray = (*p_env)->NewByteArray (p_env,
                                                   *nb_channels *
                                                   FRAME_SIZE *
                                                   sizeof (uint16_t) /* =2 */);
    if (byteArray == NULL)
    {
        LOGE("Couldn't allocate the Java byte array to store the audio data!");
        (*myVm)->DetachCurrentThread (myVm);
        *opaque = NULL;
        free (p_sys);
        return -1;
    }

    /* Use a global reference to not reallocate memory each time we run
       the display function. */
    p_sys->byteArray = (*p_env)->NewGlobalRef (p_env, byteArray);
    if (p_sys->byteArray == NULL)
    {
        LOGE ("Couldn't create the global reference!");
        (*myVm)->DetachCurrentThread (myVm);
        *opaque = NULL;
        free (p_sys);
        return -1;
    }

    /* The local reference is no longer useful. */
    (*p_env)->DeleteLocalRef (p_env, byteArray);

    // Get the play methodId
    p_sys->play = (*p_env)->GetMethodID (p_env, cls, "playAudio", "([BI)V");
    assert (p_sys->play != NULL);
    return 0;
}

/**
 * Play an audio sample
 **/
//void aout_play (void *opaque, unsigned char *buffer, size_t bufferSize, unsigned int nb_samples)
void aout_play(void *opaque, const void *samples, unsigned count, int64_t pts)
{
    aout_sys_t *p_sys = (aout_sys_t*) opaque;
    JNIEnv *p_env;

    /* How ugly: we constantly attach/detach this thread to/from the JVM
         * because it will be killed before aout_close is called.
         * aout_close will actually be called in an different thread!
         */
    (*myVm)->AttachCurrentThread (myVm, &p_env, NULL);

    (*p_env)->SetByteArrayRegion (p_env, p_sys->byteArray, 0,
                                  2 /*nb_channels*/ * count * sizeof (uint16_t), (jbyte*) samples);
    if ((*p_env)->ExceptionCheck (p_env))
    {
        // This can happen if for some reason the size of the input buffer
        // is larger than the size of the output buffer
        LOGE ("An exception occurred while calling SetByteArrayRegion");
        (*p_env)->ExceptionDescribe (p_env);
        (*p_env)->ExceptionClear (p_env);
        return;
    }

    (*p_env)->CallVoidMethod (p_env, p_sys->j_libVlc, p_sys->play,
                              p_sys->byteArray, 2 /*nb_channels*/ * count * sizeof (uint16_t), FRAME_SIZE);
    // FIXME: check for errors

    (*myVm)->DetachCurrentThread (myVm);
}

void aout_close(void *opaque)
{
    LOGI ("Closing audio output");
    aout_sys_t *p_sys = (aout_sys_t*) opaque;
    if (!p_sys)
        return;

    if (p_sys->byteArray)
    {
        // Want a crash? Call this function! But whyyyyy???
        // Anyway, one more good reason to create the buffer in pure Java
        //(*p_sys->p_env)->DeleteGlobalRef (p_sys->p_env, p_sys->byteArray);
    }
    (*myVm)->DetachCurrentThread (myVm);
    free (p_sys);
}
