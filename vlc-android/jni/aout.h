#ifndef LIBVLCJNI_AOUT_H
#define LIBVLCJNI_AOUT_H

typedef struct
{
    jobject j_libVlc;   // Pointer to the LibVLC Java object
    jmethodID play;
    JNIEnv *p_env;
} aout_sys_t;

void aout_open(void **opaque, unsigned int *rate, unsigned int *nb_channels, unsigned int *fourCCFormat, unsigned int *nb_samples);
void aout_play(void *opaque, unsigned char *buffer, size_t bufferSize, unsigned int nb_samples);
void aout_close(void *opaque);


#endif // LIBVLCJNI_VOUT_H
