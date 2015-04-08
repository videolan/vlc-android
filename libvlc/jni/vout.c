/*****************************************************************************
 * vout.c
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

#include <vlc/vlc.h>
#include <vlc_common.h>

#include <jni.h>

#define THREAD_NAME "jni_vout"
extern JNIEnv *jni_get_env(const char *name);

pthread_mutex_t vout_android_lock;
static void *vout_android_gui = NULL;
static jobject vout_android_java_surf = NULL;
static jobject vout_android_subtitles_surf = NULL;

void *jni_LockAndGetSubtitlesSurface() {
    pthread_mutex_lock(&vout_android_lock);
    if (!vout_android_subtitles_surf) {
        pthread_mutex_unlock(&vout_android_lock);
        return NULL;
    }
    return vout_android_subtitles_surf;
}

jobject jni_LockAndGetAndroidJavaSurface() {
    pthread_mutex_lock(&vout_android_lock);
    if (!vout_android_java_surf) {
        pthread_mutex_unlock(&vout_android_lock);
        return NULL;
    }
    return vout_android_java_surf;
}

void jni_UnlockAndroidSurface() {
    pthread_mutex_unlock(&vout_android_lock);
}

void jni_EventHardwareAccelerationError()
{
    JNIEnv *env;

    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui == NULL) {
        pthread_mutex_unlock(&vout_android_lock);
        return;
    }

    jclass cls = (*env)->GetObjectClass(env, vout_android_gui);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "eventHardwareAccelerationError", "()V");
    (*env)->CallVoidMethod(env, vout_android_gui, methodId);

    (*env)->DeleteLocalRef(env, cls);
    pthread_mutex_unlock(&vout_android_lock);
}

static void jni_SetSurfaceLayoutEnv(JNIEnv *p_env, int width, int height, int visible_width, int visible_height, int sar_num, int sar_den)
{
    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui == NULL) {
        pthread_mutex_unlock(&vout_android_lock);
        return;
    }

    jclass cls = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, cls, "setSurfaceLayout", "(IIIIII)V");

    (*p_env)->CallVoidMethod (p_env, vout_android_gui, methodId, width, height, visible_width, visible_height, sar_num, sar_den);

    (*p_env)->DeleteLocalRef(p_env, cls);
    pthread_mutex_unlock(&vout_android_lock);
}

void jni_SetSurfaceLayout(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den)
{
    JNIEnv *p_env;

    if (!(p_env = jni_get_env(THREAD_NAME)))
        return;

    jni_SetSurfaceLayoutEnv(p_env, width, height, visible_width, visible_height, sar_num, sar_den);
}

void *jni_AndroidJavaSurfaceToNativeSurface(jobject surf)
{
    JNIEnv *p_env;
    jclass clz;
    jfieldID fid;
    void *native_surface = NULL;

    if (!(p_env = jni_get_env(THREAD_NAME)))
        return NULL;

    clz = (*p_env)->GetObjectClass(p_env, surf);
    fid = (*p_env)->GetFieldID(p_env, clz, "mSurface", "I");
    if (fid == NULL) {
        jthrowable exp = (*p_env)->ExceptionOccurred(p_env);
        if (exp) {
            (*p_env)->DeleteLocalRef(p_env, exp);
            (*p_env)->ExceptionClear(p_env);
        }
        fid = (*p_env)->GetFieldID(p_env, clz, "mNativeSurface", "I");
        if (fid == NULL) {
            jthrowable exp = (*p_env)->ExceptionOccurred(p_env);
            if (exp) {
                (*p_env)->DeleteLocalRef(p_env, exp);
                (*p_env)->ExceptionClear(p_env);
            }
        }
    }
    if (fid != NULL)
        native_surface = (void*)(*p_env)->GetIntField(p_env, surf, fid);
    (*p_env)->DeleteLocalRef(p_env, clz);

    return native_surface;
}

int jni_ConfigureSurface(jobject jsurf, int width, int height, int hal, bool *configured)
{
    JNIEnv *p_env;
    int ret;

    if (!(p_env = jni_get_env(THREAD_NAME)))
        return -1;

    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui == NULL) {
        pthread_mutex_unlock(&vout_android_lock);
        return -1;
    }

    jclass clz = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, clz, "configureSurface", "(Landroid/view/Surface;III)I");
    ret = (*p_env)->CallIntMethod (p_env, vout_android_gui, methodId, jsurf, width, height, hal);
    if (ret >= 0 && configured)
        *configured = ret == 1;

    (*p_env)->DeleteLocalRef(p_env, clz);

    pthread_mutex_unlock(&vout_android_lock);
    return ret == -1 ? -1 : 0;
}

void Java_org_videolan_libvlc_LibVLC_attachSurface(JNIEnv *env, jobject thiz, jobject surf, jobject gui) {
    pthread_mutex_lock(&vout_android_lock);

    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    if (vout_android_java_surf != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_java_surf);
    vout_android_gui = (*env)->NewGlobalRef(env, gui);
    vout_android_java_surf = (*env)->NewGlobalRef(env, surf);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_detachSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    if (vout_android_java_surf != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_java_surf);
    vout_android_gui = NULL;
    vout_android_java_surf = NULL;
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_attachSubtitlesSurface(JNIEnv *env, jobject thiz, jobject surf) {
    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_subtitles_surf != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_subtitles_surf);
    vout_android_subtitles_surf = (*env)->NewGlobalRef(env, surf);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_detachSubtitlesSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_subtitles_surf != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_subtitles_surf);
    vout_android_subtitles_surf = NULL;
    pthread_mutex_unlock(&vout_android_lock);
}

static int mouse_x = -1;
static int mouse_y = -1;
static int mouse_button = -1;
static int mouse_action = -1;

void Java_org_videolan_libvlc_LibVLC_sendMouseEvent(JNIEnv* env, jobject thiz, jint action, jint button, jint x, jint y)
{
    mouse_x = x;
    mouse_y = y;
    mouse_button = button;
    mouse_action = action;
}

void jni_getMouseCoordinates(int *action, int *button, int *x, int *y)
{
    *x = mouse_x;
    *y = mouse_y;
    *button = mouse_button;
    *action = mouse_action;

    mouse_button = mouse_action = mouse_x = mouse_y = -1;
}
