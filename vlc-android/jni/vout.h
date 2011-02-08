#ifndef LIBVLCJNI_VOUT_H
#define LIBVLCJNI_VOUT_H

typedef struct vout_sys_t
{
    unsigned i_frameWidth;
    unsigned i_frameHeight;

    unsigned i_frameSize;
    char *p_frameData;
    jbyteArray byteArray;

    jobject j_libVlc; // Pointer to the LibVLC Java object.
    JNIEnv *p_env;

    unsigned char b_attached; // Thread is attached to Java VM
    jmethodID methodIdDisplay;
}vout_sys_t;


unsigned getAlignedSize(unsigned size);
unsigned vout_format(void **opaque, char *chroma,
                unsigned *width, unsigned *height,
                unsigned *pitches,
                unsigned *lines);
void vout_cleanup(void *opaque);
void *vout_lock(void *opaque, void **pixels);
void vout_unlock(void *opaque, void *picture, void *const *p_pixels);
void vout_display(void *opaque, void *picture);


#endif // LIBVLCJNI_VOUT_H
