LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c aout.c thumbnailer.c

LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include

ifeq ($(NO_NEON),)
ARCH=armeabi-v7a
else
ARCH=armeabi
endif

LOCAL_LDLIBS := -L$(VLC_CONTRIB)/lib \
	$(VLC_MODULES) \
	$(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
	$(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
	$(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
	-ldl -lz -lm -logg -lvorbisenc -lvorbis -lFLAC -lspeex -lspeexdsp -ltheora \
	-lavformat -lavcodec -lswscale -lavutil -lpostproc \
	-lmpeg2 -lpng -ldca -ldvbpsi -ltwolame -lkate -llog -la52 \
	-lebml -lmatroska -ltag \
	$(ANDROID_NDK)/sources/cxx-stl/gnu-libstdc++/libs/$(ARCH)/libstdc++.a

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)

LOCAL_MODULE     := libiomx
LOCAL_SRC_FILES  := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/modules/codec/omxil $(ANDROID_SYS_HEADERS)/frameworks/base/include $(ANDROID_SYS_HEADERS)/system/core/include
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder

include $(BUILD_SHARED_LIBRARY)
