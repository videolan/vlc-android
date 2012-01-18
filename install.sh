#! /bin/sh

GIT=git

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK and ANDROID_SDK before starting";
   exit 1;
fi;

# Using CyanogenMod headers instead of AOSP, since CyanogenMod
# has commit 1563f4aca88d354c502dba056d173cefc7c2ea7f,
# "Stagefright: Memcpy optimization on output port." (available
# upstream at https://www.codeaurora.org/gitweb/quic/la/?p=platform/frameworks/base.git;a=commit;h=052368f194c9fc180b9b0335b60114a2f1fb88d8),
# which adds some vtable entries needed on newer qualcomm devices.
if [ ! -d "android-headers/frameworks/base" ]; then
	echo "Fetching Android system headers (1/2)"
	$GIT clone -b gingerbread --depth=1 git://github.com/CyanogenMod/android_frameworks_base.git android-headers/frameworks/base
else
	echo "Updating Android system headers (1/2)"
	pushd android-headers/frameworks/base > /dev/null
	$GIT pull origin gingerbread
	popd > /dev/null
fi
if [ ! -d "android-headers/system/core" ]; then
	echo "Fetching Android system headers (2/2)"
	$GIT clone -b gingerbread --depth=1 git://github.com/CyanogenMod/android_system_core.git android-headers/system/core
else
	echo "Updating Android system headers (2/2)"
	pushd android-headers/system/core > /dev/null
	$GIT pull origin gingerbread
	popd > /dev/null
fi
export ANDROID_SYS_HEADERS=${PWD}/android-headers

echo "Fetching Android libraries for linking"
# Libraries from any froyo/gingerbread device/emulator should work
# fine, since the symbols used should be available on most of them.
if [ ! -f "update-cm-7.1.0.1-NS-signed.zip" ]; then
    curl -O http://mirror.sea.tdrevolution.net/cm/stable/gingerbread/update-cm-7.1.0.1-NS-signed.zip
    unzip update-cm-7.1.0.1-NS-signed.zip system/lib/\*
    mv system/lib android-libs
    rmdir system
fi
export ANDROID_LIBS=${PWD}/android-libs

if [ ! -d "vlc" ]; then
	echo "VLC source not found, cloning"
        $GIT clone git://git.videolan.org/vlc/vlc-1.2.git vlc
else
	echo "VLC source found, pulling from remote master"
	pushd vlc > /dev/null
	$GIT pull origin master
	popd > /dev/null
fi

echo "Applying the patches"
cd vlc
$GIT am ../patches/*.patch || $GIT am --abort

echo "Building the contribs"
mkdir contrib/android; cd contrib/android
../bootstrap --host=arm-linux-androideabi --disable-disc --disable-sout \
    --disable-sdl \
    --disable-SDL_image \
    --disable-fontconfig \
    --disable-ass \
    --disable-freetyp2 \
    --disable-fribidi \
    --disable-zvbi \
    --disable-kate \
    --disable-caca \
    --disable-gettext \
    --disable-mpcdec \
    --disable-sidplay2

make fetch
make

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
