/*****************************************************************************
 * libvlcjni.c
 *****************************************************************************
 * Copyright © 2010-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <string.h>
#include <pthread.h>

#include <vlc/vlc.h>

#include <jni.h>

#include <android/api-level.h>

#include "libvlcjni.h"
#include "aout.h"

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

static bool iomx_enabled;

void add_media_codec_options(libvlc_media_t *p_md)
{
    if (iomx_enabled) {
        /*
         * Set higher caching values if using iomx decoding, since some omx
         * decoders have a very high latency, and if the preroll data isn't
         * enough to make the decoder output a frame, the playback timing gets
         * started too soon, and every decoded frame appears to be too late.
         * On Nexus One, the decoder latency seems to be 25 input packets
         * for 320x170 H.264, a few packets less on higher resolutions.
         * On Nexus S, the decoder latency seems to be about 7 packets.
         */
        libvlc_media_add_option(p_md, ":file-caching=1500");
        libvlc_media_add_option(p_md, ":network-caching=1500");
    } else {
        libvlc_media_add_option(p_md, ":codec=avcodec,all");
    }
}

static libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    return (libvlc_media_player_t*)(*env)->GetIntField(env, thiz, fieldMP);
}

static void unsetMediaPlayer(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    (*env)->SetIntField(env, thiz, fieldMP, 0);
}

static void releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
    {
        libvlc_media_player_stop(mp);
        libvlc_media_player_release(mp);
        unsetMediaPlayer(env, thiz);
    }
}

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
JavaVM *myVm;

static jobject eventManagerInstance = NULL;

static pthread_mutex_t vout_android_lock;
static void *vout_android_surf = NULL;
static void *vout_android_gui = NULL;

void *jni_LockAndGetAndroidSurface() {
    pthread_mutex_lock(&vout_android_lock);
    return vout_android_surf;
}

void jni_UnlockAndroidSurface() {
    pthread_mutex_unlock(&vout_android_lock);
}

void jni_SetAndroidSurfaceSize(int width, int height)
{
    if (vout_android_gui == NULL)
        return;

    JNIEnv *p_env;

    (*myVm)->AttachCurrentThread (myVm, &p_env, NULL);
    jclass cls = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, cls, "setSurfaceSize", "(II)V");

    (*p_env)->CallVoidMethod (p_env, vout_android_gui, methodId, width, height);

    (*p_env)->DeleteLocalRef(p_env, cls);
    (*myVm)->DetachCurrentThread (myVm);
}

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;
    JavaVM *myVm = data;

    bool isAttached = false;

    if (eventManagerInstance == NULL)
        return;

    int status = (*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2);
    if (status < 0) {
        LOGD("vlc_event_callback: failed to get JNI environment, "
             "assuming native thread");
        status = (*myVm)->AttachCurrentThread(myVm, &env, NULL);
        if (status < 0)
            return;
        isAttached = true;
    }

    /* Get the object class */
    jclass cls = (*env)->GetObjectClass(env, eventManagerInstance);
    if (!cls) {
        LOGE("EventManager: failed to get class reference");
        goto end;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(I)V");
    if (methodID) {
        (*env)->CallVoidMethod(env, eventManagerInstance, methodID, ev->type);
    } else {
        LOGE("EventManager: failed to get the callback method");
    }

end:
    if (isAttached)
        (*myVm)->DetachCurrentThread(myVm);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;

    pthread_mutex_init(&vout_android_lock, NULL);

    LOGD("JNI interface loaded.");
    return JNI_VERSION_1_2;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    pthread_mutex_destroy(&vout_android_lock);
}

void Java_org_videolan_vlc_android_LibVLC_attachSurface(JNIEnv *env, jobject thiz, jobject surf, jobject gui, jint width, jint height) {
    jclass clz;
    jfieldID fid;

    pthread_mutex_lock(&vout_android_lock);
    clz = (*env)->GetObjectClass(env, surf);
    fid = (*env)->GetFieldID(env, clz, "mSurface", "I");
    if (fid == NULL) {
        jthrowable exp = (*env)->ExceptionOccurred(env);
        if (exp) {
            (*env)->DeleteLocalRef(env, exp);
            (*env)->ExceptionClear(env);
        }
        fid = (*env)->GetFieldID(env, clz, "mNativeSurface", "I");
    }
    vout_android_surf = (void*)(*env)->GetIntField(env, surf, fid);
    (*env)->DeleteLocalRef(env, clz);

    vout_android_gui = (*env)->NewGlobalRef(env, gui);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_vlc_android_LibVLC_detachSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    vout_android_surf = NULL;
    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    vout_android_gui = NULL;
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_vlc_android_LibVLC_nativeInit(JNIEnv *env, jobject thiz, jboolean enable_iomx)
{
    iomx_enabled = enable_iomx;
    /* Don't add any invalid options, otherwise it causes LibVLC to crash */
    static const char *argv[] = {
        "-I", "dummy",
        "-vv",
        "--no-plugins-cache",
        "--no-drop-late-frames",
        "--control", "logger",
        "--logmode", "android",
    };
    libvlc_instance_t *instance = libvlc_new(sizeof(argv) / sizeof(*argv), argv);

    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz, "mLibVlcInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) instance);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "org/videolan/vlc/android/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);
    return;
}


void Java_org_videolan_vlc_android_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
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

int currentSdk( JNIEnv *p_env, jobject thiz )
{
    jclass  cls = (*p_env)->FindClass( p_env, "org/videolan/vlc/android/Util" );
    if ( !cls )
    {
        LOGE( "Failed to load util class (org/videolan/vlc/android/Util)" );
        return 0;
    }
    jmethodID methodSdkVersion = (*p_env)->GetStaticMethodID( p_env, cls,
                                                       "getApiLevel", "()I" );
    if ( !methodSdkVersion )
    {
        LOGE("Failed to load method getApiLevel()" );
        return 0;
    }
    int version = (*p_env)->CallStaticIntMethod( p_env, cls, methodSdkVersion );
    LOGI("Got version: %d\n", version );
    return version;
}

void Java_org_videolan_vlc_android_LibVLC_detachEventManager(JNIEnv *env, jobject thiz)
{
    if (eventManagerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventManagerInstance);
        eventManagerInstance = NULL;
    }
}

void Java_org_videolan_vlc_android_LibVLC_setEventManager(JNIEnv *env, jobject thiz, jobject eventManager)
{
    if (eventManagerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventManagerInstance);
        eventManagerInstance = NULL;
    }

    jclass cls = (*env)->GetObjectClass(env, eventManager);
    if (!cls) {
        LOGE("setEventManager: failed to get class reference");
        return;
    }

    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(I)V");
    if (!methodID) {
        LOGE("setEventManager: failed to get the callback method");
        return;
    }

    eventManagerInstance = (*env)->NewGlobalRef(env, eventManager);
}

jobjectArray Java_org_videolan_vlc_android_LibVLC_readMediaMeta(JNIEnv *env,
                                    jobject thiz, jint instance, jstring mrl)
{
    jobjectArray array = (*env)->NewObjectArray(env, 8,
            (*env)->FindClass(env, "java/lang/String"),
            (*env)->NewStringUTF(env, ""));

    jboolean isCopy;
    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);
    (*env)->ReleaseStringUTFChars(env, mrl, psz_mrl);
    if (!m)
    {
        LOGE("readMediaMeta: Couldn't create the media!");
        return;
    }
    add_media_codec_options(m);

    libvlc_media_parse(m);

    static const char str[][7] = {
        "artist", "album", "title", "genre",
    };
    static const libvlc_meta_t meta_id[] = {
        libvlc_meta_Artist,
        libvlc_meta_Album,
        libvlc_meta_Title,
        libvlc_meta_Genre,
    };
    int i;
    for (i=0; i < sizeof(str) / sizeof(*str); i++) {
        char *meta = libvlc_media_get_meta(m, meta_id[i]);
        if (!meta)
            meta = strdup("unknown");

        jstring k = (*env)->NewStringUTF(env, str[i]);
        (*env)->SetObjectArrayElement(env, array, 2*i, k);
        jstring v = (*env)->NewStringUTF(env, meta);
        (*env)->SetObjectArrayElement(env, array, 2*i+1, v);

        free(meta);
   }

   libvlc_media_release(m);
   return array;
}

void Java_org_videolan_vlc_android_LibVLC_readMedia(JNIEnv *env, jobject thiz,
                                       jint instance, jstring mrl)
{
    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    /* Create a new item */
    jboolean isCopy;
    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);
    (*env)->ReleaseStringUTFChars(env, mrl, psz_mrl);
    if (!m)
    {
        LOGE("readMedia: Couldn't create the media!");
        return;
    }

    add_media_codec_options(m);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new((libvlc_instance_t*)instance);

    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz);

    libvlc_media_player_set_media(mp, m);

    if ( currentSdk( env, thiz )  < 9 ) //On newer version, we can use SLES
    {
        libvlc_audio_set_callbacks(mp, aout_play, NULL, NULL, NULL, NULL,
                                   (void*) myJavaLibVLC);
        libvlc_audio_set_format_callbacks(mp, aout_open, aout_close);
    }

    /* No need to keep the media now */
    libvlc_media_release(m);

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaPlayerPlaying,
        libvlc_MediaPlayerPaused,
        libvlc_MediaPlayerEndReached,
        libvlc_MediaPlayerStopped,
    };
    int i;
    for (i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); ++i)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);

    /* Keep a pointer to this media player */
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz,
                                        "mMediaPlayerInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) mp);

    libvlc_media_player_play(mp);
}

jboolean Java_org_videolan_vlc_android_LibVLC_hasVideoTrack(JNIEnv *env, jobject thiz,
                                                            jint i_instance, jstring filePath)
{
    libvlc_instance_t *p_instance = (libvlc_instance_t *)i_instance;
    const char *psz_filePath = (*env)->GetStringUTFChars(env, filePath, 0);

    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = libvlc_media_new_path(p_instance, psz_filePath);
    (*env)->ReleaseStringUTFChars(env, filePath, psz_filePath);

    if (p_m == NULL)
    {
        LOGE("Couldn't create the media!");
        return JNI_FALSE;
    }

    add_media_codec_options(p_m);

    /* Get the tracks information of the media. */
    libvlc_media_track_info_t *p_tracks;
    libvlc_media_parse(p_m);

    jboolean hasVideo = JNI_FALSE;
    int i_nbTracks = libvlc_media_get_tracks_info(p_m, &p_tracks);
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
        if (p_tracks[i].i_type == libvlc_track_video)
        {
            hasVideo = JNI_TRUE;
            break;
        }

    free(p_tracks);
    libvlc_media_release(p_m);

    return hasVideo;
}

struct length_change_monitor {
    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
    bool length_changed;
};

static void length_changed_callback(const libvlc_event_t *ev, void *data)
{
    struct length_change_monitor *monitor = data;
    pthread_mutex_lock(&monitor->doneMutex);
    monitor->length_changed = true;
    pthread_cond_signal(&monitor->doneCondVar);
    pthread_mutex_unlock(&monitor->doneMutex);
}

jlong Java_org_videolan_vlc_android_LibVLC_getLengthFromFile(JNIEnv *env, jobject thiz,
                                                        jint i_instance, jstring filePath)
{
    jlong length = 0;
    struct length_change_monitor *monitor;
    monitor = malloc(sizeof(*monitor));
    if (!monitor)
        return 0;

    /* Initialize pthread variables. */
    pthread_mutex_init(&monitor->doneMutex, NULL);
    pthread_cond_init(&monitor->doneCondVar, NULL);
    monitor->length_changed = false;

    libvlc_instance_t *p_instance = (libvlc_instance_t *)i_instance;
    const char *psz_filePath = (*env)->GetStringUTFChars(env, filePath, 0);

    /* Create a new item and assign it to the media player. */
    libvlc_media_t *m = libvlc_media_new_path(p_instance, psz_filePath);
    if (m == NULL)
    {
        LOGE("Couldn't create the media to play!");
        goto end;
    }

    add_media_codec_options(m);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new_from_media (m);
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_release (m);
    libvlc_media_player_play( mp );

    pthread_mutex_lock(&monitor->doneMutex);
    while (!monitor->length_changed)
        pthread_cond_wait(&monitor->doneCondVar, &monitor->doneMutex);
    pthread_mutex_unlock(&monitor->doneMutex);

    length = libvlc_media_player_get_length( mp );
    libvlc_media_player_stop( mp );
    libvlc_media_player_release( mp );

end:
    pthread_mutex_destroy(&monitor->doneMutex);
    pthread_cond_destroy(&monitor->doneCondVar);
    free(monitor);

    return length;
}

jboolean Java_org_videolan_vlc_android_LibVLC_hasMediaPlayer(JNIEnv *env, jobject thiz)
{
    return !!getMediaPlayer(env, thiz);
}

jboolean Java_org_videolan_vlc_android_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_playing(mp);
    return 0;
}

jboolean Java_org_videolan_vlc_android_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_seekable(mp);
    return 0;
}

void Java_org_videolan_vlc_android_LibVLC_play(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_play(mp);
}

void Java_org_videolan_vlc_android_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_pause(mp);
}

void Java_org_videolan_vlc_android_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_stop(mp);
}

jint Java_org_videolan_vlc_android_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_volume(mp);
    return -1;
}

jint Java_org_videolan_vlc_android_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume(mp, (int) volume);
    return -1;
}

jlong Java_org_videolan_vlc_android_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_time(mp);
    return -1;
}

void Java_org_videolan_vlc_android_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_time(mp, time);
}

jfloat Java_org_videolan_vlc_android_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jfloat) libvlc_media_player_get_position(mp);
    return -1;
}

void Java_org_videolan_vlc_android_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_position(mp, pos);
}

jlong Java_org_videolan_vlc_android_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_media_player_get_length(mp);
    return -1;
}

jstring Java_org_videolan_vlc_android_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_org_videolan_vlc_android_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_vlc_android_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}
