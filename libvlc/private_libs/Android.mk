LOCAL_PATH := $(call my-dir)
ANDROID_PRIVATE_LIBDIR := $(LOCAL_PATH)/../../android-libs
include $(CLEAR_VARS)

ANDROID_PRIVATE_LIBS=$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libstagefright.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libmedia.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libutils.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libcutils.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libbinder.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libui.so \
					 $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/libhardware.so

$(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)/%.so: $(ANDROID_PRIVATE_LIBDIR)/%.c
	mkdir -p $(ANDROID_PRIVATE_LIBDIR)/$(TARGET_TUPLE)
	$(GEN)clang $< -shared -o $@

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
