#include <string.h>
#include <jni.h>

#include <vlc/libvlc.h>

#include "libvlcjni.h"

jlong Java_vlc_android_libVLC_init(JNIEnv *env, jobject thiz)
{
    const char *argv[] = { "-I dummy", "-vvv", "--no-plugins-cache" };
    return (jlong)libvlc_new_with_builtins( 3, argv, vlc_builtins_modules );
}

void Java_vlc_android_libVLC_destroy(JNIEnv *env, jobject thiz, jlong instance)
{
    libvlc_instance_t *p_instance = (libvlc_instance_t*)instance;
    libvlc_release(p_instance);
}

jstring Java_vlc_android_libVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_vlc_android_libVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_vlc_android_libVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}
