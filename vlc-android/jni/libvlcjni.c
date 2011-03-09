#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <jni.h>

#include <vlc/vlc.h>

#include "libvlcjni.h"
#include "aout.h"
#include "vout.h"

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
JavaVM *myVm;


jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;

    LOGD("JNI interface loaded.");
    return JNI_VERSION_1_2;
}


void Java_vlc_android_LibVLC_nativeInit(JNIEnv *env, jobject thiz)
{
    const char *argv[] = {"-I", "dummy", "-vvv", "--no-plugins-cache",
                          "--no-drop-late-frames"};

    libvlc_instance_t *instance =
            libvlc_new_with_builtins(sizeof(argv) / sizeof(*argv),
                                     argv, vlc_builtins_modules);

    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz,
                                        "mLibVlcInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) instance);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "vlc/android/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);
    return;
}


void Java_vlc_android_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);

    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    jint mediaPlayer = (*env)->GetIntField(env, thiz, fieldMP);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_stop(mp);
        libvlc_media_player_release(mp);
        (*env)->SetIntField(env, thiz, fieldMP, 0);
    }

    jfieldID field = (*env)->GetFieldID(env, clazz, "mLibVlcInstance", "I");
    jint libVlcInstance = (*env)->GetIntField(env, thiz, field);
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*) libVlcInstance;
    libvlc_release(instance);

    (*env)->SetIntField(env, thiz, field, 0);
}


void Java_vlc_android_LibVLC_readMedia(JNIEnv *env, jobject thiz,
                                       jint instance, jstring mrl)
{
    jboolean isCopy;
    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);

    /* Create a new item */
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new((libvlc_instance_t*)instance);

    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz);

    libvlc_media_player_set_media(mp, m);
    libvlc_video_set_format_callbacks(mp, vout_format, vout_cleanup);
    libvlc_video_set_callbacks(mp, vout_lock, vout_unlock, vout_display,
                               (void*) myJavaLibVLC);

    libvlc_audio_set_callbacks(mp, aout_open, aout_play, aout_close,
                               (void*) myJavaLibVLC);

    /* No need to keep the media now */
    libvlc_media_release(m);

    /* Keep a pointer to this media player */
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz,
                                        "mMediaPlayerInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) mp);

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
