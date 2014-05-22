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

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

pthread_mutex_t vout_android_lock;
pthread_cond_t vout_android_surf_attached;
static void *vout_android_surf = NULL;
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

void *jni_LockAndGetAndroidSurface() {
    pthread_mutex_lock(&vout_android_lock);
    while (vout_android_surf == NULL)
        pthread_cond_wait(&vout_android_surf_attached, &vout_android_lock);
    return vout_android_surf;
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
    if (vout_android_gui == NULL)
        return;

    JNIEnv *env;
    (*myVm)->AttachCurrentThread(myVm, &env, NULL);

    jclass cls = (*env)->GetObjectClass(env, vout_android_gui);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "eventHardwareAccelerationError", "()V");
    (*env)->CallVoidMethod(env, vout_android_gui, methodId);

    (*env)->DeleteLocalRef(env, cls);
    (*myVm)->DetachCurrentThread(myVm);
}

void jni_SetAndroidSurfaceSizeEnv(JNIEnv *p_env, int width, int height, int visible_width, int visible_height, int sar_num, int sar_den)
{
    if (vout_android_gui == NULL)
        return;

    jclass cls = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, cls, "setSurfaceSize", "(IIIIII)V");

    (*p_env)->CallVoidMethod (p_env, vout_android_gui, methodId, width, height, visible_width, visible_height, sar_num, sar_den);

    (*p_env)->DeleteLocalRef(p_env, cls);
}

void jni_SetAndroidSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den)
{
    JNIEnv *p_env;

    (*myVm)->AttachCurrentThread (myVm, &p_env, NULL);
    jni_SetAndroidSurfaceSizeEnv(p_env, width, height, visible_width, visible_height, sar_num, sar_den);

    (*myVm)->DetachCurrentThread (myVm);
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
    jclass clz;
    jfieldID fid;

    clz = (*env)->FindClass(env, "org/videolan/libvlc/LibVlcUtil");
    jmethodID methodId = (*env)->GetStaticMethodID(env, clz, "isGingerbreadOrLater", "()Z");
    jboolean gingerbreadOrLater = (*env)->CallStaticBooleanMethod(env, clz, methodId);
    // Android 2.2 and under don't have ANativeWindow_fromSurface
    if(unlikely(!gingerbreadOrLater)) {
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
    }
    vout_android_gui = (*env)->NewGlobalRef(env, gui);
    vout_android_java_surf = (*env)->NewGlobalRef(env, surf);
    pthread_cond_signal(&vout_android_surf_attached);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_detachSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    vout_android_surf = NULL;
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
    vout_android_subtitles_surf = (*env)->NewGlobalRef(env, surf);
    pthread_cond_signal(&vout_android_surf_attached);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_libvlc_LibVLC_detachSubtitlesSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
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
