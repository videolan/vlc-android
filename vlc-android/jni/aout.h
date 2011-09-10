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

int aout_open(void **opaque, char *format, unsigned *rate, unsigned *nb_channels);
void aout_play(void *opaque, const void *samples, unsigned count, int64_t pts);
void aout_close(void *opaque);

#endif // LIBVLCJNI_VOUT_H
