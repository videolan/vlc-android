LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libvlc
ARCH=$(APP_ABI)
LOCAL_SRC_FILES += libvlcjni-modules.c libvlcjni-symbols.c dummy.cpp
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
	$(VLC_LDFLAGS)

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c
LOCAL_SRC_FILES += libvlcjni-mediaplayer.c
LOCAL_SRC_FILES += libvlcjni-vlcobject.c
LOCAL_SRC_FILES += libvlcjni-media.c libvlcjni-medialist.c libvlcjni-mediadiscoverer.c
LOCAL_SRC_FILES += libvlcjni-dialog.c
LOCAL_SRC_FILES += thumbnailer.c
LOCAL_SRC_FILES += std_logger.c

LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include

ARCH=$(APP_ABI)

LOCAL_CFLAGS := -std=c11
ifeq ($(ARCH), armeabi-v7a)
	LOCAL_CFLAGS += -DHAVE_ARMEABI_V7A
endif

LOCAL_SHARED_LIBRARIES:= libvlc

include $(BUILD_SHARED_LIBRARY)
