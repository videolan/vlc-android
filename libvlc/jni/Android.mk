LOCAL_PATH := $(call my-dir)
MEDIALIBRARY_JNI_DIR := $(LOCAL_PATH)/../../medialibrary/jni

# libvlc jni static library
include $(CLEAR_VARS)
LOCAL_MODULE    := vlcjni_static
LOCAL_SRC_FILES := libvlcjni.c
LOCAL_SRC_FILES += libvlcjni-mediaplayer.c
LOCAL_SRC_FILES += libvlcjni-vlcobject.c
LOCAL_SRC_FILES += libvlcjni-media.c libvlcjni-medialist.c libvlcjni-mediadiscoverer.c libvlcjni-rendererdiscoverer.c
LOCAL_SRC_FILES += libvlcjni-dialog.c
LOCAL_SRC_FILES += thumbnailer.c
LOCAL_SRC_FILES += std_logger.c
LOCAL_SRC_FILES += dummy.cpp
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include $(VLC_BUILD_DIR)/include $(MEDIALIBRARY_JNI_DIR) $(LOCAL_PATH)/loader

LOCAL_CFLAGS := -std=c11
LOCAL_CXXFLAGS := -std=c++11
include $(BUILD_STATIC_LIBRARY)

# libvlc dynamic library
include $(CLEAR_VARS)
LOCAL_MODULE    := vlcjni
LOCAL_SRC_FILES := libvlcjni-modules.c libvlcjni-symbols.c
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
	$(MEDIALIBRARY_LDLIBS) \
	$(VLC_LDFLAGS) \
	-llog

LOCAL_WHOLE_STATIC_LIBRARIES := libvlcjni_static
ifeq ($(BUILD_ML), 1)
LOCAL_WHOLE_STATIC_LIBRARIES += libmla
endif
include $(BUILD_SHARED_LIBRARY)

ifeq ($(BUILD_ML), 1)
JNILOADER_INCLUDES := $(LOCAL_PATH)/loader
$(call import-add-path, $(MEDIALIBRARY_JNI_DIR))
$(call import-module, .)
endif
