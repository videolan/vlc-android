#include <string.h>
#include <jni.h>

#include <vlc/libvlc.h>


jstring Java_vlc_android_vlc_getLibvlcVersion(JNIEnv* env, jobject thiz)
{  
    return (*env)->NewStringUTF(env, libvlc_get_version());
}
