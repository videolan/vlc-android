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

#include "libvlcjni-vlcobject.h"
#include "utils.h"
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
        jclass local_class = (clazz); \
        (clazz) = (jclass) (*env)->NewGlobalRef(env, (clazz)); \
        (*env)->DeleteLocalRef(env, local_class); \
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
    (*env)->DeleteLocalRef(env, Version_clazz);

    GET_CLASS(fields.IllegalStateException.clazz,
              "java/lang/IllegalStateException", true);
    GET_CLASS(fields.IllegalArgumentException.clazz,
              "java/lang/IllegalArgumentException", true);
    GET_CLASS(fields.RuntimeException.clazz,
              "java/lang/RuntimeException", true);
    GET_CLASS(fields.OutOfMemoryError.clazz,
              "java/lang/OutOfMemoryError", true);
    GET_CLASS(fields.String.clazz,
              "java/lang/String", true);
    GET_CLASS(fields.FileDescriptor.clazz,
              "java/io/FileDescriptor", true);
    GET_ID(GetFieldID,
           fields.FileDescriptor.descriptorID,
           fields.FileDescriptor.clazz,
           "descriptor", "I");
    GET_CLASS(fields.VLCObject.clazz,
              "org/videolan/libvlc/VLCObject", true);
    GET_CLASS(fields.Media.clazz,
              "org/videolan/libvlc/Media", true);
    GET_CLASS(fields.Media.Track.clazz,
              "org/videolan/libvlc/interfaces/IMedia$Track", true);
    GET_CLASS(fields.Media.Slave.clazz,
              "org/videolan/libvlc/interfaces/IMedia$Slave", true);
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
    GET_CLASS(fields.MediaDiscoverer.clazz,
              "org/videolan/libvlc/MediaDiscoverer", true);
    GET_CLASS(fields.MediaDiscoverer.Description.clazz,
              "org/videolan/libvlc/MediaDiscoverer$Description", true);
    GET_CLASS(fields.RendererDiscoverer.clazz,
              "org/videolan/libvlc/RendererDiscoverer", true);
    GET_CLASS(fields.RendererDiscoverer.Description.clazz,
              "org/videolan/libvlc/RendererDiscoverer$Description", true);
    GET_CLASS(fields.Dialog.clazz,
              "org/videolan/libvlc/Dialog", true);

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
           "dispatchEventFromNative", "(IJJFLjava/lang/String;)V");

    GET_ID(GetStaticMethodID,
           fields.Media.createAudioTrackFromNativeID,
           fields.Media.clazz,
           "createAudioTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;II)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createVideoTrackFromNativeID,
           fields.Media.clazz,
           "createVideoTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;IIIIIIII)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createSubtitleTrackFromNativeID,
           fields.Media.clazz,
           "createSubtitleTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createUnknownTrackFromNativeID,
           fields.Media.clazz,
           "createUnknownTrackFromNative",
           "(Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;Ljava/lang/String;)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Track;");

    GET_ID(GetStaticMethodID,
           fields.Media.createSlaveFromNativeID,
           fields.Media.clazz,
           "createSlaveFromNative",
           "(IILjava/lang/String;)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Slave;");

    GET_ID(GetStaticMethodID,
           fields.Media.createStatsFromNativeID,
           fields.Media.clazz,
           "createStatsFromNative",
           "(IFIFIIIIIIIIIIF)"
           "Lorg/videolan/libvlc/interfaces/IMedia$Stats;");

    GET_ID(GetStaticMethodID,
           fields.MediaPlayer.createTitleFromNativeID,
           fields.MediaPlayer.clazz,
           "createTitleFromNative",
           "(JLjava/lang/String;I)Lorg/videolan/libvlc/MediaPlayer$Title;");

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

    GET_ID(GetStaticMethodID,
           fields.MediaDiscoverer.createDescriptionFromNativeID,
           fields.MediaDiscoverer.clazz,
           "createDescriptionFromNative",
           "(Ljava/lang/String;Ljava/lang/String;I)"
           "Lorg/videolan/libvlc/MediaDiscoverer$Description;");

    GET_ID(GetStaticMethodID,
           fields.RendererDiscoverer.createDescriptionFromNativeID,
           fields.RendererDiscoverer.clazz,
           "createDescriptionFromNative",
           "(Ljava/lang/String;Ljava/lang/String;)"
           "Lorg/videolan/libvlc/RendererDiscoverer$Description;");

    GET_ID(GetStaticMethodID,
           fields.RendererDiscoverer.createItemFromNativeID,
           fields.RendererDiscoverer.clazz,
           "createItemFromNative",
           "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJ)"
           "Lorg/videolan/libvlc/RendererItem;");

    GET_ID(GetStaticMethodID,
           fields.Dialog.displayErrorFromNativeID,
           fields.Dialog.clazz,
           "displayErrorFromNative",
           "(Ljava/lang/String;Ljava/lang/String;)V");

    GET_ID(GetStaticMethodID,
           fields.Dialog.displayLoginFromNativeID,
           fields.Dialog.clazz,
           "displayLoginFromNative",
           "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)"
           "Lorg/videolan/libvlc/Dialog;");

    GET_ID(GetStaticMethodID,
           fields.Dialog.displayQuestionFromNativeID,
           fields.Dialog.clazz,
           "displayQuestionFromNative",
           "(JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;"
           "Ljava/lang/String;Ljava/lang/String;)"
           "Lorg/videolan/libvlc/Dialog;");

    GET_ID(GetStaticMethodID,
           fields.Dialog.displayProgressFromNativeID,
           fields.Dialog.clazz,
           "displayProgressFromNative",
           "(JLjava/lang/String;Ljava/lang/String;ZFLjava/lang/String;)"
           "Lorg/videolan/libvlc/Dialog;");

    GET_ID(GetStaticMethodID,
           fields.Dialog.cancelFromNativeID,
           fields.Dialog.clazz,
           "cancelFromNative",
           "(Lorg/videolan/libvlc/Dialog;)V");

    GET_ID(GetStaticMethodID,
           fields.Dialog.updateProgressFromNativeID,
           fields.Dialog.clazz,
           "updateProgressFromNative",
           "(Lorg/videolan/libvlc/Dialog;FLjava/lang/String;)V");

#undef GET_CLASS
#undef GET_ID

    LOGD("JNI interface loaded.");
    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;

    if ((*vm)->GetEnv(vm, (void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return;

    (*env)->DeleteGlobalRef(env, fields.IllegalStateException.clazz);
    (*env)->DeleteGlobalRef(env, fields.IllegalArgumentException.clazz);
    (*env)->DeleteGlobalRef(env, fields.RuntimeException.clazz);
    (*env)->DeleteGlobalRef(env, fields.OutOfMemoryError.clazz);
    (*env)->DeleteGlobalRef(env, fields.String.clazz);
    (*env)->DeleteGlobalRef(env, fields.FileDescriptor.clazz);
    (*env)->DeleteGlobalRef(env, fields.VLCObject.clazz);
    (*env)->DeleteGlobalRef(env, fields.Media.clazz);
    (*env)->DeleteGlobalRef(env, fields.Media.Track.clazz);
    (*env)->DeleteGlobalRef(env, fields.Media.Slave.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaPlayer.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaPlayer.Title.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaPlayer.Chapter.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaPlayer.TrackDescription.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaPlayer.Equalizer.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaDiscoverer.clazz);
    (*env)->DeleteGlobalRef(env, fields.MediaDiscoverer.Description.clazz);
    (*env)->DeleteGlobalRef(env, fields.RendererDiscoverer.clazz);
    (*env)->DeleteGlobalRef(env, fields.RendererDiscoverer.Description.clazz);
    (*env)->DeleteGlobalRef(env, fields.Dialog.clazz);

    pthread_key_delete(jni_env_key);

#ifndef NDEBUG
    std_logger_Close(p_std_logger);
#endif
}

void Java_org_videolan_libvlc_LibVLC_nativeNew(JNIEnv *env, jobject thiz,
                                               jobjectArray jstringArray,
                                               jstring jhomePath)
{
    vlcjni_object *p_obj = NULL;
    libvlc_instance_t *p_libvlc = NULL;
    jstring *strings = NULL;
    const char **argv = NULL;
    int argc = 0;

    if (jhomePath)
    {
        const char *psz_home = (*env)->GetStringUTFChars(env, jhomePath, 0);
        if (psz_home)
        {
            setenv("HOME", psz_home, 1);
            (*env)->ReleaseStringUTFChars(env, jhomePath, psz_home);
        }
    }
    setenv("VLC_DATA_PATH", "/system/usr/share", 1);

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
        throw_Exception(env, VLCJNI_EX_ILLEGAL_STATE,
                        "can't create LibVLC instance");
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

jint Java_org_videolan_libvlc_LibVLC_majorVersion(JNIEnv* env, jobject thiz)
{
    return atoi(libvlc_get_version());
}

jstring Java_org_videolan_libvlc_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_libvlc_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}

void Java_org_videolan_libvlc_LibVLC_nativeSetUserAgent(JNIEnv* env,
                                                        jobject thiz,
                                                        jstring jname,
                                                        jstring jhttp)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);
    const char *psz_name, *psz_http;

    if (!p_obj)
        return;

    psz_name = jname ? (*env)->GetStringUTFChars(env, jname, 0) : NULL;
    psz_http = jhttp ? (*env)->GetStringUTFChars(env, jhttp, 0) : NULL;

    if (psz_name && psz_http)
        libvlc_set_user_agent(p_obj->u.p_libvlc, psz_name, psz_http);

    if (psz_name)
        (*env)->ReleaseStringUTFChars(env, jname, psz_name);
    if (psz_http)
        (*env)->ReleaseStringUTFChars(env, jhttp, psz_http);

    if (!psz_name || !psz_http)
        throw_Exception(env, VLCJNI_EX_ILLEGAL_ARGUMENT, "name or http invalid");
}
