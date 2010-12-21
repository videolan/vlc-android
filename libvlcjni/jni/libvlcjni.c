#include <stdio.h>
#include <string.h>

#include <android/native_window_jni.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "libvlcjni.h"

// Native android window to display the video.
ANativeWindow *p_nativeWindow;


void Java_vlc_android_LibVLC_setSurface(JNIEnv *env, jobject thiz, jobject surface)
{
    p_nativeWindow = ANativeWindow_fromSurface(env, surface);
}

jint Java_vlc_android_LibVLC_init(JNIEnv *env, jobject thiz)
{
    char psz_pWin[255];
    snprintf(psz_pWin, 255, "%i", p_nativeWindow);

    const char *argv[] = {"-I", "dummy", "-vvv", "--no-plugins-cache",
                          "--no-audio", "--no-drop-late-frames",
                          "--vout", "egl_android",
                          "--egl-android-window", psz_pWin};
    return (jint)libvlc_new_with_builtins(10, argv, vlc_builtins_modules);
}

void Java_vlc_android_LibVLC_destroy(JNIEnv *env, jobject thiz, jint instance)
{
    libvlc_instance_t *p_instance = (libvlc_instance_t*)instance;
    libvlc_release(p_instance);
}

void Java_vlc_android_LibVLC_readMedia(JNIEnv *env, jobject thiz, jint instance,
                                       jstring mrl)
{
    jboolean isCopy;
    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);

    /* Create a new item */
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);

    /* Create a media player playing environement */
    libvlc_media_player_t *mp = libvlc_media_player_new_from_media(m);

    /* No need to keep the media now */
    libvlc_media_release(m);

    /* Play the media. */
    libvlc_media_player_play(mp);

    //libvlc_media_player_release(mp);

    (*env)->ReleaseStringUTFChars(env, mrl, psz_mrl);
}

jstring Java_vlc_android_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_vlc_android_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_vlc_android_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}
