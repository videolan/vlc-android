LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := jniloader
LOCAL_SRC_FILES := jniloader.c
LOCAL_LDFLAGS := -ldl -llog
include $(BUILD_SHARED_LIBRARY)
