LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
ANDROID_PRIVATE_LIBDIR := $(LOCAL_PATH)/../../android-libs

LOCAL_MODULE    := libvlcjni

LOCAL_SRC_FILES := libvlcjni.c
LOCAL_SRC_FILES += libvlcjni-mediaplayer.c
LOCAL_SRC_FILES += libvlcjni-vlcobject.c
LOCAL_SRC_FILES += libvlcjni-media.c libvlcjni-medialist.c libvlcjni-mediadiscoverer.c
LOCAL_SRC_FILES += native_crash_handler.c thumbnailer.c
LOCAL_SRC_FILES += std_logger.c

ifneq ($(APP_PLATFORM),android-21)
# compat functions not needed after android-21
LOCAL_SRC_FILES += compat/pthread-condattr.c compat/pthread-rwlocks.c
LOCAL_SRC_FILES += compat/pthread-once.c compat/eventfd.c compat/sem.c compat/pipe2.c
LOCAL_SRC_FILES += compat/localtime.c
LOCAL_SRC_FILES += compat/wchar/wcpcpy.c
LOCAL_SRC_FILES += compat/wchar/wcpncpy.c
LOCAL_SRC_FILES += compat/wchar/wcscasecmp.c
LOCAL_SRC_FILES += compat/wchar/wcscat.c
LOCAL_SRC_FILES += compat/wchar/wcschr.c
LOCAL_SRC_FILES += compat/wchar/wcscmp.c
LOCAL_SRC_FILES += compat/wchar/wcscoll.c
LOCAL_SRC_FILES += compat/wchar/wcscpy.c
LOCAL_SRC_FILES += compat/wchar/wcscspn.c
LOCAL_SRC_FILES += compat/wchar/wcsdup.c
LOCAL_SRC_FILES += compat/wchar/wcslcat.c
LOCAL_SRC_FILES += compat/wchar/wcslcpy.c
LOCAL_SRC_FILES += compat/wchar/wcslen.c
LOCAL_SRC_FILES += compat/wchar/wcsncasecmp.c
LOCAL_SRC_FILES += compat/wchar/wcsncat.c
LOCAL_SRC_FILES += compat/wchar/wcsncmp.c
LOCAL_SRC_FILES += compat/wchar/wcsncpy.c
LOCAL_SRC_FILES += compat/wchar/wcsnlen.c
LOCAL_SRC_FILES += compat/wchar/wcspbrk.c
LOCAL_SRC_FILES += compat/wchar/wcsrchr.c
LOCAL_SRC_FILES += compat/wchar/wcsspn.c
LOCAL_SRC_FILES += compat/wchar/wcsstr.c
LOCAL_SRC_FILES += compat/wchar/wcstok.c
LOCAL_SRC_FILES += compat/wchar/wcswidth.c
LOCAL_SRC_FILES += compat/wchar/wcsxfrm.c
LOCAL_SRC_FILES += compat/wchar/wmemchr.c
LOCAL_SRC_FILES += compat/wchar/wmemcmp.c
LOCAL_SRC_FILES += compat/wchar/wmemcpy.c
LOCAL_SRC_FILES += compat/wchar/wmemmove.c
LOCAL_SRC_FILES += compat/wchar/wmemset.c
endif

LOCAL_C_INCLUDES := $(VLC_SRC_DIR)/include

ARCH=$(APP_ABI)

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
	-ldvbpsi -lmatroska -lebml -ltag \
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
	-ldsm -ltasn1 \
	-lmad \
	-lzvbi \
	-lssh2 \
	-lmodplug \
	-lupnp -lthreadutil -lixml \
	-larchive \
	$(EXTRA_LDFLAGS)

$(TARGET_OUT)/$(LOCAL_MODULE).so: $(ANDROID_PRIVATE_LIBS)
include $(BUILD_SHARED_LIBRARY)

################
# PRIVATE LIBS #
################

ANDROID_PRIVATE_LIBS=$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libstagefright.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libmedia.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libutils.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libcutils.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libbinder.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libui.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libhardware.so

$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/%.so: $(ANDROID_PRIVATE_LIBDIR)/%.c
	mkdir -p $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)
	$(GEN)$(TARGET_TUPLE)-gcc $< -shared -o $@ --sysroot=$(SYSROOT)

$(ANDROID_PRIVATE_LIBDIR)/%.c: $(ANDROID_PRIVATE_LIBDIR)/%.symbols
	$(VERBOSE)rm -f $@ && touch $@
	$(GEN)for s in `cat $<`; do echo "void $$s() {}" >> $@; done

###########
# libiOMX #
###########

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

LIBIOMX_INCLUDES_21 := $(LIBIOMX_INCLUDES_COMMON) \
	$(ANDROID_SYS_HEADERS)/21/frameworks/native/include \
	$(ANDROID_SYS_HEADERS)/21/frameworks/av/include \
	$(ANDROID_SYS_HEADERS)/21/system/core/include \
	$(ANDROID_SYS_HEADERS)/21/hardware/libhardware/include

define build_iomx
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_SRC_FILES  := $(VLC_SRC_DIR)/modules/codec/omxil/iomx.cpp
LOCAL_C_INCLUDES := $(LIBIOMX_INCLUDES_$(2))
LOCAL_LDLIBS     := -L$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE) -lgcc -lstagefright -lmedia -lutils -lbinder -llog -lcutils -lui
LOCAL_CFLAGS     := -Wno-psabi -DANDROID_API=$(2)
$(TARGET_OUT)/$(1).so: $(ANDROID_PRIVATE_LIBS)
include $(BUILD_SHARED_LIBRARY)
endef

# call build_iomx for each libiomx-* in LIBIOMX_LIBS
$(foreach IOMX_MODULE, $(LIBIOMX_LIBS), \
	$(eval $(call build_iomx,$(IOMX_MODULE),$(subst libiomx.,,$(IOMX_MODULE)))))


#######
# ANW #
#######
LIBANW_SRC_FILES_COMMON += $(VLC_SRC_DIR)/modules/video_output/android/nativewindowpriv.c

define build_anw
include $(CLEAR_VARS)
LOCAL_MODULE := $(1)
LOCAL_SRC_FILES  := $(LIBANW_SRC_FILES_COMMON)
LOCAL_C_INCLUDES := $(LIBIOMX_INCLUDES_$(2))
LOCAL_LDLIBS     := -L$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE) -llog -lhardware
LOCAL_CFLAGS     := $(LIBIOMX_CFLAGS_COMMON) -DANDROID_API=$(2)
$(TARGET_OUT)/$(1).so: $(ANDROID_PRIVATE_LIBS)
include $(BUILD_SHARED_LIBRARY)
endef

$(foreach ANW_MODULE,$(LIBANW_LIBS), \
    $(eval $(call build_anw,$(ANW_MODULE),$(subst libanw.,,$(ANW_MODULE)))))
