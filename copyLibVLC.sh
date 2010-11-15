#!/bin/sh

if [ "x$1" = "x" ]; then
    echo "Please give your VLC build directory."
    exit 1
fi

VLC_BUILD=$1

if [  ! -d "./vlc-android/libs/armeabi" ]; then
    mkdir -p ./vlc-android/libs/armeabi/
fi

cp -v $VLC_BUILD/src/.libs/libvlccore.so ./vlc-android/libs/armeabi/
cp -v $VLC_BUILD/src/.libs/libvlc.so ./vlc-android/libs/armeabi/

