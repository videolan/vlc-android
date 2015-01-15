/*****************************************************************************
 * libvlcjni.c
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <vlc/vlc.h>
#include <vlc_common.h>
#include <vlc_url.h>

#include <jni.h>

#include <android/api-level.h>

#include "libvlcjni.h"
#include "aout.h"
#include "vout.h"
#include "utils.h"
#include "native_crash_handler.h"

#define VOUT_ANDROID_SURFACE 0
#define VOUT_OPENGLES2       1
#define VOUT_ANDROID_WINDOW  2

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

#define VLC_JNI_VERSION JNI_VERSION_1_2

#define THREAD_NAME "libvlcjni"
int jni_attach_thread(JNIEnv **env, const char *thread_name);
void jni_detach_thread();
int jni_get_env(JNIEnv **env);

static void add_media_options(libvlc_media_t *p_md, JNIEnv *env, jobjectArray mediaOptions)
{
    int stringCount = (*env)->GetArrayLength(env, mediaOptions);
    for(int i = 0; i < stringCount; i++)
    {
        jstring option = (jstring)(*env)->GetObjectArrayElement(env, mediaOptions, i);
        const char* p_st = (*env)->GetStringUTFChars(env, option, 0);
        libvlc_media_add_option(p_md, p_st); // option
        (*env)->ReleaseStringUTFChars(env, option, p_st);
    }
}

libvlc_media_t *new_media(JNIEnv *env, jobject thiz, jstring fileLocation, bool noOmx, bool noVideo)
{
    libvlc_instance_t *libvlc = getLibVlcInstance(env, thiz);
    jboolean isCopy;
    const char *psz_location = (*env)->GetStringUTFChars(env, fileLocation, &isCopy);
    libvlc_media_t *p_md = libvlc_media_new_location(libvlc, psz_location);
    (*env)->ReleaseStringUTFChars(env, fileLocation, psz_location);
    if (!p_md)
        return NULL;

    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getMediaOptions", "(ZZ)[Ljava/lang/String;");
    if (methodId != NULL)
    {
        jobjectArray mediaOptions = (*env)->CallObjectMethod(env, thiz, methodId, noOmx, noVideo);
        if (mediaOptions != NULL)
        {
            add_media_options(p_md, env, mediaOptions);
            (*env)->DeleteLocalRef(env, mediaOptions);
        }
    }
    return p_md;
}

libvlc_instance_t *getLibVlcInstance(JNIEnv *env, jobject thiz)
{
    return (libvlc_instance_t*)(intptr_t)getLong(env, thiz, "mLibVlcInstance");
}

libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_player_t*)(intptr_t)getLong(env, thiz, "mInternalMediaPlayerInstance");
}


static void releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t* p_mp = getMediaPlayer(env, thiz);
    if (p_mp)
    {
        libvlc_media_player_stop(p_mp);
        libvlc_media_player_release(p_mp);
        setLong(env, thiz, "mInternalMediaPlayerInstance", 0);
    }
}

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
static JavaVM *myVm;

static jobject eventHandlerInstance = NULL;

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;

    bool isAttached = false;

    if (eventHandlerInstance == NULL)
        return;

    if (jni_get_env(&env) < 0) {
        if (jni_attach_thread(&env, THREAD_NAME) < 0)
            return;
        isAttached = true;
    }

    /* Creating the bundle in C allows us to subscribe to more events
     * and get better flexibility for each event. For example, we can
     * have totally different types of data for each event, instead of,
     * for example, only an integer and/or string.
     */
    jclass clsBundle = (*env)->FindClass(env, "android/os/Bundle");
    jmethodID clsCtor = (*env)->GetMethodID(env, clsBundle, "<init>", "()V" );
    jobject bundle = (*env)->NewObject(env, clsBundle, clsCtor);

    jmethodID putInt = (*env)->GetMethodID(env, clsBundle, "putInt", "(Ljava/lang/String;I)V" );
    jmethodID putLong = (*env)->GetMethodID(env, clsBundle, "putLong", "(Ljava/lang/String;J)V" );
    jmethodID putFloat = (*env)->GetMethodID(env, clsBundle, "putFloat", "(Ljava/lang/String;F)V" );
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );

    if (ev->type == libvlc_MediaPlayerPositionChanged) {
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putFloat, sData, ev->u.media_player_position_changed.new_position);
        (*env)->DeleteLocalRef(env, sData);
    } else if (ev->type == libvlc_MediaPlayerTimeChanged) {
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putLong, sData, ev->u.media_player_time_changed.new_time);
        (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaPlayerVout) {
        /* For determining the vout/ES track change */
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putInt, sData, ev->u.media_player_vout.new_count);
        (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaListItemAdded ||
              ev->type == libvlc_MediaListItemDeleted ) {
        jstring item_uri = (*env)->NewStringUTF(env, "item_uri");
        jstring item_index = (*env)->NewStringUTF(env, "item_index");
        char* mrl = libvlc_media_get_mrl(
            ev->type == libvlc_MediaListItemAdded ?
            ev->u.media_list_item_added.item :
            ev->u.media_list_item_deleted.item
            );
        jstring item_uri_value = (*env)->NewStringUTF(env, mrl);
        jint item_index_value;
        if(ev->type == libvlc_MediaListItemAdded)
            item_index_value = ev->u.media_list_item_added.index;
        else
            item_index_value = ev->u.media_list_item_deleted.index;

        (*env)->CallVoidMethod(env, bundle, putString, item_uri, item_uri_value);
        (*env)->CallVoidMethod(env, bundle, putInt, item_index, item_index_value);

        (*env)->DeleteLocalRef(env, item_uri);
        (*env)->DeleteLocalRef(env, item_uri_value);
        (*env)->DeleteLocalRef(env, item_index);
        free(mrl);
    }

    /* Get the object class */
    jclass cls = (*env)->GetObjectClass(env, eventHandlerInstance);
    if (!cls) {
        LOGE("EventHandler: failed to get class reference");
        goto end;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (methodID) {
        (*env)->CallVoidMethod(env, eventHandlerInstance, methodID, ev->type, bundle);
    } else {
        LOGE("EventHandler: failed to get the callback method");
    }

end:
    (*env)->DeleteLocalRef(env, bundle);
    if (isAttached)
        jni_detach_thread();
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;

    pthread_mutex_init(&vout_android_lock, NULL);
    pthread_cond_init(&vout_android_surf_attached, NULL);

    LOGD("JNI interface loaded.");
    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    pthread_mutex_destroy(&vout_android_lock);
    pthread_cond_destroy(&vout_android_surf_attached);
}

int jni_attach_thread(JNIEnv **env, const char *thread_name)
{
    JavaVMAttachArgs args;
    jint result;

    args.version = VLC_JNI_VERSION;
    args.name = thread_name;
    args.group = NULL;

    result = (*myVm)->AttachCurrentThread(myVm, env, &args);
    return result == JNI_OK ? 0 : -1;
}

void jni_detach_thread()
{
    (*myVm)->DetachCurrentThread(myVm);
}

int jni_get_env(JNIEnv **env)
{
    return (*myVm)->GetEnv(myVm, (void **)env, VLC_JNI_VERSION) == JNI_OK ? 0 : -1;
}

// FIXME: use atomics
static bool verbosity;

void Java_org_videolan_libvlc_LibVLC_nativeInit(JNIEnv *env, jobject thiz)
{
    //only use OpenSLES if java side says we can
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getAout", "()I");
    int aout = (*env)->CallIntMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "getVout", "()I");
    int vout = (*env)->CallIntMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "timeStretchingEnabled", "()Z");
    bool enable_time_stretch = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "frameSkipEnabled", "()Z");
    bool enable_frame_skip = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "getDeblocking", "()I");
    int deblocking = (*env)->CallIntMethod(env, thiz, methodId);
    char deblockstr[2];
    snprintf(deblockstr, sizeof(deblockstr), "%d", deblocking);
    LOGD("Using deblocking level %d", deblocking);

    methodId = (*env)->GetMethodID(env, cls, "getNetworkCaching", "()I");
    int networkCaching = (*env)->CallIntMethod(env, thiz, methodId);
    char networkCachingstr[25];
    if(networkCaching > 0) {
        snprintf(networkCachingstr, sizeof(networkCachingstr), "--network-caching=%d", networkCaching);
        LOGD("Using network caching of %d ms", networkCaching);
    }

    methodId = (*env)->GetMethodID(env, cls, "getHttpReconnect", "()Z");
    bool enable_http_reconnect = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "getChroma", "()Ljava/lang/String;");
    jstring chroma = (*env)->CallObjectMethod(env, thiz, methodId);
    const char *chromastr = (*env)->GetStringUTFChars(env, chroma, 0);
    LOGD("Chroma set to \"%s\"", chromastr);

    methodId = (*env)->GetMethodID(env, cls, "getSubtitlesEncoding", "()Ljava/lang/String;");
    jstring subsencoding = (*env)->CallObjectMethod(env, thiz, methodId);
    const char *subsencodingstr = (*env)->GetStringUTFChars(env, subsencoding, 0);
    LOGD("Subtitle encoding set to \"%s\"", subsencodingstr);

    methodId = (*env)->GetMethodID(env, cls, "isVerboseMode", "()Z");
    verbosity = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "isDirectRendering", "()Z");
    bool direct_rendering = (*env)->CallBooleanMethod(env, thiz, methodId);
    /* With the MediaCodec opaque mode we cannot use the OpenGL ES vout. */
    if (direct_rendering)
        vout = VOUT_ANDROID_WINDOW;

    methodId = (*env)->GetMethodID(env, cls, "getCachePath", "()Ljava/lang/String;");
    jstring cachePath = (*env)->CallObjectMethod(env, thiz, methodId);
    if (cachePath) {
        const char *cache_path = (*env)->GetStringUTFChars(env, cachePath, 0);
        setenv("DVDCSS_CACHE", cache_path, 1);
        (*env)->ReleaseStringUTFChars(env, cachePath, cache_path);
    }

#define MAX_ARGV 18
    const char *argv[MAX_ARGV];
    int argc = 0;

    /* CPU intensive plugin, setting for slow devices */
    argv[argc++] = enable_time_stretch ? "--audio-time-stretch" : "--no-audio-time-stretch";
    /* avcodec-skiploopfilter */
    argv[argc++] = "--avcodec-skiploopfilter";
    argv[argc++] = deblockstr;
    /* avcodec-skip-frame */
    argv[argc++] = "--avcodec-skip-frame";
    argv[argc++] = enable_frame_skip ? "2" : "0";
    /* avcodec-skip-idct */
    argv[argc++] = "--avcodec-skip-idct";
    argv[argc++] = enable_frame_skip ? "2" : "0";
    /* Remove me when UTF-8 is enforced by law */
    argv[argc++] = "--subsdec-encoding";
    argv[argc++] = subsencodingstr;
    /* Enable statistics */
    argv[argc++] = "--stats";
    /* XXX: why can't the default be fine ? #7792 */
    if (networkCaching > 0)
        argv[argc++] = networkCachingstr;
    /* Android audio API */
    argv[argc++] = aout == AOUT_OPENSLES ? "--aout=opensles" :
        (aout == AOUT_AUDIOTRACK ? "--aout=android_audiotrack" : "--aout=dummy");
    /* Android video API  */
    argv[argc++] = vout == VOUT_ANDROID_WINDOW ? "--vout=androidwindow" :
        (vout == VOUT_OPENGLES2 ? "--vout=gles2" : "--vout=androidsurface");
    /* chroma */
    argv[argc++] = "--androidsurface-chroma";
    argv[argc++] = chromastr != NULL && chromastr[0] != 0 ? chromastr : "RV32";
    /* direct rendering */
    if (!direct_rendering) {
        argv[argc++] = "--no-mediacodec-dr";
#ifdef HAVE_IOMX_DR
        argv[argc++] = "--no-omxil-dr";
#endif
    }
    /* Reconnect on lost HTTP streams, e.g. network change */
    if (enable_http_reconnect)
        argv[argc++] = "--http-reconnect";

    assert(MAX_ARGV >= argc);
    libvlc_instance_t *instance = libvlc_new(argc, argv);

    setLong(env, thiz, "mLibVlcInstance", (jlong)(intptr_t) instance);

    (*env)->ReleaseStringUTFChars(env, chroma, chromastr);
    (*env)->ReleaseStringUTFChars(env, subsencoding, subsencodingstr);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "org/videolan/libvlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);

    libvlc_log_set(instance, debug_log, &verbosity);

    init_native_crash_handler(env, thiz);
}

void Java_org_videolan_libvlc_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    destroy_native_crash_handler(env);

    releaseMediaPlayer(env, thiz);
    jlong libVlcInstance = getLong(env, thiz, "mLibVlcInstance");
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*)(intptr_t) libVlcInstance;
    libvlc_log_unset(instance);
    libvlc_release(instance);

    setLong(env, thiz, "mLibVlcInstance", 0);
}

void Java_org_videolan_libvlc_LibVLC_detachEventHandler(JNIEnv *env, jobject thiz)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }
}

void Java_org_videolan_libvlc_LibVLC_setEventHandler(JNIEnv *env, jobject thiz, jobject eventHandler)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }

    eventHandlerInstance = getEventHandlerReference(env, thiz, eventHandler);
}

void Java_org_videolan_libvlc_LibVLC_playMRL(JNIEnv *env, jobject thiz,
                                             jstring mrl, jobjectArray mediaOptions)
{
    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    libvlc_instance_t *p_instance = getLibVlcInstance(env, thiz);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new(p_instance);
    libvlc_media_player_set_video_title_display(mp, libvlc_position_disable, 0);
    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz); // freed in aout_close

    // If AOUT_AUDIOTRACK_JAVA, use amem
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getAout", "()I");
    if ( (*env)->CallIntMethod(env, thiz, methodId) == AOUT_AUDIOTRACK_JAVA )
    {
        libvlc_audio_set_callbacks(mp, aout_play, aout_pause, NULL, NULL, NULL,
                                   (void*) myJavaLibVLC);
        libvlc_audio_set_format_callbacks(mp, aout_open, aout_close);
    }

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaPlayerPlaying,
        libvlc_MediaPlayerPaused,
        libvlc_MediaPlayerEndReached,
        libvlc_MediaPlayerStopped,
        libvlc_MediaPlayerVout,
        libvlc_MediaPlayerPositionChanged,
        libvlc_MediaPlayerTimeChanged,
        libvlc_MediaPlayerEncounteredError
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);

    /* Keep a pointer to this media player */
    setLong(env, thiz, "mInternalMediaPlayerInstance", (jlong)(intptr_t)mp);

    cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodID = (*env)->GetMethodID(env, cls, "applyEqualizer", "()V");
    (*env)->CallVoidMethod(env, thiz, methodID);

    const char* p_mrl = (*env)->GetStringUTFChars(env, mrl, 0);

    libvlc_media_t* p_md = libvlc_media_new_location(p_instance, p_mrl);
    /* media options */
    if (mediaOptions != NULL)
        add_media_options(p_md, env, mediaOptions);

    (*env)->ReleaseStringUTFChars(env, mrl, p_mrl);

    /* Connect the media event manager. */
    libvlc_event_manager_t *ev_media = libvlc_media_event_manager(p_md);
    static const libvlc_event_type_t mp_media_events[] = {
        libvlc_MediaParsedChanged,
        libvlc_MediaMetaChanged,
    };
    for(int i = 0; i < (sizeof(mp_media_events) / sizeof(*mp_media_events)); i++)
        libvlc_event_attach(ev_media, mp_media_events[i], vlc_event_callback, myVm);

    libvlc_media_player_set_media(mp, p_md);
    libvlc_media_player_play(mp);
}

jfloat Java_org_videolan_libvlc_LibVLC_getRate(JNIEnv *env, jobject thiz) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        return libvlc_media_player_get_rate(mp);
    else
        return 1.00;
}

void Java_org_videolan_libvlc_LibVLC_setRate(JNIEnv *env, jobject thiz, jfloat rate) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        libvlc_media_player_set_rate(mp, rate);
}

jboolean Java_org_videolan_libvlc_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_playing(mp);
    else
        return 0;
}

jboolean Java_org_videolan_libvlc_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_seekable(mp);
    return 0;
}

void Java_org_videolan_libvlc_LibVLC_play(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_play(mp);
}

void Java_org_videolan_libvlc_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_pause(mp);
}

void Java_org_videolan_libvlc_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_stop(mp);
}

jint Java_org_videolan_libvlc_LibVLC_getPlayerState(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_media_player_get_state(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_volume(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume(mp, (int) volume);
    return -1;
}

jlong Java_org_videolan_libvlc_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_time(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_time(mp, time);
}

jfloat Java_org_videolan_libvlc_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jfloat) libvlc_media_player_get_position(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_position(mp, pos);
}

jlong Java_org_videolan_libvlc_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_media_player_get_length(mp);
    return -1;
}

jstring Java_org_videolan_libvlc_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_org_videolan_libvlc_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_libvlc_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}

jstring Java_org_videolan_libvlc_LibVLC_getMeta(JNIEnv *env, jobject thiz, int meta)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    char *psz_meta;
    jstring string = NULL;
    if (!mp)
        return NULL;

    libvlc_media_t *p_mp = libvlc_media_player_get_media(mp);
    if (!p_mp)
        return NULL;

    psz_meta = libvlc_media_get_meta(p_mp, meta);
    if (psz_meta) {
        string = (*env)->NewStringUTF(env, psz_meta);
        free(psz_meta);
    }
    return string;
}

jint Java_org_videolan_libvlc_LibVLC_getTitle(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_title(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_setTitle(JNIEnv *env, jobject thiz, jint title)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_title(mp, title);
}

jint Java_org_videolan_libvlc_LibVLC_getChapterCountForTitle(JNIEnv *env, jobject thiz, jint title)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_chapter_count_for_title(mp, title);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_getTitleCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_title_count(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_playerNavigate(JNIEnv *env, jobject thiz, jint navigate)
{
    unsigned nav = navigate;
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_navigate(mp, (unsigned) nav);
}

static int expand_media_internal(JNIEnv *env, libvlc_instance_t* p_instance, jobject arrayList, libvlc_media_t* p_md) {
    if(!p_md) {
        return -1;
    }
    libvlc_media_list_t* p_subitems = libvlc_media_subitems(p_md);
    libvlc_media_release(p_md);
    if(p_subitems) {
        // Expand any subitems if needed
        int subitem_count = libvlc_media_list_count(p_subitems);
        if(subitem_count > 0) {
            LOGD("Found %d subitems, expanding", subitem_count);
            jclass arrayListClass; jmethodID methodAdd;
            arrayListGetIDs(env, &arrayListClass, &methodAdd, NULL);

            for(int i = subitem_count - 1; i >= 0; i--) {
                libvlc_media_t* p_subitem = libvlc_media_list_item_at_index(p_subitems, i);
                char* p_subitem_uri = libvlc_media_get_mrl(p_subitem);
                arrayListStringAdd(env, arrayListClass, methodAdd, arrayList, p_subitem_uri);
                free(p_subitem_uri);
            }
        }
        libvlc_media_list_release(p_subitems);
        if(subitem_count > 0) {
            return 0;
        } else {
            return -1;
        }
    } else {
        return -1;
    }
}

jint Java_org_videolan_libvlc_LibVLC_expandMedia(JNIEnv *env, jobject thiz, jobject children) {
    return (jint)expand_media_internal(env,
        getLibVlcInstance(env, thiz),
        children,
        (libvlc_media_t*)libvlc_media_player_get_media(getMediaPlayer(env, thiz)));
}

// TODO: remove static variables
static int i_window_width = 0;
static int i_window_height = 0;

void Java_org_videolan_libvlc_LibVLC_setWindowSize(JNIEnv *env, jobject thiz, jint width, jint height)
{
    pthread_mutex_lock(&vout_android_lock);
    i_window_width = width;
    i_window_height = height;
    pthread_mutex_unlock(&vout_android_lock);
}

int jni_GetWindowSize(int *width, int *height)
{
    pthread_mutex_lock(&vout_android_lock);
    *width = i_window_width;
    *height = i_window_height;
    pthread_mutex_unlock(&vout_android_lock);
    return 0;
}
