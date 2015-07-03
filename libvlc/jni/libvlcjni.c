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
#include "libvlcjni-vlcobject.h"
#include "utils.h"
#include "native_crash_handler.h"
#include "std_logger.h"

struct fields fields;

#define VLC_JNI_VERSION JNI_VERSION_1_2

#define THREAD_NAME "libvlcjni"
JNIEnv *jni_get_env(const char *name);

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
static JavaVM *myVm;

JavaVM *
libvlc_get_jvm()
{
    return myVm;
}

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
    GET_CLASS(fields.MediaPlayer.clazz,
              "org/videolan/libvlc/MediaPlayer", true);
    GET_CLASS(fields.MediaPlayer.Title.clazz,
              "org/videolan/libvlc/MediaPlayer$Title", true);
    GET_CLASS(fields.MediaPlayer.Chapter.clazz,
              "org/videolan/libvlc/MediaPlayer$Chapter", true);
    GET_CLASS(fields.MediaPlayer.TrackDescription.clazz,
              "org/videolan/libvlc/MediaPlayer$TrackDescription", true);
    GET_CLASS(fields.MediaPlayer.Equalizer.clazz,
              "org/videolan/libvlc/MediaPlayer$Equalizer", true);

    GET_ID(GetStaticMethodID,
           fields.LibVLC.onNativeCrashID,
           fields.LibVLC.clazz,
           "onNativeCrash", "()V");

    GET_ID(GetFieldID,
           fields.VLCObject.mInstanceID,
           fields.VLCObject.clazz,
           "mInstance", "J");

    GET_ID(GetFieldID,
           fields.MediaPlayer.Equalizer.mInstanceID,
           fields.MediaPlayer.Equalizer.clazz,
           "mInstance", "J");

    GET_ID(GetMethodID,
           fields.VLCObject.dispatchEventFromNativeID,
           fields.VLCObject.clazz,
           "dispatchEventFromNative", "(IJF)V");

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
               "dispatchEventFromWeakNative", "(Ljava/lang/Object;IJF)V");
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

    GET_ID(GetStaticMethodID,
           fields.MediaPlayer.createTitleFromNativeID,
           fields.MediaPlayer.clazz,
           "createTitleFromNative",
           "(JLjava/lang/String;Z)Lorg/videolan/libvlc/MediaPlayer$Title;");

    GET_ID(GetStaticMethodID,
           fields.MediaPlayer.createChapterFromNativeID,
           fields.MediaPlayer.clazz,
           "createChapterFromNative",
           "(JJLjava/lang/String;)Lorg/videolan/libvlc/MediaPlayer$Chapter;");

    GET_ID(GetStaticMethodID,
           fields.MediaPlayer.createTrackDescriptionFromNativeID,
           fields.MediaPlayer.clazz,
           "createTrackDescriptionFromNative",
           "(ILjava/lang/String;)Lorg/videolan/libvlc/MediaPlayer$TrackDescription;");

#undef GET_CLASS
#undef GET_ID

    init_native_crash_handler();

    LOGD("JNI interface loaded.");
    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;

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

void Java_org_videolan_libvlc_LibVLC_nativeNew(JNIEnv *env, jobject thiz, jobjectArray jstringArray)
{
    vlcjni_object *p_obj = NULL;
    libvlc_instance_t *p_libvlc = NULL;
    jstring *strings = NULL;
    const char **argv = NULL;
    int argc = 0;

    if (jstringArray)
    {
        argc = (*env)->GetArrayLength(env, jstringArray);

        argv = malloc(argc * sizeof(const char *));
        strings = malloc(argc * sizeof(jstring));
        if (!argv || !strings)
        {
            argc = 0;
            goto error;
        }
        for (int i = 0; i < argc; ++i)
        {
            strings[i] = (*env)->GetObjectArrayElement(env, jstringArray, i);
            if (!strings[i])
            {
                argc = i;
                goto error;
            }
            argv[i] = (*env)->GetStringUTFChars(env, strings[i], 0);
            if (!argv)
            {
                argc = i;
                goto error;
            }
        }
    }

    p_libvlc = libvlc_new(argc, argv);

error:

    if (jstringArray)
    {
        for (int i = 0; i < argc; ++i)
        {
            (*env)->ReleaseStringUTFChars(env, strings[i], argv[i]);
            (*env)->DeleteLocalRef(env, strings[i]);
        }
    }
    free(argv);
    free(strings);

    if (!p_libvlc)
    {
        throw_IllegalStateException(env, "can't create LibVLC instance");
        return;
    }

    p_obj = VLCJniObject_newFromLibVlc(env, thiz, NULL);
    if (!p_obj)
    {
        libvlc_release(p_libvlc);
        return;
    }
    p_obj->u.p_libvlc = p_libvlc;
}

void Java_org_videolan_libvlc_LibVLC_nativeRelease(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    libvlc_release(p_obj->u.p_libvlc);
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

/* TODO REMOVE */
static jobject error_obj = NULL;
pthread_mutex_t error_obj_lock;

void Java_org_videolan_libvlc_LibVLC_nativeSetOnHardwareAccelerationError(JNIEnv *env, jobject thiz, jobject error_obj_)
{
    pthread_mutex_lock(&error_obj_lock);

    if (error_obj != NULL)
        (*env)->DeleteGlobalRef(env, error_obj);
    error_obj = error_obj_ ? (*env)->NewGlobalRef(env, error_obj_) : NULL;
    pthread_mutex_unlock(&error_obj_lock);
}

void jni_EventHardwareAccelerationError()
{
    JNIEnv *env;

    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    pthread_mutex_lock(&error_obj_lock);
    if (error_obj == NULL) {
        pthread_mutex_unlock(&error_obj_lock);
        return;
    }

    jclass cls = (*env)->GetObjectClass(env, error_obj);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "eventHardwareAccelerationError", "()V");
    (*env)->CallVoidMethod(env, error_obj, methodId);

    (*env)->DeleteLocalRef(env, cls);
    pthread_mutex_unlock(&error_obj_lock);
}
