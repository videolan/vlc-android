/*****************************************************************************
 * libvlcjni-dialog.c
 *****************************************************************************
 * Copyright © 2016 VLC authors, VideoLAN and VideoLabs
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
#include <stdlib.h>
#include <pthread.h>

#include <jni.h>
#include <vlc/vlc.h>
#include <vlc/libvlc_dialog.h>
#define THREAD_NAME "jni-dialog"

#include "libvlcjni-vlcobject.h"

JNIEnv *jni_get_env(const char *name);


static void
dialog_set_context(JNIEnv *env, libvlc_dialog_id *p_id, jobject *jdialog)
{
    if (jdialog != NULL
     && (jdialog = (*env)->NewGlobalRef(env, jdialog)) != NULL)
        libvlc_dialog_set_context(p_id, jdialog);
    else
        libvlc_dialog_dismiss(p_id);
}

static void
display_error_cb(void *p_data, const char *psz_title, const char *psz_text)
{
    JNIEnv *env = NULL;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    (*env)->CallStaticVoidMethod(env, fields.Dialog.clazz,
        fields.Dialog.displayErrorFromNativeID,
        (*env)->NewStringUTF(env, psz_title),
        (*env)->NewStringUTF(env, psz_text));
}

static void
display_login_cb(void *p_data, libvlc_dialog_id *p_id, const char *psz_title,
                 const char *psz_text, const char *psz_default_username,
                 bool b_ask_store)
{
    jobject jdialog = NULL;
    JNIEnv *env = NULL;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    jdialog = (*env)->CallStaticObjectMethod(env, fields.Dialog.clazz,
        fields.Dialog.displayLoginFromNativeID,
        (jlong)(intptr_t) p_id,
        (*env)->NewStringUTF(env, psz_title),
        (*env)->NewStringUTF(env, psz_text),
        (*env)->NewStringUTF(env, psz_default_username), b_ask_store);
    dialog_set_context(env, p_id, jdialog);
}

static void
display_question_cb(void *p_data, libvlc_dialog_id *p_id, const char *psz_title,
                    const char *psz_text, libvlc_dialog_question_type i_type,
                    const char *psz_cancel, const char *psz_action1,
                    const char *psz_action2)
{
    jobject jdialog = NULL;
    JNIEnv *env = NULL;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    jdialog = (*env)->CallStaticObjectMethod(env, fields.Dialog.clazz,
        fields.Dialog.displayQuestionFromNativeID,
        (jlong)(intptr_t) p_id,
        (*env)->NewStringUTF(env, psz_title),
        (*env)->NewStringUTF(env, psz_text), i_type,
        (*env)->NewStringUTF(env, psz_cancel),
        psz_action1 ? (*env)->NewStringUTF(env, psz_action1) : NULL,
        psz_action2 ? (*env)->NewStringUTF(env, psz_action2) : NULL);
    dialog_set_context(env, p_id, jdialog);
}

static void
display_progress_cb(void *p_data, libvlc_dialog_id *p_id, const char *psz_title,
                    const char *psz_text, bool b_indeterminate,
                    float f_position, const char *psz_cancel)
{
    jobject jdialog = NULL;
    JNIEnv *env = NULL;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    jdialog = (*env)->CallStaticObjectMethod(env, fields.Dialog.clazz,
        fields.Dialog.displayProgressFromNativeID,
        (jlong)(intptr_t) p_id,
        (*env)->NewStringUTF(env, psz_title),
        (*env)->NewStringUTF(env, psz_text),
        b_indeterminate, f_position, (*env)->NewStringUTF(env, psz_cancel));
    dialog_set_context(env, p_id, jdialog);
}

static void
cancel_cb(void *p_data, libvlc_dialog_id *p_id)
{
    JNIEnv *env = NULL;
    jobject jdialog = (jobject)libvlc_dialog_get_context(p_id);

    if (jdialog == NULL)
        return;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    (*env)->CallStaticVoidMethod(env, fields.Dialog.clazz,
        fields.Dialog.cancelFromNativeID, jdialog);

    (*env)->DeleteGlobalRef(env, jdialog);
}

static void
update_progress_cb(void *p_data, libvlc_dialog_id *p_id, float f_position,
                   const char *psz_text)
{
    JNIEnv *env = NULL;
    jobject jdialog = (jobject)libvlc_dialog_get_context(p_id);

    if (jdialog == NULL)
        return;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    (*env)->CallStaticVoidMethod(env, fields.Dialog.clazz,
        fields.Dialog.updateProgressFromNativeID, jdialog, f_position,
        (*env)->NewStringUTF(env, psz_text));
}

static const libvlc_dialog_cbs dialog_cbs = {
    .pf_display_error = display_error_cb,
    .pf_display_login = display_login_cb,
    .pf_display_question = display_question_cb,
    .pf_display_progress = display_progress_cb,
    .pf_cancel = cancel_cb,
    .pf_update_progress = update_progress_cb,
};

void
Java_org_videolan_libvlc_Dialog_nativeSetCallbacks(
    JNIEnv *env, jobject thiz, jobject libvlc, jboolean b_enabled)
{
    vlcjni_object *p_lib_obj = VLCJniObject_getInstance(env, libvlc);
    libvlc_instance_t *p_libvlc = p_lib_obj->u.p_libvlc;

    if (b_enabled)
        libvlc_dialog_set_callbacks(p_libvlc, &dialog_cbs, NULL);
    else
        libvlc_dialog_set_callbacks(p_libvlc, NULL, NULL);
}

static libvlc_dialog_id *
get_dialog_id(jlong i_id)
{
    return (libvlc_dialog_id *)(intptr_t)i_id;
}

void
Java_org_videolan_libvlc_Dialog_00024IdDialog_nativeDismiss(
    JNIEnv *env, jobject thiz, jlong i_id)
{
    (void) thiz;
    libvlc_dialog_id *p_id = get_dialog_id(i_id);
    jobject jdialog = (jobject) libvlc_dialog_get_context(p_id);

    libvlc_dialog_dismiss(p_id);

    (*env)->DeleteGlobalRef(env, jdialog);
}

void
Java_org_videolan_libvlc_Dialog_00024LoginDialog_nativePostLogin(
    JNIEnv *env, jobject thiz, jlong i_id, jstring username, jstring password,
    jboolean b_store)
{
    (void) thiz;
    const char *psz_username;
    const char *psz_password;

    if (!username
     || !(psz_username = (*env)->GetStringUTFChars(env, username, 0)))
    {
        throw_IllegalArgumentException(env, "username invalid");
        return;
    }

    if (!password
     || !(psz_password = (*env)->GetStringUTFChars(env, password, 0)))
    {
        (*env)->ReleaseStringUTFChars(env, username, psz_username);
        throw_IllegalArgumentException(env, "password invalid");
        return;
    }

    libvlc_dialog_id *p_id = get_dialog_id(i_id);
    jobject jdialog = (jobject) libvlc_dialog_get_context(p_id);

    libvlc_dialog_post_login(p_id, psz_username, psz_password, b_store);

    (*env)->DeleteGlobalRef(env, jdialog);
    (*env)->ReleaseStringUTFChars(env, username, psz_username);
    (*env)->ReleaseStringUTFChars(env, password, psz_password);
}

void
Java_org_videolan_libvlc_Dialog_00024QuestionDialog_nativePostAction(
    JNIEnv *env, jobject thiz, jlong i_id, jint i_action)
{
    (void) thiz;
    libvlc_dialog_id *p_id = get_dialog_id(i_id);
    jobject jdialog = (jobject) libvlc_dialog_get_context(p_id);

    libvlc_dialog_post_action(p_id, i_action);

    (*env)->DeleteGlobalRef(env, jdialog);
}
