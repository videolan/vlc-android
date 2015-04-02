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

#include <stdbool.h>
#include <signal.h>

#include "native_crash_handler.h"
#include "utils.h"

static struct sigaction old_actions[NSIG];

#define THREAD_NAME "native_crash_handler"
extern JNIEnv *jni_get_env(const char *name);

// Monitored signals.
static const int monitored_signals[] = {
    SIGILL,
    SIGABRT,
    SIGBUS,
    SIGFPE,
    SIGSEGV,
#ifndef _MIPS_ARCH
    SIGSTKFLT,
#else
    SIGEMT,
#endif
    SIGPIPE
};


/**
 * Callback called when a monitored signal is triggered.
 */
void sigaction_callback(int signal, siginfo_t *info, void *reserved)
{
    JNIEnv *env;
    if (!(env = jni_get_env(THREAD_NAME)))
        return;

    // Call the Java LibVLC method that handle the crash.
    (*env)->CallStaticVoidMethod(env, fields.LibVLC.clazz,
                                 fields.LibVLC.onNativeCrashID);

    // Call the old signal handler.
    old_actions[signal].sa_handler(signal);
}


void init_native_crash_handler()
{
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


void destroy_native_crash_handler()
{
    // Uninstall the signal handlers and restore their old actions.
    for (unsigned i = 0; i < sizeof(monitored_signals) / sizeof(int); ++i)
    {
        const int s = monitored_signals[i];
        sigaction(s, &old_actions[s], NULL);
    }
}
