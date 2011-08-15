#include <stdio.h>
#include <string.h>
#include <assert.h>

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <pthread.h>
#include <vlc/vlc.h>

#include <jni.h>
#include <android/api-level.h>

#include "libvlcjni.h"
#include "aout.h"

#define LOG_TAG "VLC/JNI/main"
#include "log.h"

#define NAME1(CLZ, FUN) Java_##CLZ##_##FUN
#define NAME2(CLZ, FUN) NAME1(CLZ, FUN)

#define NAME(FUN) NAME2(CLASS, FUN)



jint getMediaPlayer(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    return (*env)->GetIntField(env, thiz, fieldMP);
}

jboolean releaseMediaPlayer(JNIEnv *env, jobject thiz)
{
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          "mMediaPlayerInstance", "I");
    jint mediaPlayer = (*env)->GetIntField(env, thiz, fieldMP);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_stop(mp);
        libvlc_media_player_release(mp);
        (*env)->SetIntField(env, thiz, fieldMP, 0);
    }
    return (mediaPlayer == 0);
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

void jni_SetAndroidSurfaceSize(int width, int height)
{
    if (vout_android_gui == NULL)
        return;

    JNIEnv *p_env;

    (*myVm)->AttachCurrentThread (myVm, &p_env, NULL);
    jclass cls = (*p_env)->GetObjectClass (p_env, vout_android_gui);
    jmethodID methodId = (*p_env)->GetMethodID (p_env, cls, "setSurfaceSize", "(II)V");

    (*p_env)->CallVoidMethod (p_env, vout_android_gui, methodId, width, height);

    (*p_env)->DeleteLocalRef(p_env, cls);
    (*myVm)->DetachCurrentThread (myVm);
}

static const libvlc_event_type_t mp_events[] = {
    libvlc_MediaPlayerPlaying,
    libvlc_MediaPlayerPaused,
    libvlc_MediaPlayerEndReached,
    libvlc_MediaPlayerStopped,
};

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    int status;
    JNIEnv *env;
    JavaVM *myVm = (JavaVM*)data;
    jint etype = ev->type;

    int isAttached = 0;

    if (eventManagerInstance == NULL)
	    return;

    status = (*myVm)->GetEnv(myVm, (void**) &env, JNI_VERSION_1_2);
    if (status < 0) {
        LOGD("vlc_event_callback: failed to get JNI environment, "
             "assuming native thread");
        status = (*myVm)->AttachCurrentThread(myVm, &env, NULL);
        if (status < 0)
            return;
        isAttached = 1;
    }

    /* Get the object class */
    jclass cls = (*env)->GetObjectClass(env, eventManagerInstance);
    if (!cls) {
        LOGE("EventManager: failed to get class reference");
        if (isAttached) (*myVm)->DetachCurrentThread(myVm);
        return;
    }

    /* Find the callback ID */
    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(I)V");
    if (!methodID) {
        LOGE("EventManager: failed to get the callback method");
        if (isAttached) (*myVm)->DetachCurrentThread(myVm);
        return;
    }

    (*env)->CallVoidMethod(env, eventManagerInstance, methodID, etype);
    if (isAttached) (*myVm)->DetachCurrentThread(myVm);
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

void Java_org_videolan_vlc_android_LibVLC_attachSurface(JNIEnv *env, jobject thiz, jobject surf, jobject gui, jint width, jint height) {
    jclass clz;
    jfieldID fid;
    jthrowable exp;

    pthread_mutex_lock(&vout_android_lock);
    //vout_android_ref = (*env)->NewGlobalRef(env, surf);
    clz = (*env)->GetObjectClass(env, surf);
    fid = (*env)->GetFieldID(env, clz, "mSurface", "I");
    if (fid == NULL) {
        exp = (*env)->ExceptionOccurred(env);
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

void Java_org_videolan_vlc_android_LibVLC_detachSurface(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&vout_android_lock);
    //(*env)->DeleteGlobalRef(env, vout_android_ref);
    vout_android_surf = NULL;
    if (vout_android_gui != NULL)
        (*env)->DeleteGlobalRef(env, vout_android_gui);
    vout_android_gui = NULL;
    pthread_mutex_unlock(&vout_android_lock);
}

void Java_org_videolan_vlc_android_LibVLC_nativeInit(JNIEnv *env, jobject thiz)
{
    const char *argv[] = {"-I", "dummy", "-vvv", "--no-plugins-cache",
                          "--no-drop-late-frames"};

    libvlc_instance_t *instance =
            libvlc_new_with_builtins(sizeof(argv) / sizeof(*argv),
                                     argv, vlc_builtins_modules);

    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz,
                                        "mLibVlcInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) instance);

    if (!instance)
    {
        jclass exc = (*env)->FindClass(env, "vlc/android/LibVlcException");
        (*env)->ThrowNew(env, exc, "Unable to instantiate LibVLC");
    }

    LOGI("LibVLC initialized: %p", instance);
    return;
}


void Java_org_videolan_vlc_android_LibVLC_nativeDestroy(JNIEnv *env, jobject thiz)
{
    releaseMediaPlayer(env, thiz);
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz, "mLibVlcInstance", "I");
    jint libVlcInstance = (*env)->GetIntField(env, thiz, field);
    if (!libVlcInstance)
        return; // Already destroyed

    libvlc_instance_t *instance = (libvlc_instance_t*) libVlcInstance;
    libvlc_release(instance);

    (*env)->SetIntField(env, thiz, field, 0);
}

int currentSdk( JNIEnv *p_env, jobject thiz )
{
    jclass  cls = (*p_env)->FindClass( p_env, "org/videolan/vlc/android/Util" );
    if ( !cls )
    {
        LOGE( "Failed to load util class (org/videolan/vlc/android/Util)" );
        return 0;
    }
    jmethodID methodSdkVersion = (*p_env)->GetStaticMethodID( p_env, cls,
                                                       "getApiLevel", "()I" );
    if ( !methodSdkVersion )
    {
        LOGE("Failed to load method getApiLevel()" );
        return 0;
    }
    int version = (*p_env)->CallStaticIntMethod( p_env, cls, methodSdkVersion );
    LOGI("Got version: %d\n", version );
    return version;
}

void Java_org_videolan_vlc_android_LibVLC_detachEventManager(JNIEnv *env, jobject thiz)
{
    if (eventManagerInstance != NULL) {
        (*env)->DeleteGlobalRef(env, eventManagerInstance);
        eventManagerInstance = NULL;
    }
}

void Java_org_videolan_vlc_android_LibVLC_setEventManager(JNIEnv *env, jobject thiz, jobject eventManager)
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

    jmethodID methodID = (*env)->GetMethodID(env, cls, "callback", "(I)V");
    if (!methodID) {
        LOGE("setEventManager: failed to get the callback method");
        return;
    }

    eventManagerInstance = (*env)->NewGlobalRef(env, eventManager);
}

jobjectArray Java_org_videolan_vlc_android_LibVLC_readMediaMeta(JNIEnv *env,
                                    jobject thiz, jint instance, jstring mrl)
{
    jboolean isCopy;
    jobjectArray array = (*env)->NewObjectArray(env, 8,
            (*env)->FindClass(env, "java/lang/String"),
            (*env)->NewStringUTF(env, ""));


    static const char str[4][7] = {
        "artist", "album", "title", "genre",
    };
    static const libvlc_meta_t meta_id[4] = {
        libvlc_meta_Artist,
        libvlc_meta_Album,
        libvlc_meta_Title,
        libvlc_meta_Genre,
    };

    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);
    libvlc_media_parse(m);

    int i;
    for (i=0; i < 4 ; i++) {
        char *meta = libvlc_media_get_meta(m, meta_id[i]);
        if (!meta)
            meta = strdup("unknown");

        jstring k = (*env)->NewStringUTF(env, str[i]);
        (*env)->SetObjectArrayElement(env, array, 2*i, k);
        jstring v = (*env)->NewStringUTF(env, meta);
        (*env)->SetObjectArrayElement(env, array, 2*i+1, v);

        free(meta);
   }

   libvlc_media_release(m);
   return array;
}

void Java_org_videolan_vlc_android_LibVLC_readMedia(JNIEnv *env, jobject thiz,
                                       jint instance, jstring mrl)
{
    jboolean isCopy;
    int i;
    const char *psz_mrl = (*env)->GetStringUTFChars(env, mrl, &isCopy);

    /* Release previous media player, if any */
    releaseMediaPlayer(env, thiz);

    /* Create a new item */
    libvlc_media_t *m = libvlc_media_new_path((libvlc_instance_t*)instance,
                                              psz_mrl);

    /* Create a media player playing environment */
    libvlc_media_player_t *mp = libvlc_media_player_new((libvlc_instance_t*)instance);

    jobject myJavaLibVLC = (*env)->NewGlobalRef(env, thiz);

    libvlc_media_player_set_media(mp, m);

    if ( currentSdk( env, thiz )  < 9 ) //On newer version, we can use SLES
    {
        libvlc_audio_set_callbacks(mp, aout_open, aout_play, aout_close,
                                   (void*) myJavaLibVLC);
    }

    /* No need to keep the media now */
    libvlc_media_release(m);

    /* Connect the event manager */
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);

    /* Subscribe to the events */

    for (i = 0; i < (sizeof(mp_events) / sizeof(*mp_events)); ++i)
        libvlc_event_attach(ev, mp_events[i], vlc_event_callback, myVm);

    /* Keep a pointer to this media player */
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clazz,
                                        "mMediaPlayerInstance", "I");
    (*env)->SetIntField(env, thiz, field, (jint) mp);

    /* Play the media. */
    libvlc_media_player_play(mp);

    //libvlc_media_player_release(mp);

    (*env)->ReleaseStringUTFChars(env, mrl, psz_mrl);
}

jboolean Java_org_videolan_vlc_android_LibVLC_hasVideoTrack(JNIEnv *env, jobject thiz, 
                                                            jint i_instance, jstring filePath) 
{   
    libvlc_instance_t *p_instance = (libvlc_instance_t *)i_instance;    
    const char *psz_filePath = (*env)->GetStringUTFChars(env, filePath, 0);

    /* Create a new item and assign it to the media player. */
    libvlc_media_t *p_m = libvlc_media_new_path(p_instance, psz_filePath);
    if (p_m == NULL)
    {
        LOGE("Couldn't create the media!");
        return 0;
    }

    /* Get the tracks information of the media. */
    libvlc_media_track_info_t *p_tracks;
    libvlc_media_parse(p_m);
    int i_nbTracks = libvlc_media_get_tracks_info(p_m, &p_tracks);
    
    unsigned i;
    for (i = 0; i < i_nbTracks; ++i)
    {   
        if (p_tracks[i].i_type == libvlc_track_video)
            return 1;
    }
    return 0;
}

static pthread_mutex_t doneMutex;
static pthread_cond_t doneCondVar;

static void length_changed_callback(const libvlc_event_t *ev, void *data)
{    
    pthread_mutex_lock(&doneMutex);
    pthread_cond_signal(&doneCondVar);
    pthread_mutex_unlock(&doneMutex);
}

jlong Java_org_videolan_vlc_android_LibVLC_getLengthFromFile(JNIEnv *env, jobject thiz, 
                                                        jint i_instance, jstring filePath) 
{   
    libvlc_media_t *m;   
    libvlc_media_player_t *mp; 

    /* Initialize pthread variables. */
    pthread_mutex_init(&doneMutex, NULL);
    pthread_cond_init(&doneCondVar, NULL);

    libvlc_instance_t *p_instance = (libvlc_instance_t *)i_instance;    
    const char *psz_filePath = (*env)->GetStringUTFChars(env, filePath, 0);

    /* Create a new item and assign it to the media player. */
    m = libvlc_media_new_path(p_instance, psz_filePath);
    if (m == NULL)
    {
        LOGE("Couldn't create the media to play!");
        return;
    }

    /* Create a media player playing environment */
    mp = libvlc_media_player_new_from_media (m);
    libvlc_event_manager_t *ev = libvlc_media_player_event_manager(mp);
    libvlc_event_attach(ev, libvlc_MediaPlayerLengthChanged, length_changed_callback, NULL);
    libvlc_media_release (m);
    libvlc_media_player_play( mp );
    pthread_mutex_lock(&doneMutex);
    pthread_cond_wait(&doneCondVar, &doneMutex);
    pthread_mutex_unlock(&doneMutex); 
    jlong length = (jlong)libvlc_media_player_get_length( mp );
    libvlc_media_player_stop( mp );
    libvlc_media_player_release( mp );  

    pthread_mutex_destroy(&doneMutex);
    pthread_cond_destroy(&doneCondVar);  

    return length;
}

jboolean Java_org_videolan_vlc_android_LibVLC_hasMediaPlayer(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
        return 1;
    return 0;
}

jboolean Java_org_videolan_vlc_android_LibVLC_isPlaying(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return ( libvlc_media_player_is_playing( mp ) == 1 );
    }
    return 0;
}

jboolean Java_org_videolan_vlc_android_LibVLC_isSeekable(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return ( libvlc_media_player_is_seekable( mp ) == 1 );
    }
    return 0;
}

void Java_org_videolan_vlc_android_LibVLC_play(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_play( mp );
    }
}

void Java_org_videolan_vlc_android_LibVLC_pause(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_pause( mp );
    }
}

void Java_org_videolan_vlc_android_LibVLC_stop(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_stop( mp );
    }
}

jint Java_org_videolan_vlc_android_LibVLC_getVolume(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jint) libvlc_audio_get_volume( mp );
    }
    return -1;
}

jint Java_org_videolan_vlc_android_LibVLC_setVolume(JNIEnv *env, jobject thiz, jint volume)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        //Returns 0 if the volume was set, -1 if it was out of range or error
        return (jint) libvlc_audio_set_volume( mp, (int) volume );
    }
    return -1;
}

jlong Java_org_videolan_vlc_android_LibVLC_getTime(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return libvlc_media_player_get_time( mp );
    }
    return -1;
}

void Java_org_videolan_vlc_android_LibVLC_setTime(JNIEnv *env, jobject thiz, jlong time)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_set_time( mp, time );
    }
}

jfloat Java_org_videolan_vlc_android_LibVLC_getPosition(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jfloat) libvlc_media_player_get_position( mp );
    }
    return -1;
}

void Java_org_videolan_vlc_android_LibVLC_setPosition(JNIEnv *env, jobject thiz, jfloat pos)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        libvlc_media_player_set_position( mp, pos );
    }
}

jlong Java_org_videolan_vlc_android_LibVLC_getLength(JNIEnv *env, jobject thiz)
{
    jint mediaPlayer = getMediaPlayer(env, thiz);
    if (mediaPlayer != 0)
    {
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mediaPlayer;
        return (jlong) libvlc_media_player_get_length( mp );
    }
    return -1;
}

jstring Java_org_videolan_vlc_android_LibVLC_version(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_version());
}

jstring Java_org_videolan_vlc_android_LibVLC_compiler(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_compiler());
}

jstring Java_org_videolan_vlc_android_LibVLC_changeset(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, libvlc_get_changeset());
}
