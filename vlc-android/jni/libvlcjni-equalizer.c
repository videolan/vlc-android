/*****************************************************************************
 * libvlcjni-equalizer.c
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

#include "utils.h"

#define LOG_TAG "VLC/JNI/Equalizer"
#include "log.h"

/**
 * return band list as float[]
 */
jfloatArray Java_org_videolan_libvlc_LibVLC_getBands(JNIEnv *env, jobject thiz)
{
    unsigned count = libvlc_audio_equalizer_get_band_count();
    jfloatArray bands = (*env)->NewFloatArray(env, count);

    for (unsigned i = 0; i < count; ++i)
    {
        jfloat band = libvlc_audio_equalizer_get_band_frequency(i);
        (*env)->SetFloatArrayRegion(env, bands, i, 1, &band);
    }
    return bands;
}

/**
 * return preset list as String[]
 */
jobjectArray Java_org_videolan_libvlc_LibVLC_getPresets(JNIEnv *env, jobject thiz)
{
    unsigned count = libvlc_audio_equalizer_get_preset_count();
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray presets = (*env)->NewObjectArray(env, count, stringClass, NULL);

    for (unsigned i = 0; i < count; ++i)
    {
        const char *name = libvlc_audio_equalizer_get_preset_name(i);
        jstring jname = (*env)->NewStringUTF(env, name);
        (*env)->SetObjectArrayElement(env, presets, i, jname);
    }
    return presets;
}

/**
 * return preset n° <index> as float[] (first element is preamp, then bands)
 */
jfloatArray Java_org_videolan_libvlc_LibVLC_getPreset(JNIEnv *env, jobject thiz, jint index)
{
    unsigned count = libvlc_audio_equalizer_get_band_count();
    jfloatArray array = (*env)->NewFloatArray(env, count + 1);
    libvlc_equalizer_t *p_equalizer = libvlc_audio_equalizer_new_from_preset(index);
    if (p_equalizer != NULL)
    {
        jfloat preamp = libvlc_audio_equalizer_get_preamp(p_equalizer);
        (*env)->SetFloatArrayRegion(env, array, 0, 1, &preamp);

        for (unsigned i = 0; i < count; ++i)
        {
            jfloat band = libvlc_audio_equalizer_get_amp_at_index(p_equalizer, i);
            (*env)->SetFloatArrayRegion(env, array, i + 1, 1, &band);
        }
        libvlc_audio_equalizer_release(p_equalizer);
    }
    return array;
}

/**
 * apply equalizer settings (param bands is float[] (first element is preamp, then bands))
 */
//"--audio-filter=equalizer", "--equalizer-bands=-3.5 -4.5 -1 0 0 5 8 8 8 8",
jint Java_org_videolan_libvlc_LibVLC_setNativeEqualizer(JNIEnv *env, jobject thiz, jlong media_player, jfloatArray bands)
{
    jint res = -1;
    libvlc_media_player_t *mp = (libvlc_media_player_t*)(intptr_t)media_player;
    if (!mp)
        return res;

    if (bands == NULL)
        return libvlc_media_player_set_equalizer(mp, NULL);

    jfloat *cbands = (*env)->GetFloatArrayElements(env, bands, NULL);
    if (cbands == NULL)
        return res;

    jsize input_count = (*env)->GetArrayLength(env, bands);
    unsigned band_count = libvlc_audio_equalizer_get_band_count();
    if (input_count == band_count+1) // first item is preamp
    {
        libvlc_equalizer_t *p_equalizer = libvlc_audio_equalizer_new();
        libvlc_audio_equalizer_set_preamp(p_equalizer, cbands[0]);
        for (unsigned i = 0; i < band_count; ++i)
        {
            libvlc_audio_equalizer_set_amp_at_index(p_equalizer, cbands[i+1], i);
        }
        res = libvlc_media_player_set_equalizer(mp, p_equalizer);
        libvlc_audio_equalizer_release(p_equalizer);
    }
    return res;
}
