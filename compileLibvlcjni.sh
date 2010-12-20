#!/bin/sh

if [ -z "$ANDROID_NDK" ]; then
    echo "Set your ANDROID_NDK environment variable"
    exit 1
fi


cd libvlcjni
$ANDROID_NDK/ndk-build

if [  ! -d "../vlc-android/libs/armeabi" ]; then
    mkdir -p ../vlc-android/libs/armeabi/
fi

cp -v libs/armeabi/libvlcjni.so ../vlc-android/libs/armeabi/

