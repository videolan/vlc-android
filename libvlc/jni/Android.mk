LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c libvlcjni-util.c libvlcjni-track.c libvlcjni-medialist.c aout.c vout.c libvlcjni-equalizer.c native_crash_handler.c
LOCAL_SRC_FILES += thumbnailer.c
ifneq ($(ANDROID_API),android-21)
# compat functions not needed after android-21
LOCAL_SRC_FILES += pthread-condattr.c pthread-rwlocks.c pthread-once.c eventfd.c sem.c pipe2.c
endif
LOCAL_SRC_FILES += wchar/wcpcpy.c
LOCAL_SRC_FILES += wchar/wcpncpy.c
LOCAL_SRC_FILES += wchar/wcscasecmp.c
LOCAL_SRC_FILES += wchar/wcscat.c
LOCAL_SRC_FILES += wchar/wcschr.c
LOCAL_SRC_FILES += wchar/wcscmp.c
LOCAL_SRC_FILES += wchar/wcscoll.c
LOCAL_SRC_FILES += wchar/wcscpy.c
LOCAL_SRC_FILES += wchar/wcscspn.c
LOCAL_SRC_FILES += wchar/wcsdup.c
LOCAL_SRC_FILES += wchar/wcslcat.c
LOCAL_SRC_FILES += wchar/wcslcpy.c
LOCAL_SRC_FILES += wchar/wcslen.c
LOCAL_SRC_FILES += wchar/wcsncasecmp.c
LOCAL_SRC_FILES += wchar/wcsncat.c
LOCAL_SRC_FILES += wchar/wcsncmp.c
LOCAL_SRC_FILES += wchar/wcsncpy.c
LOCAL_SRC_FILES += wchar/wcsnlen.c
LOCAL_SRC_FILES += wchar/wcspbrk.c
LOCAL_SRC_FILES += wchar/wcsrchr.c
LOCAL_SRC_FILES += wchar/wcsspn.c
LOCAL_SRC_FILES += wchar/wcsstr.c
LOCAL_SRC_FILES += wchar/wcstok.c
LOCAL_SRC_FILES += wchar/wcswidth.c
LOCAL_SRC_FILES += wchar/wcsxfrm.c
LOCAL_SRC_FILES += wchar/wmemchr.c
LOCAL_SRC_FILES += wchar/wmemcmp.c
LOCAL_SRC_FILES += wchar/wmemcpy.c
LOCAL_SRC_FILES += wchar/wmemmove.c
LOCAL_SRC_FILES += wchar/wmemset.c


LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include

ARCH=$(ANDROID_ABI)

CPP_STATIC=$(ANDROID_NDK)/sources/cxx-stl/gnu-libstdc++$(CXXSTL)/libs/$(ARCH)/libgnustl_static.a

LOCAL_CFLAGS := -std=gnu99
ifeq ($(ARCH), armeabi)
	LOCAL_CFLAGS += -DHAVE_ARMEABI
	# Needed by ARMv6 Thumb1 (the System Control coprocessor/CP15 is mandatory on ARMv6)
	# On newer ARM architectures we can use Thumb2
	LOCAL_ARM_MODE := arm
endif
ifeq ($(ARCH), armeabi-v7a)
	LOCAL_CFLAGS += -DHAVE_ARMEABI_V7A
endif
ifneq (,$(wildcard $(LOCAL_PATH)/../$(VLC_SRC_DIR)/modules/video_output/android/nativewindowpriv.c))
	LOCAL_CFLAGS += -DHAVE_IOMX_DR
endif
LOCAL_LDLIBS := -L$(VLC_CONTRIB)/lib \
	$(VLC_MODULES) \
	$(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
	$(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
	$(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
	-ldl -lz -lm -llog \
	-ldvbpsi -lebml -lmatroska -ltag \
	-logg -lFLAC -ltheora -lvorbis \
	-lmpeg2 -la52 \
	-lavformat -lavcodec -lswscale -lavutil -lpostproc -lgsm -lopenjpeg \
	-lliveMedia -lUsageEnvironment -lBasicUsageEnvironment -lgroupsock \
	-lspeex -lspeexdsp \
	-lxml2 -lpng -lgnutls -lgcrypt -lgpg-error \
	-lnettle -lhogweed -lgmp \
	-lfreetype -liconv -lass -lfribidi -lopus \
	-lEGL -lGLESv2 -ljpeg \
	-ldvdnav -ldvdread -ldvdcss \
	-lmad \
	$(CPP_STATIC)

include $(BUILD_SHARED_LIBRARY)

# libiomx-* build

LIBIOMX_INCLUDES_COMMON := $(VLC_SRC_DIR)/modules/codec/omxil

# no hwbuffer for gingerbread
LIBIOMX_INCLUDES_10 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/10/frameworks/base/include \
	$(ANDROID_SYS_HEADERS)/10/system/core/include \
	$(ANDROID_SYS_HEADERS)/10/hardware/libhardware/include

LIBIOMX_INCLUDES_13 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/13/frameworks/base/include \
	$(ANDROID_SYS_HEADERS)/13/frameworks/base/native/include \
	$(ANDROID_SYS_HEADERS)/13/system/core/include \
	$(ANDROID_SYS_HEADERS)/13/hardware/libhardware/include

LIBIOMX_INCLUDES_14 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/14/frameworks/base/include \
	$(ANDROID_SYS_HEADERS)/14/frameworks/base/native/include \
	$(ANDROID_SYS_HEADERS)/14/system/core/include \
	$(ANDROID_SYS_HEADERS)/14/hardware/libhardware/include

LIBIOMX_INCLUDES_18 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/18/frameworks/native/include \
	$(ANDROID_SYS_HEADERS)/18/frameworks/av/include \
	$(ANDROID_SYS_HEADERS)/18/system/core/include \
	$(ANDROID_SYS_HEADERS)/18/hardware/libhardware/include

LIBIOMX_INCLUDES_19 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/19/frameworks/native/include \
	$(ANDROID_SYS_HEADERS)/19/frameworks/av/include \
	$(ANDROID_SYS_HEADERS)/19/system/core/include \
	$(ANDROID_SYS_HEADERS)/19/hardware/libhardware/include

define build_iomx
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_SRC_FILES  := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(LIBIOMX_INCLUDES_$(2))
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder -llog -lcutils -lui
LOCAL_CFLAGS     := -Wno-psabi -DANDROID_API=$(2)
include $(BUILD_SHARED_LIBRARY)
endef

# call build_iomx for each libiomx-* in LIBVLC_LIBS
$(foreach IOMX_MODULE,$(filter libiomx.%,$(LIBVLC_LIBS)), \
	$(eval $(call build_iomx,$(IOMX_MODULE),$(subst libiomx.,,$(IOMX_MODULE)))))

LIBANW_SRC_FILES_COMMON += ../$(VLC_SRC_DIR)/modules/video_output/android/nativewindowpriv.c
# Once we always build this with a version of vlc that contains nativewindowpriv.c,
# we can remove this condition
ifneq (,$(wildcard $(LOCAL_PATH)/$(LIBANW_SRC_FILES_COMMON)))

define build_anw
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_SRC_FILES  := $(LIBANW_SRC_FILES_COMMON)
LOCAL_C_INCLUDES := $(LIBIOMX_INCLUDES_$(2))
LOCAL_LDLIBS     := -L$(ANDROID_LIBS) -llog -lhardware
LOCAL_CFLAGS     := $(LIBIOMX_CFLAGS_COMMON) -DANDROID_API=$(2)
include $(BUILD_SHARED_LIBRARY)
endef

$(foreach ANW_MODULE,$(filter libanw.%,$(LIBVLC_LIBS)), \
    $(eval $(call build_anw,$(ANW_MODULE),$(subst libanw.,,$(ANW_MODULE)))))
endif
