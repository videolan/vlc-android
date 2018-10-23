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
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include $(VLC_BUILD_DIR)/include $(MEDIALIBRARY_JNI_DIR)
LOCAL_CFLAGS := -std=c11
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libvlc
include $(BUILD_SHARED_LIBRARY)

# libvlc
include $(CLEAR_VARS)
LOCAL_MODULE    := libvlc
LOCAL_SRC_FILES := libvlcjni-modules.c libvlcjni-symbols.c dummy.cpp
LOCAL_LDFLAGS := -L$(VLC_CONTRIB)/lib
LOCAL_LDLIBS := \
	$(VLC_MODULES) \
	$(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
	$(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
	$(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
	$(VLC_CONTRIB_LDFLAGS) \
	-ldl -lz -lm -llog \
	-lliveMedia -lUsageEnvironment -lBasicUsageEnvironment -lgroupsock \
	-la52 -ljpeg \
	-lavcodec -lebml \
	-llua \
	-lgcrypt -lgpg-error \
	$(VLC_LDFLAGS) \
	-llog
LOCAL_CXXFLAGS := -std=c++11
include $(BUILD_SHARED_LIBRARY)
