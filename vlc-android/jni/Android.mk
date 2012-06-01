LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c aout.c thumbnailer.c pthread-condattr.c pthread-rwlocks.c eventfd.c sem.c

LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include

ifeq ($(NO_NEON),)
ARCH=armeabi-v7a
else
ARCH=armeabi
endif

ifeq ($(NO_NDK_V7),1)
CPP_STATIC=$(ANDROID_NDK)/sources/cxx-stl/gnu-libstdc++/libs/$(ARCH)/libstdc++.a
else
CPP_STATIC=$(ANDROID_NDK)/sources/cxx-stl/gnu-libstdc++/libs/$(ARCH)/libgnustl_static.a
endif

LOCAL_CFLAGS := -std=gnu99
LOCAL_LDLIBS := -L$(VLC_CONTRIB)/lib \
	$(VLC_MODULES) \
	$(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
	$(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
	$(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
	-ldl -lz -lm -llog \
	-ldvbpsi -lebml -lmatroska -ltag \
	-logg -lFLAC -ltheora \
	-lmpeg2 -ldca -la52 \
	-lavformat -lavcodec -lswscale -lavutil -lpostproc -lgsm -lopenjpeg \
	-lliveMedia -lUsageEnvironment -lBasicUsageEnvironment -lgroupsock \
	-lspeex -lspeexdsp \
	-lxml2 -lpng -lgnutls -lgcrypt -lgpg-error \
	-lfreetype -liconv -lass -lfribidi \
	$(CPP_STATIC)

include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)

LOCAL_MODULE     := libiomx-gingerbread
LOCAL_SRC_FILES  := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/modules/codec/omxil $(ANDROID_SYS_HEADERS_GINGERBREAD)/frameworks/base/include $(ANDROID_SYS_HEADERS_GINGERBREAD)/system/core/include
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE     := libiomx-hc
LOCAL_SRC_FILES  := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/modules/codec/omxil $(ANDROID_SYS_HEADERS_HC)/frameworks/base/include $(ANDROID_SYS_HEADERS_HC)/frameworks/base/native/include $(ANDROID_SYS_HEADERS_HC)/system/core/include $(ANDROID_SYS_HEADERS_HC)/hardware/libhardware/include
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE     := libiomx-ics
LOCAL_SRC_FILES  := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/modules/codec/omxil $(ANDROID_SYS_HEADERS_ICS)/frameworks/base/include $(ANDROID_SYS_HEADERS_ICS)/frameworks/base/native/include $(ANDROID_SYS_HEADERS_ICS)/system/core/include $(ANDROID_SYS_HEADERS_ICS)/hardware/libhardware/include
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder

include $(BUILD_SHARED_LIBRARY)
