/*****************************************************************************
 * libvlcjni.c
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
#include <string.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <vlc/vlc.h>
#include <vlc_common.h>
#include <vlc_url.h>

#include <jni.h>

#include <android/api-level.h>

#include "libvlcjni.h"
#include "aout.h"
#include "utils.h"

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

libvlc_media_t *new_media(jlong instance, JNIEnv *env, jobject thiz, jstring fileLocation, bool noOmx, bool noVideo)
{
    libvlc_instance_t *libvlc = (libvlc_instance_t*)(intptr_t)instance;
    jboolean isCopy;
    const char *psz_location = (*env)->GetStringUTFChars(env, fileLocation, &isCopy);
    libvlc_media_t *p_md = libvlc_media_new_location(libvlc, psz_location);
    (*env)->ReleaseStringUTFChars(env, fileLocation, psz_location);
    if (!p_md)
        return NULL;

    if (!noOmx) {
        jclass cls = (*env)->GetObjectClass(env, thiz);
        jmethodID methodId = (*env)->GetMethodID(env, cls, "useIOMX", "()Z");
        if ((*env)->CallBooleanMethod(env, thiz, methodId)) {
            /*
             * Set higher caching values if using iomx decoding, since some omx
             * decoders have a very high latency, and if the preroll data isn't
             * enough to make the decoder output a frame, the playback timing gets
             * started too soon, and every decoded frame appears to be too late.
             * On Nexus One, the decoder latency seems to be 25 input packets
             * for 320x170 H.264, a few packets less on higher resolutions.
             * On Nexus S, the decoder latency seems to be about 7 packets.
             */
            libvlc_media_add_option(p_md, ":file-caching=1500");
            libvlc_media_add_option(p_md, ":network-caching=1500");
            libvlc_media_add_option(p_md, ":codec=mediacodec,iomx,all");
        }
        if (noVideo)
            libvlc_media_add_option(p_md, ":no-video");
    }
    return p_md;
}

// Get the current media list being followed
libvlc_media_list_t* getMediaList(JNIEnv *env, jobject thiz) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaList", "Lorg/videolan/libvlc/MediaList;");
    jobject javaML = (*env)->GetObjectField(env, thiz, fieldMP);
    return getMediaListFromJava(env, javaML);
}

libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_player_t*)(intptr_t)getLong(env, thiz, "mInternalMediaPlayerInstance");
}

static void releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t* p_mp = getMediaPlayer(env, thiz);
    if (p_mp)
    {
        libvlc_media_player_stop(p_mp);
        libvlc_media_player_release(p_mp);
        setLong(env, thiz, "mInternalMediaPlayerInstance", 0);
    }
}

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
JavaVM *myVm;

static jobject eventHandlerInstance = NULL;

/** vout lock declared in vout.c */
extern pthread_mutex_t vout_android_lock;

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;

    bool isAttached = false;

    if (eventHandlerInstance == NULL)
        return;

    if ((*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2) < 0) {
        if ((*myVm)->AttachCurrentThread(myVm, &env, NULL) < 0)
            return;
        isAttached = true;
    }

    /* Creating the bundle in C allows us to subscribe to more events
     * and get better flexibility for each event. For example, we can
     * have totally different types of data for each event, instead of,
     * for example, only an integer and/or string.
     */
    jclass clsBundle = (*env)->FindClass(env, "android/os/Bundle");
    jmethodID clsCtor = (*env)->GetMethodID(env, clsBundle, "<init>", "()V" );
    jobject bundle = (*env)->NewObject(env, clsBundle, clsCtor);

    jmethodID putInt = (*env)->GetMethodID(env, clsBundle, "putInt", "(Ljava/lang/String;I)V" );
    jmethodID putFloat = (*env)->GetMethodID(env, clsBundle, "putFloat", "(Ljava/lang/String;F)V" );
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );

    if (ev->type == libvlc_MediaPlayerPositionChanged) {
            jstring sData = (*env)->NewStringUTF(env, "data");
            (*env)->CallVoidMethod(env, bundle, putFloat, sData, ev->u.media_player_position_changed.new_position);
            (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaPlayerVout) {
        /* For determining the vout/ES track change */
        jstring sData = (*env)->NewStringUTF(env, "data");
        (*env)->CallVoidMethod(env, bundle, putInt, sData, ev->u.media_player_vout.new_count);
        (*env)->DeleteLocalRef(env, sData);
    } else if(ev->type == libvlc_MediaListItemAdded ||
              ev->type == libvlc_MediaListItemDeleted ) {
        jstring item_uri = (*env)->NewStringUTF(env, "item_uri");
        jstring item_index = (*env)->NewStringUTF(env, "item_index");
        char* mrl = libvlc_media_get_mrl(
            ev->type == libvlc_MediaListItemAdded ?
            ev->u.media_list_item_added.item :
            ev->u.media_list_item_deleted.item
            );
        jstring item_uri_value = (*env)->NewStringUTF(env, mrl);
        jint item_index_value;
        if(ev->type == libvlc_MediaListItemAdded)
            item_index_value = ev->u.media_list_item_added.index;
        else
            item_index_value = ev->u.media_list_item_deleted.index;

        (*env)->CallVoidMethod(env, bundle, putString, item_uri, item_uri_value);
        (*env)->CallVoidMethod(env, bundle, putInt, item_index, item_index_value);

        (*env)->DeleteLocalRef(env, item_uri);
        (*env)->DeleteLocalRef(env, item_uri_value);
        (*env)->DeleteLocalRef(env, item_index);
        free(mrl);
    }

    /* Get the object class */
    jclass cls = (*env)->GetObjectClass(env, eventHandlerInstance);
    if (!cls) {
        LOGE("EventHandler: failed to get class reference");
        goto end;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (methodID) {
        (*env)->CallVoidMethod(env, eventHandlerInstance, methodID, ev->type, bundle);
    } else {
        LOGE("EventHandler: failed to get the callback method");
    }

end:
    (*env)->DeleteLocalRef(env, bundle);
    if (isAttached)
        (*myVm)->DetachCurrentThread(myVm);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;

    pthread_mutex_init(&vout_android_lock, NULL);

    LOGD("JNI interface loaded.");
    return JNI_VERSION_1_2;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    pthread_mutex_destroy(&vout_android_lock);
}

// FIXME: use atomics
static bool verbosity;

void Java_org_videolan_libvlc_LibVLC_nativeInit(JNIEnv *env, jobject thiz)
{
    //only use OpenSLES if java side says we can
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getAout", "()I");
    bool use_opensles = (*env)->CallIntMethod(env, thiz, methodId) == AOUT_OPENSLES;

    methodId = (*env)->GetMethodID(env, cls, "timeStretchingEnabled", "()Z");
    bool enable_time_stretch = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "frameSkipEnabled", "()Z");
    bool enable_frame_skip = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "getDeblocking", "()I");
    int deblocking = (*env)->CallIntMethod(env, thiz, methodId);
    char deblockstr[2] = "3";
    snprintf(deblockstr, 2, "%d", deblocking);
    LOGD("Using deblocking level %d", deblocking);

    methodId = (*env)->GetMethodID(env, cls, "getNetworkCaching", "()I");
    int networkCaching = (*env)->CallIntMethod(env, thiz, methodId);
    char networkCachingstr[25] = "0";
    if(networkCaching > 0) {
        snprintf(networkCachingstr, 25, "--network-caching=%d", networkCaching);
        LOGD("Using network caching of %d ms", networkCaching);
    }

    methodId = (*env)->GetMethodID(env, cls, "getChroma", "()Ljava/lang/String;");
    jstring chroma = (*env)->CallObjectMethod(env, thiz, methodId);
    const char *chromastr = (*env)->GetStringUTFChars(env, chroma, 0);
    LOGD("Chroma set to \"%s\"", chromastr);

    methodId = (*env)->GetMethodID(env, cls, "getSubtitlesEncoding", "()Ljava/lang/String;");
    jstring subsencoding = (*env)->CallObjectMethod(env, thiz, methodId);
    const char *subsencodingstr = (*env)->GetStringUTFChars(env, subsencoding, 0);
    LOGD("Subtitle encoding set to \"%s\"", subsencodingstr);

    methodId = (*env)->GetMethodID(env, cls, "isVerboseMode", "()Z");
    verbosity = (*env)->CallBooleanMethod(env, thiz, methodId);

    /* Don't add any invalid options, otherwise it causes LibVLC to crash */
    const char *argv[] = {
        "-I", "dummy",
        "--no-osd",
        "--no-video-title-show",
        "--no-stats",
        "--no-plugins-cache",
        "--no-drop-late-frames",
        /* The VLC default is to pick the highest resolution possible
         * (i.e. 1080p). For mobile, pick a more sane default for slow
         * mobile data networks and slower hardware. */
        "--preferred-resolution", "360",
        "--avcodec-fast",
        "--avcodec-threads=0",
        "--subsdec-encoding", subsencodingstr,
        enable_time_stretch ? "--audio-time-stretch" : "--no-audio-time-stretch",
        "--avcodec-skiploopfilter", deblockstr,
        "--avcodec-skip-frame", enable_frame_skip ? "4" : "0",
        "--avcodec-skip-idct", enable_frame_skip ? "4" : "0",
        (networkCaching > 0) ? networkCachingstr : "",
        use_opensles ? "--aout=opensles" : "--aout=android_audiotrack",
        "--androidsurface-chroma", chromastr != NULL && chromastr[0] != 0 ? chromastr : "RV32",
    };
    libvlc_instance_t *instance = libvlc_new(sizeof(argv) / sizeof(*argv), argv);

    setLong(env, thiz, "mLibVlcInstance", (jlong)(intptr_t) instance);

    (*env)->ReleaseStringUTFChars(env, chroma, chromastr);
    (*env)->ReleaseStringUTFChars(env, subsencoding, subsencodingstr);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "org/videolan/libvlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);

    libvlc_log_set(instance, debug_log, &verbosity);
}

void Java_org_videolan_libvlc_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    releaseMediaPlayer(env, thiz);
    jlong libVlcInstance = getLong(env, thiz, "mLibVlcInstance");
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*)(intptr_t) libVlcInstance;
    libvlc_log_unset(instance);
    libvlc_release(instance);

    setLong(env, thiz, "mLibVlcInstance", 0);
}

void Java_org_videolan_libvlc_LibVLC_detachEventHandler(JNIEnv *env, jobject thiz)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }
}

void Java_org_videolan_libvlc_LibVLC_setEventHandler(JNIEnv *env, jobject thiz, jobject eventHandler)
{
    if (eventHandlerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventHandlerInstance);
        eventHandlerInstance = NULL;
    }

    eventHandlerInstance = getEventHandlerReference(env, thiz, eventHandler);
}

static void create_player_and_play(JNIEnv* env, jobject thiz,
                                   jlong instance, int position) {
    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new((libvlc_instance_t*)(intptr_t)instance);
    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz);

    //if AOUT_AUDIOTRACK_JAVA, we use amem
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getAout", "()I");
    if ( (*env)->CallIntMethod(env, thiz, methodId) == AOUT_AUDIOTRACK_JAVA )
    {
        libvlc_audio_set_callbacks(mp, aout_play, aout_pause, NULL, NULL, NULL,
                                   (void*) myJavaLibVLC);
        libvlc_audio_set_format_callbacks(mp, aout_open, aout_close);
    }

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaPlayerPlaying,
        libvlc_MediaPlayerPaused,
        libvlc_MediaPlayerEndReached,
        libvlc_MediaPlayerStopped,
        libvlc_MediaPlayerVout,
        libvlc_MediaPlayerPositionChanged,
        libvlc_MediaPlayerEncounteredError
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);


    /* Keep a pointer to this media player */
    setLong(env, thiz, "mInternalMediaPlayerInstance", (jlong)(intptr_t)mp);

    cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodID = (*env)->GetMethodID(env, cls, "applyEqualizer", "()V");
    (*env)->CallVoidMethod(env, thiz, methodID);

    setInt(env, thiz, "mInternalMediaPlayerIndex", (jint)position);
    libvlc_media_list_lock(p_mlist);
    libvlc_media_t* p_md = libvlc_media_list_item_at_index(p_mlist, position);
    libvlc_media_list_unlock(p_mlist);
    libvlc_media_player_set_media(mp, p_md);
    libvlc_media_player_play(mp);
}

jint Java_org_videolan_libvlc_LibVLC_readMedia(JNIEnv *env, jobject thiz,
                                            jlong instance, jstring mrl, jboolean novideo)
{
    /* Create a new item */
    libvlc_media_t *m = new_media(instance, env, thiz, mrl, false, novideo);
    if (!m)
    {
        LOGE("readMedia: Could not create the media!");
        return -1;
    }

    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);

    libvlc_media_list_lock(p_mlist);
    if(libvlc_media_list_add_media(p_mlist, m) != 0) {
        LOGE("readMedia: Could not add to the media list!");
        libvlc_media_list_unlock(p_mlist);
        libvlc_media_release(m);
        return -1;
    }
    int position = libvlc_media_list_index_of_item(p_mlist, m);
    libvlc_media_list_unlock(p_mlist);

    /* No need to keep the media now */
    libvlc_media_release(m);

    create_player_and_play(env, thiz, instance, position);

    return position;
}

void Java_org_videolan_libvlc_LibVLC_playIndex(JNIEnv *env, jobject thiz,
                                            jlong instance, int position) {
    create_player_and_play(env, thiz, instance, position);
}

void Java_org_videolan_libvlc_LibVLC_getMediaListItems(
                JNIEnv *env, jobject thiz, jobject arrayList) {
    jclass arrayClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID methodID = (*env)->GetMethodID(env, arrayClass, "add", "(Ljava/lang/Object;)Z");
    jstring str;

    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);
    libvlc_media_list_lock( p_mlist );
    for(int i = 0; i < libvlc_media_list_count( p_mlist ); i++) {
        char* mrl = libvlc_media_get_mrl( libvlc_media_list_item_at_index( p_mlist, i ) );
        str = (*env)->NewStringUTF(env, mrl);
        (*env)->CallBooleanMethod(env, arrayList, methodID, str);
        (*env)->DeleteLocalRef(env, str);
        free(mrl);
    }
    libvlc_media_list_unlock( p_mlist );
}

jfloat Java_org_videolan_libvlc_LibVLC_getRate(JNIEnv *env, jobject thiz) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        return libvlc_media_player_get_rate(mp);
    else
        return 1.00;
}

void Java_org_videolan_libvlc_LibVLC_setRate(JNIEnv *env, jobject thiz, jfloat rate) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        libvlc_media_player_set_rate(mp, rate);
}

jboolean Java_org_videolan_libvlc_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_playing(mp);
    else
        return 0;
}

jboolean Java_org_videolan_libvlc_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_seekable(mp);
    return 0;
}

void Java_org_videolan_libvlc_LibVLC_play(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_play(mp);
}

void Java_org_videolan_libvlc_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_pause(mp);
}

void Java_org_videolan_libvlc_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_stop(mp);
}

void Java_org_videolan_libvlc_LibVLC_previous(JNIEnv *env, jobject thiz)
{
    int current_position = getInt(env, thiz, "mInternalMediaPlayerIndex");

    if(current_position-1 >= 0) {
        setInt(env, thiz, "mInternalMediaPlayerIndex", (jint)(current_position-1));
        create_player_and_play(env, thiz,
             getLong(env, thiz, "mLibVlcInstance"), current_position-1);
    }
}

void Java_org_videolan_libvlc_LibVLC_next(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);
    int current_position = getInt(env, thiz, "mInternalMediaPlayerIndex");

    if(current_position+1 < libvlc_media_list_count(p_mlist)) {
        setInt(env, thiz, "mInternalMediaPlayerIndex", (jint)(current_position+1));
        create_player_and_play(env, thiz,
             getLong(env, thiz, "mLibVlcInstance"), current_position+1);
    }
}

jint Java_org_videolan_libvlc_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_volume(mp);
    return -1;
}

jint Java_org_videolan_libvlc_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume(mp, (int) volume);
    return -1;
}

jlong Java_org_videolan_libvlc_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_time(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_time(mp, time);
}

jfloat Java_org_videolan_libvlc_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jfloat) libvlc_media_player_get_position(mp);
    return -1;
}

void Java_org_videolan_libvlc_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_position(mp, pos);
}

jlong Java_org_videolan_libvlc_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_media_player_get_length(mp);
    return -1;
}

jstring Java_org_videolan_libvlc_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_org_videolan_libvlc_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_libvlc_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}
