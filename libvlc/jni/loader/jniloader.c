/*****************************************************************************
 * jniloader.c
 *****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
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
#include <dlfcn.h>
#include <stdbool.h>
#include <android/log.h>
#include "jniloader.h"

#define LOG_TAG "VLC"
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

static void *handle;
static bool has_ml;

/* jniloader.so is a shared C library used to receive JNI_OnLoad callback.
 * Indeed, as JNI_OnLoad is already implemented in VLC core for its internal
 * configuration, we can't have an other JNI_OnLoad implementation inside the
 * libvlcjni.so.
 */
int JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv *env;

    if ((*vm)->GetEnv(vm, (void**) &env, VLC_JNI_VERSION) != JNI_OK)
    {
        LOGE("GetEnv failed");
        return -1;
    }

    handle = dlopen("libvlcjni.so", RTLD_LAZY);
    if (!handle)
    {
        LOGE("could not link libvlcjni.so");
        return -1;
    }

    int (*load)(JavaVM *, JNIEnv*);
    load = dlsym(handle, "VLCJNI_OnLoad");
    if (!load || load(vm, env) != 0)
    {
        if (!load)
            LOGE("could not find VLCJNI_OnLoad");
        else
            LOGE("VLCJNI_OnLoad failed");
        return -1;
    }

    /* MediaLibraryJNI_OnLoad is not mandatory */
    load = dlsym(handle, "MediaLibraryJNI_OnLoad");
    if (load && load(vm, env) != 0)
    {
        LOGE("MediaLibraryJNI_OnLoad failed");
        return -1;
    }
    has_ml = !!load;

    return VLC_JNI_VERSION;
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{
    JNIEnv* env;

    if ((*vm)->GetEnv(vm, (void**) &env, VLC_JNI_VERSION) != JNI_OK)
        return;
    void (*unload)(JavaVM *, JNIEnv*);

    if (has_ml)
    {
        unload = dlsym(handle, "MediaLibraryJNI_OnUnload");
        if (unload)
            unload(vm, env);
        else
            LOGE("could not find MediaLibraryJNI_OnUnload");
    }
    unload = dlsym(handle, "VLCJNI_OnUnload");
    if (unload)
        unload(vm, env);
    else
        LOGE("could not find VLCJNI_OnUnload");

    dlclose(handle);
}
