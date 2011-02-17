#ifndef LIBVLCJNI_LOG_H
#define LIBVLCJNI_LOG_H

#include <android/log.h>

/* C files should define LOG_TAG before including this header */
#ifndef LOG_TAG
# define  LOG_TAG    "LibVLC/JNI"
#endif

#ifndef NDEBUG
# define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
# define LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)
#else
# define LOGD(...)  (void)0
# define LOGV(...)  (void)0
#endif
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARNING,LOG_TAG,__VA_ARGS__)

#endif // LIBVLCJNI_LOG_H
