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
extern int jni_attach_thread(JNIEnv **env, const char *thread_name);
extern void jni_detach_thread();
extern int jni_get_env(JNIEnv **env);

pthread_mutex_t vout_android_lock;
pthread_cond_t vout_android_surf_attached;
static void *vout_android_gui = NULL;
static jobject vout_android_java_surf = NULL;
static jobject vout_android_subtitles_surf = NULL;
static bool vout_video_player_activity_created = false;

void *jni_LockAndGetSubtitlesSurface() {
    pthread_mutex_lock(&vout_android_lock);
    while (vout_android_subtitles_surf == NULL)
        pthread_cond_wait(&vout_android_surf_attached, &vout_android_lock);
    return vout_android_subtitles_surf;
}

jobject jni_LockAndGetAndroidJavaSurface() {
    pthread_mutex_lock(&vout_android_lock);
    while (vout_android_java_surf == NULL)
        pthread_cond_wait(&vout_android_surf_attached, &vout_android_lock);
    return vout_android_java_surf;
}

void jni_UnlockAndroidSurface() {
    pthread_mutex_unlock(&vout_android_lock);
}

void jni_EventHardwareAccelerationError()
{
    JNIEnv *env;
    bool isAttached = false;

    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui == NULL) {
        pthread_mutex_unlock(&vout_android_lock);
        return;
    }

    if (jni_get_env(&env) < 0) {
        if (jni_attach_thread(&env, THREAD_NAME) < 0) {
            pthread_mutex_unlock(&vout_android_lock);
            return;
        }
        isAttached = true;
    }

    jclass cls = (*env)->GetObjectClass(env, vout_android_gui);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "eventHardwareAccelerationError", "()V");
    (*env)->CallVoidMethod(env, vout_android_gui, methodId);

    (*env)->DeleteLocalRef(env, cls);
    if (isAttached)
        jni_detach_thread();
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
    bool isAttached = false;

    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread(&p_env, THREAD_NAME) < 0)
            return;
        isAttached = true;
    }
    jni_SetSurfaceLayoutEnv(p_env, width, height, visible_width, visible_height, sar_num, sar_den);

    if (isAttached)
        jni_detach_thread();
}

void *jni_AndroidJavaSurfaceToNativeSurface(jobject *surf)
{
    JNIEnv *p_env;
    jclass clz;
    jfieldID fid;
    void *native_surface = NULL;
    bool isAttached = false;

    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread(&p_env, THREAD_NAME) < 0)
            return NULL;
        isAttached = true;
    }
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

    if (isAttached)
        jni_detach_thread();
    return native_surface;
}

int jni_ConfigureSurface(jobject jsurf, int width, int height, int hal, bool *configured)
{
    JNIEnv *p_env;
    bool isAttached = false;
    int ret;

    pthread_mutex_lock(&vout_android_lock);
    if (vout_android_gui == NULL) {
        pthread_mutex_unlock(&vout_android_lock);
        return -1;
    }

    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread(&p_env, THREAD_NAME) < 0) {
            pthread_mutex_unlock(&vout_android_lock);
            return -1;
        }
        isAttached = true;
    }
    jclass clz = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, clz, "configureSurface", "(Landroid/view/Surface;III)I");
    ret = (*p_env)->CallIntMethod (p_env, vout_android_gui, methodId, jsurf, width, height, hal);
    if (ret >= 0 && configured)
        *configured = ret == 1;

    (*p_env)->DeleteLocalRef(p_env, clz);

    if (isAttached)
        jni_detach_thread();
    pthread_mutex_unlock(&vout_android_lock);
    return ret == -1 ? -1 : 0;
}

bool jni_IsVideoPlayerActivityCreated() {
    pthread_mutex_lock(&vout_android_lock);
    bool result = vout_video_player_activity_created;
    pthread_mutex_unlock(&vout_android_lock);
    return result;
}

void Java_org_videolan_libvlc_LibVLC_eventVideoPlayerActivityCreated(JNIEnv *env, jobject thiz, jboolean created) {
    pthread_mutex_lock(&vout_android_lock);
    vout_video_player_activity_created = created;
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_attachSurface(JNIEnv *env, jobject thiz, jobject surf, jobject gui) {
    pthread_mutex_lock(&vout_android_lock);

    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    if (vout_android_java_surf != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_java_surf);
    vout_android_gui = (*env)->NewGlobalRef(env, gui);
    vout_android_java_surf = (*env)->NewGlobalRef(env, surf);
    pthread_cond_signal(&vout_android_surf_attached);
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
    pthread_cond_signal(&vout_android_surf_attached);
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
