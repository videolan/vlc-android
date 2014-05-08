/*****************************************************************************
 * native_crash_handler.c
 *****************************************************************************
 * Copyright © 2010-2014 VLC authors and VideoLAN
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

#include <signal.h>

#include "native_crash_handler.h"

static struct sigaction old_actions[NSIG];
static jobject j_libVLC;

/** Unique Java VM instance, as defined in libvlcjni.c */
extern JavaVM *myVm;

// Monitored signals.
static const int monitored_signals[] = {
    SIGILL,
    SIGABRT,
    SIGBUS,
    SIGFPE,
    SIGSEGV,
    SIGSTKFLT,
    SIGPIPE
};


/**
 * Callback called when a monitored signal is triggered.
 */
void sigaction_callback(int signal, siginfo_t *info, void *reserved)
{
    // Call the Java LibVLC method that handle the crash.
    JNIEnv *env;
    (*myVm)->AttachCurrentThread(myVm, &env, NULL);

    jclass cls = (*env)->GetObjectClass(env, j_libVLC);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "onNativeCrash", "()V");
    (*env)->CallVoidMethod(env, j_libVLC, methodId);

    (*env)->DeleteLocalRef(env, cls);
    (*myVm)->DetachCurrentThread(myVm);

    // Call the old signal handler.
    old_actions[signal].sa_handler(signal);
}


void init_native_crash_handler(JNIEnv *env, jobject j_libVLC_local)
{
    j_libVLC = (*env)->NewGlobalRef(env, j_libVLC_local);
    struct sigaction handler;
    memset(&handler, 0, sizeof(struct sigaction));

    handler.sa_sigaction = sigaction_callback;
    handler.sa_flags = SA_RESETHAND;

    // Install the signal handlers and save their old actions.
    for (unsigned i = 0; i < sizeof(monitored_signals) / sizeof(int); ++i)
    {
        const int s = monitored_signals[i];
        sigaction(s, &handler, &old_actions[s]);
    }
}


void destroy_native_crash_handler(JNIEnv *env)
{
    // Uninstall the signal handlers and restore their old actions.
    for (unsigned i = 0; i < sizeof(monitored_signals) / sizeof(int); ++i)
    {
        const int s = monitored_signals[i];
        sigaction(s, &old_actions[s], NULL);
    }

    (*env)->DeleteGlobalRef(env, j_libVLC);
}
