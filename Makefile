# Sources and objects

export ANDROID_HOME=$(ANDROID_SDK)

ARCH = $(ANDROID_ABI)

SRC=libvlc
APP_SRC=vlc-android
JAVA_SOURCES=$(shell find $(APP_SRC)/src/org/videolan -name "*.java" -o -name "*.aidl")
JAVA_SOURCES+=$(shell find $(SRC)/src/org/videolan -name "*.java" -o -name "*.aidl")
JAVA_SOURCES+=$(shell find $(APP_SRC)/res -name "*.xml" -o -name "*.png") 
JNI_SOURCES=$(SRC)/jni/*.c $(SRC)/jni/*.h
LIBVLC_LIBS = libvlcjni

ifneq ($(HAVE_64),1)
# Can't link with 32bits symbols.
# Not a problem since MediaCodec should work on 64bits devices (android-21)
LIBVLC_LIBS += libiomx.14 libiomx.13 libiomx.10
endif

# The following iomx libs are used for DEBUG only.
# (after android Jelly Bean, we prefer to use MediaCodec instead of iomx)
#LIBVLC_LIBS += libiomx.19 libiomx.18

LIBVLC_LIBS += libanw.10 libanw.13 libanw.14 libanw.18 libanw.21

LIBVLCJNI= $(addprefix $(SRC)/obj/local/$(ARCH)/,$(addsuffix .so,$(LIBVLC_LIBS)))

PRIVATE_LIBDIR=android-libs
PRIVATE_LIBS=$(PRIVATE_LIBDIR)/libstagefright.so $(PRIVATE_LIBDIR)/libmedia.so $(PRIVATE_LIBDIR)/libutils.so $(PRIVATE_LIBDIR)/libcutils.so $(PRIVATE_LIBDIR)/libbinder.so $(PRIVATE_LIBDIR)/libui.so $(PRIVATE_LIBDIR)/libhardware.so

ANT_OPTS += -v
VERBOSE =
GEN =

ifeq ($(RELEASE),1)
ANT_TARGET = release
VLC_APK=$(APP_SRC)/bin/VLC-release-unsigned.apk
NDK_DEBUG=0
else
ANT_TARGET = debug
VLC_APK=$(APP_SRC)/bin/VLC-debug.apk
NDK_DEBUG=1
endif

define build_apk
	@echo
	@echo "=== Building $(VLC_APK) for $(ARCH) ==="
	@echo
	date +"%Y-%m-%d" > $(APP_SRC)/assets/builddate.txt
	echo `id -u -n`@`hostname` > $(APP_SRC)/assets/builder.txt
	git rev-parse --short HEAD > $(APP_SRC)/assets/revision.txt
	./gen-env.sh $(APP_SRC)
	$(VERBOSE)cd $(APP_SRC) && ant $(ANT_OPTS) $(ANT_TARGET)
endef

$(VLC_APK): $(LIBVLCJNI) $(JAVA_SOURCES)
	$(call build_apk)

$(PRIVATE_LIBDIR)/%.so: $(PRIVATE_LIBDIR)/%.c
	$(GEN)$(TARGET_TUPLE)-gcc $< -shared -o $@ --sysroot=$(SYSROOT)

$(PRIVATE_LIBDIR)/%.c: $(PRIVATE_LIBDIR)/%.symbols
	$(VERBOSE)rm -f $@
	$(GEN)for s in `cat $<`; do echo "void $$s() {}" >> $@; done

apk:
	$(call build_apk)

apkclean:
	rm -f $(VLC_APK)

lightclean:
	cd $(SRC) && rm -rf libs obj
	cd $(APP_SRC) && rm -rf bin $(VLC_APK)
	rm -f $(PRIVATE_LIBDIR)/*.so $(PRIVATE_LIBDIR)/*.c

clean: lightclean
	rm -rf $(APP_SRC)/gen java-libs/*/gen java-libs/*/bin .sdk vlc-sdk/ vlc-sdk.7z

jniclean: lightclean
	rm -rf $(LIBVLCJNI) $(APP_SRC)/libs/

distclean: clean jniclean

install: $(VLC_APK)
	@echo "=== Installing VLC on device ==="
	adb wait-for-device
	adb install -r $(VLC_APK)

uninstall:
	adb wait-for-device
	adb uninstall org.videolan.vlc

run:
	@echo "=== Running VLC on device ==="
	adb wait-for-device
ifeq ($(URL),)
	adb shell am start -n org.videolan.vlc/org.videolan.vlc.gui.MainActivity
else
	adb shell am start -n org.videolan.vlc/org.videolan.vlc.gui.video.VideoPlayerActivity $(URL)
endif

build-and-run: install run

apkclean-run: apkclean build-and-run
	adb logcat -c

distclean-run: distclean build-and-run
	adb logcat -c

vlc-sdk.7z: .sdk
	7z a $@ vlc-sdk/

.sdk:
	mkdir -p vlc-sdk/libs
	cd libvlc; cp -r libs/* ../vlc-sdk/libs
	mkdir -p vlc-sdk/src/org/videolan
	cp -r libvlc/src/org/videolan/libvlc vlc-sdk/src/org/videolan
	touch $@

.PHONY: lightclean clean jniclean distclean distclean-run apkclean apkclean-run install run build-and-run
