#! /bin/sh

GIT=git

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK and ANDROID_SDK before starting";
   exit 1;
fi;

echo "Fetching Android system headers"
# Using CyanogenMod headers instead of AOSP, since CyanogenMod
# has commit 1563f4aca88d354c502dba056d173cefc7c2ea7f,
# "Stagefright: Memcpy optimization on output port." (available
# upstream at https://www.codeaurora.org/gitweb/quic/la/?p=platform/frameworks/base.git;a=commit;h=052368f194c9fc180b9b0335b60114a2f1fb88d8),
# which adds some vtable entries needed on newer qualcomm devices.
$GIT clone -b gingerbread --depth=1 git://github.com/CyanogenMod/android_frameworks_base.git android-headers/frameworks/base
$GIT clone -b gingerbread --depth=1 git://github.com/CyanogenMod/android_system_core.git android-headers/system/core
export ANDROID_SYS_HEADERS=${PWD}/android-headers

echo "Fetching Android libraries for linking"
# Libraries from any froyo/gingerbread device/emulator should work
# fine, since the symbols used should be available on most of them.
if [ ! -f "update-cm-7.0.3-N1-signed.zip" ]; then
    wget http://download.cyanogenmod.com/get/update-cm-7.0.3-N1-signed.zip
    unzip update-cm-7.0.3-N1-signed.zip system/lib/*
    mv system/lib android-libs
    rmdir system
fi
export ANDROID_LIBS=${PWD}/android-libs

echo "Cloning and updating VLC"
$GIT clone git://git.videolan.org/vlc.git

echo "Applying the patches"
cd vlc
$GIT am ../patches/*.patch || $GIT am --abort

echo "Building the contribs"
cd extras/contrib && ./bootstrap -t arm-eabi -d android && make

cd ../.. && mkdir -p android && cd android

if test ! -s "../configure"
then
    echo "Bootstraping"
    ../bootstrap
fi;

echo "Configuring"
sh ../extras/package/android/configure.sh

echo "Building"
make

echo "Building Android"
cd ../../
make distclean
VLC_BUILD_DIR=vlc/android make
