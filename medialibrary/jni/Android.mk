LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := medialibrary.cpp AndroidMediaLibrary.cpp AndroidDeviceLister.cpp utils.cpp
LOCAL_MODULE    := mla
LOCAL_C_INCLUDES := $(MEDIALIBRARY_INCLUDE_DIR) $(JNILOADER_INCLUDES)
LOCAL_CFLAGS := $(MEDIALIBRARY_CFLAGS)
include $(BUILD_STATIC_LIBRARY)
