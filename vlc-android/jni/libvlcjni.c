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

jint getMediaPlayer(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    return (*env)->GetIntField(env, thiz, fieldMP);
}

jboolean releaseMediaPlayer(JNIEnv *env, jobject thiz)
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
    return (mediaPlayer == 0);
}

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
    releaseMediaPlayer(env, thiz);
    jclass clazz = (*env)->GetObjectClass(env, thiz);
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

    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

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


jint Java_vlc_android_LibVLC_getHeight(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return libvlc_video_get_height( mp );
    }
}

jint Java_vlc_android_LibVLC_getWidth(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return libvlc_video_get_width( mp );
    }
}

jboolean Java_vlc_android_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return ( libvlc_media_player_is_playing( mp ) == 1 );
    }
}

jboolean Java_vlc_android_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return ( libvlc_media_player_is_seekable( mp ) == 1 );
    }
}

void Java_vlc_android_LibVLC_play(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_play( mp );
    }
}

void Java_vlc_android_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_pause( mp );
    }
}

void Java_vlc_android_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_stop( mp );
    }
}

jint Java_vlc_android_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jint) libvlc_audio_get_volume( mp );
    }
    return -1;
}

jint Java_vlc_android_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume( mp, (int) volume );
    }
    return -1;
}

jlong Java_vlc_android_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return libvlc_media_player_get_time( mp );
    }
    return -1;
}

void Java_vlc_android_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_set_time( mp, time );
    }
}

jfloat Java_vlc_android_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jfloat) libvlc_media_player_get_position( mp );
    }
    return -1;
}

void Java_vlc_android_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_set_position( mp, pos );
    }
}

jlong Java_vlc_android_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jlong) libvlc_media_player_get_length( mp );
    }
    return -1;
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
