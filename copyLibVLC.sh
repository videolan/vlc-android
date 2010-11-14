#!/bin/sh

if [ "x$1" = "x" ]; then
    echo "Please give your VLC build directory."
    exit 1
fi

VLC_BUILD=$1

if [  ! -d "./vlc-android/libs/" ]
    mkdir ./vlc-android/libs/
fi

cp -v $VLC_BUILD/src/.libs/libvlccore.so ./vlc-android/libs/
cp -v $VLC_BUILD/src/.libs/libvlc.so ./vlc-android/libs/

