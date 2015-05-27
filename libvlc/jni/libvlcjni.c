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
#include <stdlib.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <vlc/vlc.h>

#include <jni.h>

#include <android/api-level.h>

#include "libvlcjni-modules.h"
#include "vout.h"
#include "utils.h"
#include "native_crash_handler.h"
#include "std_logger.h"

#define VOUT_ANDROID_SURFACE 0
#define VOUT_OPENGLES2       1
#define VOUT_ANDROID_WINDOW  2

#define AOUT_AUDIOTRACK      0
#define AOUT_OPENSLES        1

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

struct fields fields;

#define VLC_JNI_VERSION JNI_VERSION_1_2

#define THREAD_NAME "libvlcjni"
JNIEnv *jni_get_env(const char *name);

libvlc_instance_t *getLibVlcInstance(JNIEnv *env, jobject thiz)
{
    return (libvlc_instance_t*)(intptr_t)getLong(env, thiz, "mLibVlcInstance");
}

jobject eventHandlerInstance = NULL;


/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
static JavaVM *myVm;
static pthread_key_t jni_env_key;

/* This function is called when a thread attached to the Java VM is canceled or
 * exited */
static void jni_detach_thread(void *data)
{
    //JNIEnv *env = data;
    (*myVm)->DetachCurrentThread(myVm);
}

JNIEnv *jni_get_env(const char *name)
{
    JNIEnv *env;

    env = pthread_getspecific(jni_env_key);
    if (env == NULL) {
        /* if GetEnv returns JNI_OK, the thread is already attached to the
         * JavaVM, so we are already in a java thread, and we don't have to
         * setup any destroy callbacks */
        if ((*myVm)->GetEnv(myVm, (void **)&env, VLC_JNI_VERSION) != JNI_OK)
        {
            /* attach the thread to the Java VM */
            JavaVMAttachArgs args;
            jint result;

            args.version = VLC_JNI_VERSION;
            args.name = name;
            args.group = NULL;

            if ((*myVm)->AttachCurrentThread(myVm, &env, &args) != JNI_OK)
                return NULL;

            /* Set the attached env to the thread-specific data area (TSD) */
            if (pthread_setspecific(jni_env_key, env) != 0)
            {
                (*myVm)->DetachCurrentThread(myVm);
                return NULL;
            }
        }
    }

    return env;
}

#ifndef NDEBUG
static std_logger *p_std_logger = NULL;
#endif

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv* env = NULL;
    // Keep a reference on the Java VM.
    myVm = vm;

    if ((*vm)->GetEnv(vm, (void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return -1;

    /* Create a TSD area and setup a destroy callback when a thread that
     * previously set the jni_env_key is canceled or exited */
    if (pthread_key_create(&jni_env_key, jni_detach_thread) != 0)
        return -1;

    pthread_mutex_init(&vout_android_lock, NULL);
    pthread_cond_init(&vout_android_surf_attached, NULL);

#ifndef NDEBUG
    p_std_logger = std_logger_Open("VLC-std");
#endif

#define GET_CLASS(clazz, str, b_globlal) do { \
    (clazz) = (*env)->FindClass(env, (str)); \
    if (!(clazz)) { \
        LOGE("FindClass(%s) failed", (str)); \
        return -1; \
    } \
    if (b_globlal) { \
        (clazz) = (jclass) (*env)->NewGlobalRef(env, (clazz)); \
        if (!(clazz)) { \
            LOGE("NewGlobalRef(%s) failed", (str)); \
            return -1; \
        } \
    } \
} while (0)

#define GET_ID(get, id, clazz, str, args) do { \
    (id) = (*env)->get(env, (clazz), (str), (args)); \
    if (!(id)) { \
        LOGE(#get"(%s) failed", (str)); \
        return -1; \
    } \
} while (0)

    jclass Version_clazz;
    jfieldID SDK_INT_fieldID;

    GET_CLASS(Version_clazz, "android/os/Build$VERSION", false);
    GET_ID(GetStaticFieldID, SDK_INT_fieldID, Version_clazz, "SDK_INT", "I");
    fields.SDK_INT = (*env)->GetStaticIntField(env, Version_clazz,
                                               SDK_INT_fieldID);

    GET_CLASS(fields.IllegalStateException.clazz,
              "java/lang/IllegalStateException", true);
    GET_CLASS(fields.IllegalArgumentException.clazz,
              "java/lang/IllegalArgumentException", true);
    GET_CLASS(fields.String.clazz,
              "java/lang/String", true);
    GET_CLASS(fields.FileDescriptor.clazz,
              "java/io/FileDescriptor", true);
    GET_ID(GetFieldID,
           fields.FileDescriptor.descriptorID,
           fields.FileDescriptor.clazz,
           "descriptor", "I");
    GET_CLASS(fields.LibVLC.clazz,
              "org/videolan/libvlc/LibVLC", true);
    GET_CLASS(fields.VLCObject.clazz,
              "org/videolan/libvlc/VLCObject", true);
    GET_CLASS(fields.Media.clazz,
              "org/videolan/libvlc/Media", true);
    GET_CLASS(fields.Media.Track.clazz,
              "org/videolan/libvlc/Media$Track", true);

    GET_ID(GetStaticMethodID,
           fields.LibVLC.onNativeCrashID,
           fields.LibVLC.clazz,
           "onNativeCrash", "()V");

    GET_ID(GetFieldID,
           fields.VLCObject.mInstanceID,
           fields.VLCObject.clazz,
           "mInstance", "J");

    GET_ID(GetMethodID,
           fields.VLCObject.dispatchEventFromNativeID,
           fields.VLCObject.clazz,
           "dispatchEventFromNative", "(IJJ)V");

    if (fields.SDK_INT <= 10)
    {
        LOGE("fields.SDK_INT is less than 10: using compat WeakReference");
        GET_ID(GetMethodID,
               fields.VLCObject.getWeakReferenceID,
               fields.VLCObject.clazz,
               "getWeakReference", "()Ljava/lang/Object;");
        GET_ID(GetStaticMethodID,
               fields.VLCObject.dispatchEventFromWeakNativeID,
               fields.VLCObject.clazz,
               "dispatchEventFromWeakNative", "(Ljava/lang/Object;IJJ)V");
    } else
    {
        fields.VLCObject.getWeakReferenceID = NULL;
        fields.VLCObject.dispatchEventFromWeakNativeID = NULL;
    }

    GET_ID(GetStaticMethodID,
           fields.Media.createAudioTrackFromNativeID,
           fields.Media.clazz,
           "createAudioTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;II)"
           "Lorg/videolan/libvlc/Media$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createVideoTrackFromNativeID,
           fields.Media.clazz,
           "createVideoTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;IIIIII)"
           "Lorg/videolan/libvlc/Media$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createSubtitleTrackFromNativeID,
           fields.Media.clazz,
           "createSubtitleTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)"
           "Lorg/videolan/libvlc/Media$Track;");

#undef GET_CLASS
#undef GET_ID

    init_native_crash_handler();

    LOGD("JNI interface loaded.");
    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;

    pthread_mutex_destroy(&vout_android_lock);
    pthread_cond_destroy(&vout_android_surf_attached);

    destroy_native_crash_handler();

    if ((*vm)->GetEnv(vm, (void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return;

    (*env)->DeleteGlobalRef(env, fields.IllegalStateException.clazz);
    (*env)->DeleteGlobalRef(env, fields.IllegalArgumentException.clazz);
    (*env)->DeleteGlobalRef(env, fields.String.clazz);
    (*env)->DeleteGlobalRef(env, fields.VLCObject.clazz);
    (*env)->DeleteGlobalRef(env, fields.Media.clazz);

    pthread_key_delete(jni_env_key);

#ifndef NDEBUG
    std_logger_Close(p_std_logger);
#endif
}

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
    bool b_verbose = (*env)->CallBooleanMethod(env, thiz, methodId);

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

    methodId = (*env)->GetMethodID(env, cls, "isHdmiAudioEnabled", "()Z");
    bool hdmi_audio = (*env)->CallBooleanMethod(env, thiz, methodId);

#define MAX_ARGV 22
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
    if (hdmi_audio) {
        argv[argc++] = "--spdif";
        argv[argc++] = "--audiotrack-audio-channels";
        argv[argc++] = "8"; // 7.1 maximum
    }
    argv[argc++] = b_verbose ? "-vvv" : "-vv";

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
}

void Java_org_videolan_libvlc_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    jlong libVlcInstance = getLong(env, thiz, "mLibVlcInstance");
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*)(intptr_t) libVlcInstance;
    libvlc_release(instance);

    setLong(env, thiz, "mLibVlcInstance", 0);
}

/* TODO REMOVE */
void Java_org_videolan_libvlc_LibVLC_detachEventHandler(JNIEnv *env, jobject thiz)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }
}

/* TODO REMOVE */
void Java_org_videolan_libvlc_LibVLC_setEventHandler(JNIEnv *env, jobject thiz, jobject eventHandler)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }

    eventHandlerInstance = getEventHandlerReference(env, thiz, eventHandler);
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

/* used by opensles module */
int aout_get_native_sample_rate(void)
{
    JNIEnv *p_env;
    if (!(p_env = jni_get_env(THREAD_NAME)))
        return -1;
    jclass cls = (*p_env)->FindClass (p_env, "android/media/AudioTrack");
    jmethodID method = (*p_env)->GetStaticMethodID (p_env, cls, "getNativeOutputSampleRate", "(I)I");
    int sample_rate = (*p_env)->CallStaticIntMethod (p_env, cls, method, 3); // AudioManager.STREAM_MUSIC
    return sample_rate;
}
