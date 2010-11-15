LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libvlcjni
LOCAL_SRC_FILES := libvlcjni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../../../include
LOCAL_LDLIBS := $(LOCAL_PATH)/../../vlc-android/libs/armeabi/libvlc.so

include $(BUILD_SHARED_LIBRARY)

