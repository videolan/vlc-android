all: configure compile

libvlcjni/jni/Android.mk:
	./configure_libvlcjni.sh ../../../build ../../contrib/build

configure: libvlcjni/jni/Android.mk

libvlcjni/libs/armeabi/libvlcjni.so:
	./compileLibvlcjni.sh

compile: libvlcjni/libs/armeabi/libvlcjni.so

clean:
	rm -rf libvlcjni/libs/*
	rm -rf libvlcjni/objs/*

distclean: clean
	rm -rf libvlcjni/jni/Android.mk

