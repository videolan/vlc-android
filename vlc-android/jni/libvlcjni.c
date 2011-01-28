#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "libvlcjni.h"
#include "vout.h"
#include "log.h"


JavaVM *myVm; // Pointer on the Java virtul machine.
jobject myJavaLibVLC; // Pointer on the LibVLC Java object.


jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;

    LOGD("JNI interface loaded.\n");
    return JNI_VERSION_1_2;
}


jint Java_vlc_android_LibVLC_init(JNIEnv *env, jobject thiz)
{
    myJavaLibVLC = (*env)->NewGlobalRef(env, thiz);

    const char *argv[] = {"-I", "dummy", "-vvv", "--no-plugins-cache",
                          "--no-audio", "--no-drop-late-frames",
                          "--vout", "vmem"};

    jint ret = (jint)libvlc_new_with_builtins(8, argv, vlc_builtins_modules);

    LOGI("LibVLC loaded.\n");
    return ret;
}


void Java_vlc_android_LibVLC_destroy(JNIEnv *env, jobject thiz, jint instance)
{
    (*env)->DeleteGlobalRef(env, myJavaLibVLC);
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
    libvlc_media_player_t *mp = libvlc_media_player_new((libvlc_instance_t*)instance);

    libvlc_media_player_set_media(mp, m);
    libvlc_video_set_format_callbacks(mp, vout_format, vout_cleanup);
    libvlc_video_set_callbacks(mp, vout_lock, vout_unlock, vout_display, NULL);

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
