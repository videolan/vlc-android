#!/bin/sh

if [ "x$1" = "x" ]; then
    echo "Give the ndk path"
    exit 1
fi

ANDROID_NDK=$1

cd libvlcjni
$ANDROID_NDK/ndk-build

if [  ! -d "../vlc-android/libs/armeabi" ]; then
    mkdir -p ../vlc-android/libs/armeabi/
fi

cp -v libs/armeabi/libvlcjni.so ../vlc-android/libs/armeabi/

