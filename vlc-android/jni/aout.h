#ifndef LIBVLCJNI_AOUT_H
#define LIBVLCJNI_AOUT_H

#include <string.h>
#include <jni.h>

typedef struct
{
    jobject j_libVlc;        /// Pointer to the LibVLC Java object
    jmethodID play;          /// Java method to play audio buffers
    jbyteArray byteArray;    /// Raw audio data to be played
    JNIEnv *p_env;           ///< Main thread environment: this is NOT the
                             ///  play thread! See comments in aout_play()
} aout_sys_t;

void aout_open(void **opaque, unsigned int *rate, unsigned int *nb_channels, unsigned int *fourCCFormat, unsigned int *nb_samples);
void aout_play(void *opaque, unsigned char *buffer, size_t bufferSize, unsigned int nb_samples);
void aout_close(void *opaque);


#endif // LIBVLCJNI_VOUT_H
