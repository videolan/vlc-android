/*****************************************************************************
 * utils.h
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

#ifndef LIBVLCJNI_UTILS_H
#define LIBVLCJNI_UTILS_H

libvlc_media_t *new_media(jlong instance, JNIEnv *env, jobject thiz, jstring fileLocation, bool noOmx, bool noVideo);

libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz);

jint getInt(JNIEnv *env, jobject thiz, const char* field);

void setInt(JNIEnv *env, jobject item, const char* field, jint value);

jlong getLong(JNIEnv *env, jobject thiz, const char* field);

void setLong(JNIEnv *env, jobject item, const char* field, jlong value);

void setFloat(JNIEnv *env, jobject item, const char* field, jfloat value);

void setString(JNIEnv *env, jobject item, const char* field, const char* text);

void arrayListGetIDs(JNIEnv *env, jclass* p_class, jmethodID* p_add, jmethodID* p_remove);

void arrayListStringAdd(JNIEnv *env, jclass class, jmethodID methodID, jobject arrayList, const char* str);

jobject getEventHandlerReference(JNIEnv *env, jobject thiz, jobject eventHandler);

void debug_log(void *data, int level, const libvlc_log_t *ctx, const char *fmt, va_list ap);

#endif // LIBVLCJNI_UTILS_H
