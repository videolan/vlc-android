LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := medialibrary.cpp AndroidMediaLibrary.cpp AndroidDeviceLister.cpp utils.cpp
LOCAL_MODULE    := mla
LOCAL_LDLIBS    := $(MEDIALIBRARY_LDLIBS) -llog
LOCAL_C_INCLUDES := $(MEDIALIBRARY_INCLUDE_DIR)
include $(BUILD_SHARED_LIBRARY)
