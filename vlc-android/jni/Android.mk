LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c libvlcjni-util.c libvlcjni-track.c libvlcjni-medialist.c aout.c vout.c libvlcjni-equalizer.c native_crash_handler.c
LOCAL_SRC_FILES += thumbnailer.c pthread-condattr.c pthread-rwlocks.c pthread-once.c eventfd.c sem.c
LOCAL_SRC_FILES += pipe2.c
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
	$(CPP_STATIC)

include $(BUILD_SHARED_LIBRARY)

# libiomx-* build

LIBIOMX_SRC_FILES_COMMON := ../$(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LIBIOMX_INCLUDES_COMMON := $(VLC_SRC_DIR)/modules/codec/omxil
LIBIOMX_LDLIBS_COMMON := -L$(ANDROID_LIBS) -lgcc -lstagefright -lmedia -lutils -lbinder
LIBIOMX_CFLAGS_COMMON := -Wno-psabi

LIBIOMX_INCLUDES_gingerbread := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS_GINGERBREAD)/frameworks/base/include \
	$(ANDROID_SYS_HEADERS_GINGERBREAD)/system/core/include

LIBIOMX_INCLUDES_hc := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS_HC)/frameworks/base/include \
	$(ANDROID_SYS_HEADERS_HC)/frameworks/base/native/include \
	$(ANDROID_SYS_HEADERS_HC)/system/core/include \
	$(ANDROID_SYS_HEADERS_HC)/hardware/libhardware/include

LIBIOMX_INCLUDES_ics := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS_ICS)/frameworks/base/include \
	$(ANDROID_SYS_HEADERS_ICS)/frameworks/base/native/include \
	$(ANDROID_SYS_HEADERS_ICS)/system/core/include \
	$(ANDROID_SYS_HEADERS_ICS)/hardware/libhardware/include

define build_iomx
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_SRC_FILES  := $(LIBIOMX_SRC_FILES_COMMON)
LOCAL_C_INCLUDES := $(LIBIOMX_INCLUDES_$(2))
LOCAL_LDLIBS     := $(LIBIOMX_LDLIBS_COMMON)
LOCAL_CFLAGS     := $(LIBIOMX_CFLAGS_COMMON)
include $(BUILD_SHARED_LIBRARY)
endef

# call build_iomx for each libiomx-* in LIBVLC_LIBS
$(foreach IOMX_MODULE,$(filter libiomx-%,$(LIBVLC_LIBS)), \
	$(eval $(call build_iomx,$(IOMX_MODULE),$(subst libiomx-,,$(IOMX_MODULE)))))
