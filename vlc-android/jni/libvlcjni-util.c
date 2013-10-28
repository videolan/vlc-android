/*****************************************************************************
 * libvlcjni-util.c
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

#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>

#include <vlc/vlc.h>
#include <vlc_common.h>
#include <vlc_url.h>

#include <jni.h>

#define LOG_TAG "VLC/JNI/Util"
#include "log.h"

static jobject debugBufferInstance = NULL;

// FIXME: use atomics
static bool buffer_logging;

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

jint getInt(JNIEnv *env, jobject thiz, const char* field) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          field, "I");
    return (*env)->GetIntField(env, thiz, fieldMP);
}
void setInt(JNIEnv *env, jobject item, const char* field, jint value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "I");
    if (fieldId == NULL)
        return;

    (*env)->SetIntField(env, item, fieldId, value);
}

jlong getLong(JNIEnv *env, jobject thiz, const char* field) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          field, "J");
    return (*env)->GetLongField(env, thiz, fieldMP);
}
void setLong(JNIEnv *env, jobject item, const char* field, jlong value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "J");
    if (fieldId == NULL)
        return;

    (*env)->SetLongField(env, item, fieldId, value);
}

void setFloat(JNIEnv *env, jobject item, const char* field, jfloat value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "F");
    if (fieldId == NULL)
        return;

    (*env)->SetFloatField(env, item, fieldId, value);
}
void setString(JNIEnv *env, jobject item, const char* field, const char* text) {
    jclass cls;
    jfieldID fieldId;
    jstring jstr;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "Ljava/lang/String;");
    if (fieldId == NULL)
        return;

    /* Create a new string and overwrite the instance field */
    jstr = (*env)->NewStringUTF(env, text);
    if (jstr == NULL)
        return;
    (*env)->SetObjectField(env, item, fieldId, jstr);
}

void arrayListGetIDs(JNIEnv *env, jclass* p_class, jmethodID* p_add, jmethodID* p_remove) {
    *p_class = (*env)->FindClass(env, "java/util/ArrayList");
    if(p_add)
        *p_add = (*env)->GetMethodID(env, *p_class, "add", "(Ljava/lang/Object;)Z");
    if(p_remove)
        *p_remove = (*env)->GetMethodID(env, *p_class, "remove", "(I)Ljava/lang/Object;");
}

void arrayListStringAdd(JNIEnv *env, jclass class, jmethodID methodID, jobject arrayList, const char* str) {
    jstring jstr = (*env)->NewStringUTF(env, str);
    (*env)->CallBooleanMethod(env, arrayList, methodID, jstr);
    (*env)->DeleteLocalRef(env, jstr);
}

jobject getEventHandlerReference(JNIEnv *env, jobject thiz, jobject eventHandler)
{
    jclass cls = (*env)->GetObjectClass(env, eventHandler);
    if (!cls) {
        LOGE("setEventHandler: failed to get class reference");
        return NULL;
    }

    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (!methodID) {
        LOGE("setEventHandler: failed to get the callback method");
        return NULL;
    }

    return (*env)->NewGlobalRef(env, eventHandler);
}

static void debug_buffer_log(void *data, int level, const char *fmt, va_list ap)
{
    bool isAttached = false;
    JNIEnv *env = NULL;

    if ((*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2) < 0) {
        if ((*myVm)->AttachCurrentThread(myVm, &env, NULL) < 0)
            return;
        isAttached = true;
    }

    /* Prepare message string */
    char* psz_fmt_newline = malloc(strlen(fmt) + 2);
    if(!psz_fmt_newline)
        return;
    strcpy(psz_fmt_newline, fmt);
    strcat(psz_fmt_newline, "\n");
    char* psz_msg = NULL;
    int res = vasprintf(&psz_msg, psz_fmt_newline, ap);
    free(psz_fmt_newline);
    if(res < 0)
        return;

    jobject buffer = debugBufferInstance;
    jclass buffer_class = (*env)->FindClass(env, "java/lang/StringBuffer");
    jmethodID bufferAppendID = (*env)->GetMethodID(env, buffer_class, "append", "(Ljava/lang/String;)Ljava/lang/StringBuffer;");

    jstring message = (*env)->NewStringUTF(env, psz_msg);
    (*env)->CallObjectMethod(env, buffer, bufferAppendID, message);
    (*env)->DeleteLocalRef(env, message);
    free(psz_msg);

    if (isAttached)
        (*myVm)->DetachCurrentThread(myVm);
}

void debug_log(void *data, int level, const libvlc_log_t *ctx, const char *fmt, va_list ap)
{
    bool *verbose = data;

    static const uint8_t priority[5] = {
        [LIBVLC_DEBUG]   = ANDROID_LOG_DEBUG,
        [1 /* ??? */]    = ANDROID_LOG_DEBUG,
        [LIBVLC_NOTICE]  = ANDROID_LOG_INFO,
        [LIBVLC_WARNING] = ANDROID_LOG_WARN,
        [LIBVLC_ERROR]   = ANDROID_LOG_ERROR,
    };

    int prio = ANDROID_LOG_DEBUG;
    if (level >= LIBVLC_DEBUG && level <= LIBVLC_ERROR)
        prio = priority[level];

    /* Quit if we are not doing anything */
    if(!buffer_logging && (!(*verbose) && prio < ANDROID_LOG_ERROR))
        return;

    /* Add emitting module & type */
    char* fmt2 = NULL;
    if(asprintf(&fmt2, "%s %s: %s", ctx->psz_module, ctx->psz_object_type, fmt) < 0)
        return;

    if (buffer_logging) {
        va_list aq;
        va_copy(aq, ap);
        debug_buffer_log(data, level, fmt2, aq);
        va_end(aq);
    }

    __android_log_vprint(prio, "VLC", fmt2, ap);
    free(fmt2);
}

void Java_org_videolan_libvlc_LibVLC_startDebugBuffer(JNIEnv *env, jobject thiz)
{
    jclass libVLC_class = (*env)->FindClass(env, "org/videolan/libvlc/LibVLC");
    jmethodID getInstance = (*env)->GetStaticMethodID(env, libVLC_class, "getInstance", "()Lorg/videolan/libvlc/LibVLC;");
    jobject libvlcj = (*env)->CallStaticObjectMethod(env, libVLC_class, getInstance);

    jfieldID bufferID = (*env)->GetFieldID(env, libVLC_class, "mDebugLogBuffer", "Ljava/lang/StringBuffer;");
    jobject buffer = (*env)->GetObjectField(env, libvlcj, bufferID);

    debugBufferInstance = (*env)->NewGlobalRef(env, buffer);
    (*env)->DeleteLocalRef(env, buffer);

    jfieldID buffer_flag = (*env)->GetFieldID(env, libVLC_class, "mIsBufferingLog", "Z");
    (*env)->SetBooleanField(env, libvlcj, buffer_flag, JNI_TRUE);

    (*env)->DeleteLocalRef(env, libVLC_class);
    (*env)->DeleteLocalRef(env, libvlcj);
    buffer_logging = true;
}

void Java_org_videolan_libvlc_LibVLC_stopDebugBuffer(JNIEnv *env, jobject thiz)
{
    buffer_logging = false;
    jclass libVLC_class = (*env)->FindClass(env, "org/videolan/libvlc/LibVLC");
    jmethodID getInstance = (*env)->GetStaticMethodID(env, libVLC_class, "getInstance", "()Lorg/videolan/libvlc/LibVLC;");
    jobject libvlcj = (*env)->CallStaticObjectMethod(env, libVLC_class, getInstance);

    (*env)->DeleteGlobalRef(env, debugBufferInstance);

    jfieldID buffer_flag = (*env)->GetFieldID(env, libVLC_class, "mIsBufferingLog", "Z");
    (*env)->SetBooleanField(env, libvlcj, buffer_flag, JNI_FALSE);

    (*env)->DeleteLocalRef(env, libVLC_class);
    (*env)->DeleteLocalRef(env, libvlcj);
}

jstring Java_org_videolan_libvlc_LibVLC_nativeToURI(JNIEnv *env, jobject thiz, jstring path)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);
    /* Convert the path to URI */
    char* psz_location;
    if(unlikely( strstr( psz_path, "://" ) ))
        psz_location = strdup(psz_path);
    else
        psz_location = vlc_path2uri(psz_path, "file");
    /* Box into jstring */
    jstring t = (*env)->NewStringUTF(env, psz_location);
    /* Clean up */
    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    free(psz_location);
    return t;
}

void Java_org_videolan_libvlc_LibVLC_nativeReadDirectory(JNIEnv *env, jobject thiz, jstring path, jobject arrayList)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);

    DIR* p_dir = opendir(psz_path);
    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    if(!p_dir)
        return;

    jclass arrayClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID methodID = (*env)->GetMethodID(env, arrayClass, "add", "(Ljava/lang/Object;)Z");

    struct dirent* p_dirent;
    jstring str;
    while(1) {
        errno = 0;
        p_dirent = readdir(p_dir);
        if(p_dirent == NULL) {
            if(errno > 0) /* error reading this entry */
                continue;
            else if(errno == 0) /* end of stream */
                break;
        }
        str = (*env)->NewStringUTF(env, p_dirent->d_name);
        (*env)->CallBooleanMethod(env, arrayList, methodID, str);
        (*env)->DeleteLocalRef(env, str);
    }
    closedir(p_dir);
}

jboolean Java_org_videolan_libvlc_LibVLC_nativeIsPathDirectory(JNIEnv *env, jobject thiz, jstring path)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);

    jboolean isDirectory;
    struct stat buf;
    if(stat(psz_path, &buf) != 0)
        /* couldn't stat */
        isDirectory = JNI_FALSE;
    else {
        if(S_ISDIR(buf.st_mode))
            isDirectory = JNI_TRUE;
        else
            isDirectory = JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    return isDirectory;
}
