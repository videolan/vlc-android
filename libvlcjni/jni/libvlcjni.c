#include <string.h>
#include <jni.h>

#include <vlc/vlc.h>

#include "libvlcjni.h"

jint Java_vlc_android_libVLC_init(JNIEnv *env, jobject thiz)
{
    const char *argv[] = { "-I dummy", "-vvv", "--no-plugins-cache" };
    return (jint)libvlc_new_with_builtins( 3, argv, vlc_builtins_modules );
}

void Java_vlc_android_libVLC_destroy(JNIEnv *env, jobject thiz, jint instance)
{
    libvlc_instance_t *p_instance = (libvlc_instance_t*)instance;
    libvlc_release(p_instance);
}

void Java_vlc_android_libVLC_readMedia(JNIEnv *env, jobject thiz, jint instance)
{
    /* Create a new item */
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance, "/sdcard/test.mp3");

    /* Create a media player playing environement */
    libvlc_media_player_t *mp = libvlc_media_player_new_from_media(m);

    /* No need to keep the media now */
    libvlc_media_release(m);

    libvlc_media_player_play(mp);

    sleep(10000);

    libvlc_media_player_release(mp);
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
