LOCAL_PATH := $(call my-dir)

# VLC's buildsystem resulting binaries
include $(CLEAN_VARS)
LOCAL_MODULE := libvlccompat
LOCAL_SRC_FILES := $(VLC_BUILD_DIR)/compat/.libs/libcompat.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAN_VARS)
LOCAL_MODULE := libvlccore
LOCAL_SRC_FILES := $(VLC_BUILD_DIR)/src/.libs/libvlccore.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAN_VARS)
LOCAL_MODULE := libvlc-native
LOCAL_MODULE_FILENAME := libvlc
LOCAL_SRC_FILES := $(VLC_BUILD_DIR)/lib/.libs/libvlc.a
LOCAL_STATIC_LIBRARIES := libvlccore
include $(PREBUILT_STATIC_LIBRARY)

# libvlc static build with all its modules
include $(CLEAR_VARS)
LOCAL_MODULE    := libvlc
LOCAL_SRC_FILES := $(VLC_BUILD_DIR)/ndk/libvlcjni-modules.c \
				   $(VLC_BUILD_DIR)/ndk/libvlcjni-symbols.c \
				   $(VLC_BUILD_DIR)/ndk/dummy.cpp
LOCAL_LDFLAGS := -L$(VLC_CONTRIB)/lib
LOCAL_LDLIBS := \
    $(VLC_MODULES) \
    $(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
    $(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
    $(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
    $(VLC_CONTRIB_LDFLAGS) \
    -ldl -lz -lm -llog \
    -la52 -ljpeg \
    $(VLC_LDFLAGS)
LOCAL_CXXFLAGS := -std=c++11
# This duplicates the libvlc* link flags, but it propagates the dependency
# on the native build which is what we want overall
LOCAL_STATIC_LIBRARIES := libvlccore libvlccompat libvlc-native
include $(BUILD_SHARED_LIBRARY)

# libvlcjni
include $(CLEAR_VARS)
LOCAL_MODULE    := libvlcjni
LOCAL_SRC_FILES := libvlcjni.c
LOCAL_SRC_FILES += libvlcjni-mediaplayer.c
LOCAL_SRC_FILES += libvlcjni-vlcobject.c
LOCAL_SRC_FILES += libvlcjni-media.c libvlcjni-medialist.c libvlcjni-mediadiscoverer.c libvlcjni-rendererdiscoverer.c
LOCAL_SRC_FILES += libvlcjni-dialog.c
LOCAL_SRC_FILES += std_logger.c
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include $(VLC_BUILD_DIR)/include
LOCAL_CFLAGS := -std=c11
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libvlc

include $(BUILD_SHARED_LIBRARY)
