/*****************************************************************************
 * libvlcjni.c
 *****************************************************************************
 * Copyright © 2010-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
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
#include <vlc_fourcc.h>

#include <jni.h>

#include <android/api-level.h>

#include "libvlcjni.h"
#include "aout.h"
#include "utils.h"

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

#define AOUT_AUDIOTRACK_JAVA 0
#define AOUT_AUDIOTRACK      1
#define AOUT_OPENSLES        2

static jint getInt(JNIEnv *env, jobject thiz, const char* field) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          field, "I");
    return (*env)->GetIntField(env, thiz, fieldMP);
}
static void setInt(JNIEnv *env, jobject item, const char* field, jint value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "I");
    if (fieldId == NULL)
        return;

    (*env)->SetIntField(env, item, fieldId, value);
}

static jlong getLong(JNIEnv *env, jobject thiz, const char* field) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          field, "J");
    return (*env)->GetLongField(env, thiz, fieldMP);
}
static void setLong(JNIEnv *env, jobject item, const char* field, jlong value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "J");
    if (fieldId == NULL)
        return;

    (*env)->SetLongField(env, item, fieldId, value);
}

static void setFloat(JNIEnv *env, jobject item, const char* field, jfloat value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "F");
    if (fieldId == NULL)
        return;

    (*env)->SetFloatField(env, item, fieldId, value);
}
static void setString(JNIEnv *env, jobject item, const char* field, const char* text) {
    jclass cls;
    jfieldID fieldId;
    jstring jstr;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "Ljava/lang/String;");
    if (fieldId == NULL)
        return;

    /* Create a new string and overwrite the instance field */
    jstr = (*env)->NewStringUTF(env, text);
    if (jstr == NULL)
        return;
    (*env)->SetObjectField(env, item, fieldId, jstr);
}

struct length_change_monitor {
    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
    bool length_changed;
};

static void length_changed_callback(const libvlc_event_t *ev, void *data)
{
    struct length_change_monitor *monitor = data;
    pthread_mutex_lock(&monitor->doneMutex);
    monitor->length_changed = true;
    pthread_cond_signal(&monitor->doneCondVar);
    pthread_mutex_unlock(&monitor->doneMutex);
}

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

static libvlc_media_list_t *getMediaList(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_list_t*)(intptr_t)getLong(env, thiz, "mMediaListInstance");
}

static libvlc_media_player_t *getMediaPlayer(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_player_t*)(intptr_t)getLong(env, thiz, "mInternalMediaPlayerInstance");
}

static libvlc_media_list_player_t *getMediaListPlayer(JNIEnv *env, jobject thiz)
{
    return (libvlc_media_list_player_t*)(intptr_t)getLong(env, thiz, "mMediaListPlayerInstance");
}

static void releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t* p_mlp = getMediaListPlayer(env, thiz);
    if (p_mlp)
    {
        libvlc_media_list_player_stop(p_mlp);
        libvlc_media_list_player_release(p_mlp);
        /* libvlc_media_list_player_release frees the media player, so
         * we don't free it ourselves. */
        setLong(env, thiz, "mInternalMediaPlayerInstance", 0);
        setLong(env, thiz, "mMediaListPlayerInstance", 0);
    }
}

/* Pointer to the Java virtual machine
 * Note: It's okay to use a static variable for the VM pointer since there
 * can only be one instance of this shared library in a single VM
 */
JavaVM *myVm;

static jobject eventManagerInstance = NULL;

static pthread_mutex_t vout_android_lock;
static void *vout_android_surf = NULL;
static void *vout_android_gui = NULL;

void *jni_LockAndGetAndroidSurface() {
    pthread_mutex_lock(&vout_android_lock);
    return vout_android_surf;
}

void jni_UnlockAndroidSurface() {
    pthread_mutex_unlock(&vout_android_lock);
}

void jni_SetAndroidSurfaceSize(int width, int height, int sar_num, int sar_den)
{
    if (vout_android_gui == NULL)
        return;

    JNIEnv *p_env;

    (*myVm)->AttachCurrentThread (myVm, &p_env, NULL);
    jclass cls = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, cls, "setSurfaceSize", "(IIII)V");

    (*p_env)->CallVoidMethod (p_env, vout_android_gui, methodId, width, height, sar_num, sar_den);

    (*p_env)->DeleteLocalRef(p_env, cls);
    (*myVm)->DetachCurrentThread (myVm);
}

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;
    JavaVM *myVm = data;

    bool isAttached = false;

    if (eventManagerInstance == NULL)
        return;

    int status = (*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2);
    if (status < 0) {
        LOGD("vlc_event_callback: failed to get JNI environment, "
             "assuming native thread");
        status = (*myVm)->AttachCurrentThread(myVm, &env, NULL);
        if (status < 0)
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
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );

    if(ev->type == libvlc_MediaPlayerVout) {
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
    jclass cls = (*env)->GetObjectClass(env, eventManagerInstance);
    if (!cls) {
        LOGE("EventManager: failed to get class reference");
        goto end;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (methodID) {
        (*env)->CallVoidMethod(env, eventManagerInstance, methodID, ev->type, bundle);
    } else {
        LOGE("EventManager: failed to get the callback method");
    }

end:
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

void Java_org_videolan_vlc_LibVLC_attachSurface(JNIEnv *env, jobject thiz, jobject surf, jobject gui, jint width, jint height) {
    jclass clz;
    jfieldID fid;

    pthread_mutex_lock(&vout_android_lock);
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

    vout_android_gui = (*env)->NewGlobalRef(env, gui);
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_vlc_LibVLC_detachSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    vout_android_surf = NULL;
    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    vout_android_gui = NULL;
    pthread_mutex_unlock(&vout_android_lock);
}

static void debug_log(void *data, int level, const char *fmt, va_list ap)
{
    bool *verbose = data;

    static const uint8_t priority[5] = {
        [LIBVLC_DEBUG]   = ANDROID_LOG_DEBUG,
        [1 /* ??? */]    = ANDROID_LOG_DEBUG,
        [LIBVLC_NOTICE]  = ANDROID_LOG_INFO,
        [LIBVLC_WARNING] = ANDROID_LOG_WARN,
        [LIBVLC_ERROR]   = ANDROID_LOG_ERROR,
    };

    int prio = ANDROID_LOG_DEBUG;
    if (level >= LIBVLC_DEBUG && level <= LIBVLC_ERROR)
        prio = priority[level];

    if (!*verbose && prio < ANDROID_LOG_ERROR)
        return;

    __android_log_vprint(prio, "VLC", fmt, ap);
}

static libvlc_log_subscriber_t debug_subscriber;
static bool verbosity;

void Java_org_videolan_vlc_LibVLC_changeVerbosity(JNIEnv *env, jobject thiz, jboolean verbose)
{
    verbosity = verbose;
    libvlc_log_unsubscribe(&debug_subscriber);
    libvlc_log_subscribe(&debug_subscriber, debug_log, &verbosity);
}

void Java_org_videolan_vlc_LibVLC_nativeInit(JNIEnv *env, jobject thiz, jboolean verbose)
{
    //only use OpenSLES if java side says we can
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jmethodID methodId = (*env)->GetMethodID(env, cls, "getAout", "()I");
    bool use_opensles = (*env)->CallIntMethod(env, thiz, methodId) == AOUT_OPENSLES;

    methodId = (*env)->GetMethodID(env, cls, "timeStretchingEnabled", "()Z");
    bool enable_time_stretch = (*env)->CallBooleanMethod(env, thiz, methodId);

    methodId = (*env)->GetMethodID(env, cls, "getSubtitlesEncoding", "()Ljava/lang/String;");
    jstring subsencoding = (*env)->CallObjectMethod(env, thiz, methodId);
    const char *subsencodingstr = (*env)->GetStringUTFChars(env, subsencoding, 0);

    LOGD("Subtitles encoding sets to: %s", subsencodingstr);

    verbosity = verbose;
    libvlc_log_subscribe(&debug_subscriber, debug_log, &verbosity);

    /* Don't add any invalid options, otherwise it causes LibVLC to crash */
    const char *argv[] = {
        "-I", "dummy",
        "--no-osd",
        "--no-video-title-show",
        "--no-stats",
        "--no-plugins-cache",
        "--no-drop-late-frames",
        "--avcodec-fast",
        "--avcodec-threads=0",
        "--subsdec-encoding", subsencodingstr,
        enable_time_stretch ? "--audio-time-stretch" : "--no-audio-time-stretch",
        use_opensles ? "--aout=opensles" : "--aout=android_audiotrack",
    };
    libvlc_instance_t *instance = libvlc_new(sizeof(argv) / sizeof(*argv), argv);

    setLong(env, thiz, "mLibVlcInstance", (jlong)(intptr_t) instance);

    (*env)->ReleaseStringUTFChars(env, subsencoding, subsencodingstr);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "org/videolan/vlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);

    /* Initialize media list (a.k.a. playlist/history) */
    libvlc_media_list_t* pointer = libvlc_media_list_new( instance );
    if(!pointer) {
        jclass exc = (*env)->FindClass(env, "org/videolan/vlc/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to create LibVLC media list");
        return;
    }

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_list_event_manager(pointer);
    static const libvlc_event_type_t mp_events[] = {
        libvlc_MediaListItemAdded,
        libvlc_MediaListItemDeleted,
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);

    setLong(env, thiz, "mMediaListInstance", (jlong)(intptr_t)pointer);
}

jstring Java_org_videolan_vlc_LibVLC_nativeToURI(JNIEnv *env, jobject thiz, jstring path)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);
    /* Convert the path to URI */
    char* psz_location;
    if(unlikely( strstr( psz_path, "://" ) ))
        psz_location = strdup(psz_path);
    else
        psz_location = vlc_path2uri(psz_path, "file");
    /* Box into jstring */
    jstring t = (*env)->NewStringUTF(env, psz_location);
    /* Clean up */
    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    free(psz_location);
    return t;
}

void Java_org_videolan_vlc_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    releaseMediaPlayer(env, thiz);
    jlong libVlcInstance = getLong(env, thiz, "mLibVlcInstance");
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*)(intptr_t) libVlcInstance;
    libvlc_release(instance);
    libvlc_log_unsubscribe(&debug_subscriber);

    setLong(env, thiz, "mLibVlcInstance", 0);
}

void Java_org_videolan_vlc_LibVLC_detachEventManager(JNIEnv *env, jobject thiz)
{
    if (eventManagerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventManagerInstance);
        eventManagerInstance = NULL;
    }
}

void Java_org_videolan_vlc_LibVLC_setEventManager(JNIEnv *env, jobject thiz, jobject eventManager)
{
    if (eventManagerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventManagerInstance);
        eventManagerInstance = NULL;
    }

    jclass cls = (*env)->GetObjectClass(env, eventManager);
    if (!cls) {
        LOGE("setEventManager: failed to get class reference");
        return;
    }

    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(ILandroid/os/Bundle;)V");
    if (!methodID) {
        LOGE("setEventManager: failed to get the callback method");
        return;
    }

    eventManagerInstance = (*env)->NewGlobalRef(env, eventManager);
}

jobjectArray Java_org_videolan_vlc_LibVLC_readMediaMeta(JNIEnv *env,
                                                        jobject thiz, jlong instance, jstring mrl)
{
    jobjectArray array = (*env)->NewObjectArray(env, 8,
            (*env)->FindClass(env, "java/lang/String"),
            (*env)->NewStringUTF(env, ""));

    libvlc_media_t *m = new_media(instance, env, thiz, mrl, false, false);
    if (!m)
    {
        LOGE("readMediaMeta: Could not create the media!");
        return array;
    }

    libvlc_media_parse(m);

    static const char str[][7] = {
        "artist", "album", "title", "genre",
    };
    static const libvlc_meta_t meta_id[] = {
        libvlc_meta_Artist,
        libvlc_meta_Album,
        libvlc_meta_Title,
        libvlc_meta_Genre,
    };
    for (int i=0; i < sizeof(str) / sizeof(*str); i++) {
        char *meta = libvlc_media_get_meta(m, meta_id[i]);
        if (!meta)
            meta = strdup("");

        jstring k = (*env)->NewStringUTF(env, str[i]);
        (*env)->SetObjectArrayElement(env, array, 2*i, k);
        jstring v = (*env)->NewStringUTF(env, meta);
        (*env)->SetObjectArrayElement(env, array, 2*i+1, v);

        free(meta);
   }

   libvlc_media_release(m);
   return array;
}

static void create_player_and_play(JNIEnv* env, jobject thiz,
                                   jlong instance, int position) {
    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);

    /* Create a media player playing environment */
    libvlc_media_list_player_t* p_mlp = libvlc_media_list_player_new((libvlc_instance_t*)(intptr_t)instance);
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
    };
    for(int i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); i++)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);

    libvlc_media_list_player_set_media_list(p_mlp, p_mlist);
    libvlc_media_list_player_set_media_player(p_mlp, mp);

    /* Keep a pointer to this media player */
    setLong(env, thiz, "mMediaListPlayerInstance", (jlong)(intptr_t)p_mlp);
    setLong(env, thiz, "mInternalMediaPlayerInstance", (jlong)(intptr_t)mp);

    libvlc_media_list_player_play_item_at_index(p_mlp, position);
}

jint Java_org_videolan_vlc_LibVLC_readMedia(JNIEnv *env, jobject thiz,
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

void Java_org_videolan_vlc_LibVLC_playIndex(JNIEnv *env, jobject thiz,
                                            jlong instance, int position) {
    create_player_and_play(env, thiz, instance, position);
}

void Java_org_videolan_vlc_LibVLC_getMediaListItems(
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

jfloat Java_org_videolan_vlc_LibVLC_getRate(JNIEnv *env, jobject thiz) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        return libvlc_media_player_get_rate(mp);
    else
        return 1.00;
}

void Java_org_videolan_vlc_LibVLC_setRate(JNIEnv *env, jobject thiz, jfloat rate) {
    libvlc_media_player_t* mp = getMediaPlayer(env, thiz);
    if(mp)
        libvlc_media_player_set_rate(mp, rate);
}

jboolean Java_org_videolan_vlc_LibVLC_hasVideoTrack(JNIEnv *env, jobject thiz,
                                                    jlong i_instance, jstring fileLocation)
{
    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = new_media(i_instance, env, thiz, fileLocation, false, false);
    if (p_m == NULL)
    {
        LOGE("Could not create the media!");
        return JNI_FALSE;
    }

    /* Get the tracks information of the media. */
    libvlc_media_track_info_t *p_tracks;
    libvlc_media_parse(p_m);

    libvlc_media_player_t* p_mp = libvlc_media_player_new_from_media(p_m);

    struct length_change_monitor* monitor;
    monitor = malloc(sizeof(struct length_change_monitor));
    if (!monitor) return 0;

    /* Initialize pthread variables. */
    pthread_mutex_init(&monitor->doneMutex, NULL);
    pthread_cond_init(&monitor->doneCondVar, NULL);
    monitor->length_changed = false;

    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(p_mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_player_play( p_mp );

    pthread_mutex_lock(&monitor->doneMutex);

    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 2; /* If "VLC can't open the file", return */
    int mp_alive = 1;
    while( !monitor->length_changed && mp_alive ) {
        pthread_cond_timedwait(&monitor->doneCondVar, &monitor->doneMutex, &deadline);
        mp_alive = libvlc_media_player_will_play(p_mp);
    }
    pthread_mutex_unlock(&monitor->doneMutex);

    int i_nbTracks;
    if( mp_alive )
        i_nbTracks = libvlc_video_get_track_count(p_mp);
    else
        i_nbTracks = -1;
    LOGI("Number of video tracks: %d",i_nbTracks);

    libvlc_event_detach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_player_stop(p_mp);
    libvlc_media_player_release(p_mp);
    libvlc_media_release(p_m);

    pthread_mutex_destroy(&monitor->doneMutex);
    pthread_cond_destroy(&monitor->doneCondVar);
    free(monitor);

    if(i_nbTracks > 0)
        return JNI_TRUE;
    else if(i_nbTracks < 0)
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "VLC can't open the file");
    else
        return JNI_FALSE;
}

jobjectArray read_track_info_internal(JNIEnv *env, jobject thiz, libvlc_media_t* p_m)
{
    /* get java class */
    jclass cls = (*env)->FindClass( env, "org/videolan/vlc/TrackInfo" );
    if ( !cls )
    {
        LOGE("Failed to load class (org/videolan/vlc/TrackInfo)" );
        return NULL;
    }

    /* get java class contructor */
    jmethodID clsCtor = (*env)->GetMethodID( env, cls, "<init>", "()V" );
    if ( !clsCtor )
    {
        LOGE("Failed to find class constructor (org/videolan/vlc/TrackInfo)" );
        return NULL;
    }

    /* Get the tracks information of the media. */
    libvlc_media_track_info_t *p_tracks;

    int i_nbTracks = libvlc_media_get_tracks_info(p_m, &p_tracks);
    jobjectArray array = (*env)->NewObjectArray(env, i_nbTracks + 1, cls, NULL);

    unsigned i;
    if (array != NULL)
    {
        for (i = 0; i <= i_nbTracks; ++i)
        {
            jobject item = (*env)->NewObject(env, cls, clsCtor);
            if (item == NULL)
                continue;
            (*env)->SetObjectArrayElement(env, array, i, item);

            // use last track for metadata
            if (i == i_nbTracks)
            {
                setInt(env, item, "Type", 3 /* TYPE_META */);
                setLong(env, item, "Length", libvlc_media_get_duration(p_m));
                setString(env, item, "Title", libvlc_media_get_meta(p_m, libvlc_meta_Title));
                setString(env, item, "Artist", libvlc_media_get_meta(p_m, libvlc_meta_Artist));
                setString(env, item, "Album", libvlc_media_get_meta(p_m, libvlc_meta_Album));
                setString(env, item, "Genre", libvlc_media_get_meta(p_m, libvlc_meta_Genre));
                setString(env, item, "ArtworkURL", libvlc_media_get_meta(p_m, libvlc_meta_ArtworkURL));
                continue;
            }

            setInt(env, item, "Id", p_tracks[i].i_id);
            setInt(env, item, "Type", p_tracks[i].i_type);
            setString(env, item, "Codec", (const char*)vlc_fourcc_GetDescription(0,p_tracks[i].i_codec));
            setString(env, item, "Language", p_tracks[i].psz_language);

            if (p_tracks[i].i_type == libvlc_track_video)
            {
                setInt(env, item, "Height", p_tracks[i].u.video.i_height);
                setInt(env, item, "Width", p_tracks[i].u.video.i_width);
                setFloat(env, item, "Framerate", p_tracks[i].u.video.f_frame_rate);
            }
            if (p_tracks[i].i_type == libvlc_track_audio)
            {
                setInt(env, item, "Channels", p_tracks[i].u.audio.i_channels);
                setInt(env, item, "Samplerate", p_tracks[i].u.audio.i_rate);
            }
        }
    }

    libvlc_media_tracks_info_release(p_tracks, i_nbTracks);
    return array;
}


jobjectArray Java_org_videolan_vlc_LibVLC_readTracksInfo(JNIEnv *env, jobject thiz,
                                                         jlong instance, jstring mrl)
{
    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = new_media(instance, env, thiz, mrl, false, false);
    if (p_m == NULL)
    {
        LOGE("Could not create the media!");
        return NULL;
    }

    libvlc_media_parse(p_m);
    jobjectArray jar = read_track_info_internal(env, thiz, p_m);
    libvlc_media_release(p_m);
    return jar;
}


jobjectArray Java_org_videolan_vlc_LibVLC_readTracksInfoPosition(JNIEnv *env, jobject thiz,
                                                         jint position)
{
    libvlc_media_list_t* p_mlist = getMediaList(env, thiz);
    libvlc_media_t *p_m = libvlc_media_list_item_at_index( p_mlist, position );
    if (p_m == NULL) {
        LOGE("Could not load get media @ position %d!", position);
        return NULL;
    } else
        return read_track_info_internal(env, thiz, p_m);
}

jlong Java_org_videolan_vlc_LibVLC_getLengthFromLocation(JNIEnv *env, jobject thiz,
                                                     jlong i_instance, jstring fileLocation)
{
    jlong length = 0;
    struct length_change_monitor *monitor;
    monitor = malloc(sizeof(*monitor));
    if (!monitor)
        return 0;

    /* Initialize pthread variables. */
    pthread_mutex_init(&monitor->doneMutex, NULL);
    pthread_cond_init(&monitor->doneCondVar, NULL);
    monitor->length_changed = false;

    /* Create a new item and assign it to the media player. */
    libvlc_media_t *m = new_media(i_instance, env, thiz, fileLocation, false, false);
    if (m == NULL)
    {
        LOGE("Could not create the media to play!");
        goto end;
    }

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new_from_media (m);
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, monitor);
    libvlc_media_release (m);
    libvlc_media_player_play( mp );

    pthread_mutex_lock(&monitor->doneMutex);
    while (!monitor->length_changed)
        pthread_cond_wait(&monitor->doneCondVar, &monitor->doneMutex);
    pthread_mutex_unlock(&monitor->doneMutex);

    length = libvlc_media_player_get_length( mp );
    libvlc_media_player_stop( mp );
    libvlc_media_player_release( mp );

end:
    pthread_mutex_destroy(&monitor->doneMutex);
    pthread_cond_destroy(&monitor->doneCondVar);
    free(monitor);

    return length;
}

jboolean Java_org_videolan_vlc_LibVLC_hasMediaPlayer(JNIEnv *env, jobject thiz)
{
    return !!getMediaListPlayer(env, thiz);
}

jboolean Java_org_videolan_vlc_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_list_player_is_playing(mp);
    else
        return 0;
}

jboolean Java_org_videolan_vlc_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return !!libvlc_media_player_is_seekable(mp);
    return 0;
}

void Java_org_videolan_vlc_LibVLC_play(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        libvlc_media_list_player_play(mp);
}

void Java_org_videolan_vlc_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        libvlc_media_list_player_pause(mp);
}

void Java_org_videolan_vlc_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        libvlc_media_list_player_stop(mp);
}

void Java_org_videolan_vlc_LibVLC_previous(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        libvlc_media_list_player_previous(mp);
}

void Java_org_videolan_vlc_LibVLC_next(JNIEnv *env, jobject thiz)
{
    libvlc_media_list_player_t *mp = getMediaListPlayer(env, thiz);
    if (mp)
        libvlc_media_list_player_next(mp);
}

jint Java_org_videolan_vlc_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_volume(mp);
    return -1;
}

jint Java_org_videolan_vlc_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume(mp, (int) volume);
    return -1;
}

jlong Java_org_videolan_vlc_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_media_player_get_time(mp);
    return -1;
}

void Java_org_videolan_vlc_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_time(mp, time);
}

jfloat Java_org_videolan_vlc_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jfloat) libvlc_media_player_get_position(mp);
    return -1;
}

void Java_org_videolan_vlc_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        libvlc_media_player_set_position(mp, pos);
}

jlong Java_org_videolan_vlc_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jlong) libvlc_media_player_get_length(mp);
    return -1;
}

jstring Java_org_videolan_vlc_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_org_videolan_vlc_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_vlc_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}

jint Java_org_videolan_vlc_LibVLC_getAudioTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_audio_get_track_count(mp);
    return -1;
}

jobjectArray Java_org_videolan_vlc_LibVLC_getAudioTrackDescription(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (!mp)
        return NULL;

    int i_nbTracks = libvlc_audio_get_track_count(mp) - 1;
    if (i_nbTracks < 0)
        i_nbTracks = 0;
    jobjectArray array = (*env)->NewObjectArray(env, i_nbTracks,
            (*env)->FindClass(env, "java/lang/String"),
            NULL);

    libvlc_track_description_t *first = libvlc_audio_get_track_description(mp);
    libvlc_track_description_t *desc = first != NULL ? first->p_next : NULL;
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
    {
        jstring name = (*env)->NewStringUTF(env, desc->psz_name);
        (*env)->SetObjectArrayElement(env, array, i, name);
        desc = desc->p_next;
    }
    libvlc_track_description_list_release(first);
    return array;
}

jint Java_org_videolan_vlc_LibVLC_getAudioTrack(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_audio_get_track(mp);
    return -1;
}

jint Java_org_videolan_vlc_LibVLC_setAudioTrack(JNIEnv *env, jobject thiz, jint index)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_audio_set_track(mp, index);
    return -1;
}

jint Java_org_videolan_vlc_LibVLC_getVideoTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_video_get_track_count(mp);
    return -1;
}

jobjectArray Java_org_videolan_vlc_LibVLC_getSpuTrackDescription(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (!mp)
        return NULL;

    int i_nbTracks = libvlc_video_get_spu_count(mp);
    jobjectArray array = (*env)->NewObjectArray(env, i_nbTracks,
            (*env)->FindClass(env, "java/lang/String"),
            NULL);

    libvlc_track_description_t *first = libvlc_video_get_spu_description(mp);
    libvlc_track_description_t *desc = first;
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
    {
        jstring name = (*env)->NewStringUTF(env, desc->psz_name);
        (*env)->SetObjectArrayElement(env, array, i, name);
        desc = desc->p_next;
    }
    libvlc_track_description_list_release(first);
    return array;
}

jint Java_org_videolan_vlc_LibVLC_getSpuTracksCount(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return (jint) libvlc_video_get_spu_count(mp);
    return -1;
}

jint Java_org_videolan_vlc_LibVLC_getSpuTrack(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_get_spu(mp);
    return -1;
}

jint Java_org_videolan_vlc_LibVLC_setSpuTrack(JNIEnv *env, jobject thiz, jint index)
{
    libvlc_media_player_t *mp = getMediaPlayer(env, thiz);
    if (mp)
        return libvlc_video_set_spu(mp, index);
    return -1;
}

void Java_org_videolan_vlc_LibVLC_nativeReadDirectory(JNIEnv *env, jobject thiz, jstring path, jobject arrayList)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);

    DIR* p_dir = opendir(psz_path);
    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    if(!p_dir)
        return;

    jclass arrayClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID methodID = (*env)->GetMethodID(env, arrayClass, "add", "(Ljava/lang/Object;)Z");

    struct dirent* p_dirent;
    jstring str;
    while(1) {
        errno = 0;
        p_dirent = readdir(p_dir);
        if(p_dirent == NULL) {
            if(errno > 0) /* error reading this entry */
                continue;
            else if(errno == 0) /* end of stream */
                break;
        }
        str = (*env)->NewStringUTF(env, p_dirent->d_name);
        (*env)->CallBooleanMethod(env, arrayList, methodID, str);
        (*env)->DeleteLocalRef(env, str);
    }
    closedir(p_dir);
}

jboolean Java_org_videolan_vlc_LibVLC_nativeIsPathDirectory(JNIEnv *env, jobject thiz, jstring path)
{
    jboolean isCopy;
    /* Get C string */
    const char* psz_path = (*env)->GetStringUTFChars(env, path, &isCopy);

    jboolean isDirectory;
    struct stat buf;
    if(stat(psz_path, &buf) != 0)
        /* couldn't stat */
        isDirectory = JNI_FALSE;
    else {
        if(S_ISDIR(buf.st_mode))
            isDirectory = JNI_TRUE;
        else
            isDirectory = JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, path, psz_path);
    return isDirectory;
}
