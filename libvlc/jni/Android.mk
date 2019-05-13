LOCAL_PATH := $(call my-dir)

# libvlcjni
include $(CLEAR_VARS)
LOCAL_MODULE    := libvlcjni
LOCAL_SRC_FILES := libvlcjni.c
LOCAL_SRC_FILES += libvlcjni-mediaplayer.c
LOCAL_SRC_FILES += libvlcjni-vlcobject.c
LOCAL_SRC_FILES += libvlcjni-media.c libvlcjni-medialist.c libvlcjni-mediadiscoverer.c libvlcjni-rendererdiscoverer.c
LOCAL_SRC_FILES += libvlcjni-dialog.c
LOCAL_SRC_FILES += thumbnailer.c
LOCAL_SRC_FILES += std_logger.c
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include $(VLC_BUILD_DIR)/include
LOCAL_CFLAGS := -std=c11
LOCAL_LDLIBS := -llog $(VLC_OUT_LDLIBS)
include $(BUILD_SHARED_LIBRARY)
