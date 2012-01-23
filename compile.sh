#! /bin/sh

# Read the Android HOWTO and setup all that stuff correctly.
# Get the Android SDK Platform 2.1, 2.2 and 2.3 API : version 7, 8 and (9 or 10)
# or modify numbers in configure.sh and vlc-android/default.properties.
# Create an AVD with this platform.

# XXX : important!
# If you plan to use the emulator, you need a build without neon
# export NO_NEON=1
# make sure it is set for both the contribs bootstrap next and the configure.sh later.

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK and ANDROID_SDK before starting."
   echo "They must point to your NDK and SDK directories."
   exit 1
fi

# 1/ libvlc, libvlccore and its plugins

if [ ! -d "vlc" ]; then
	echo "VLC source not found, cloning"
        git clone git://git.videolan.org/vlc/vlc-1.2.git vlc
else
	echo "VLC source found, pulling from remote master"
	pushd vlc > /dev/null
	git pull origin master
	popd > /dev/null
fi

echo "Applying the patches"
cd vlc
git am ../patches/*.patch || git am --abort

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
    --disable-sidplay2 \
    --disable-live555

make fetch
make

cd ../.. && mkdir -p android && cd android

if test ! -s "../configure"
then
    echo "Bootstraping"
    ../bootstrap
fi

echo "Configuring"
sh ../extras/package/android/configure.sh

echo "Building"
make


# 2/ VLC android UI and specific code

echo "Building Android"
cd ../../

# Using CyanogenMod headers instead of AOSP, since CyanogenMod
# has commit 1563f4aca88d354c502dba056d173cefc7c2ea7f,
# "Stagefright: Memcpy optimization on output port." (available
# upstream at https://www.codeaurora.org/gitweb/quic/la/?p=platform/frameworks/base.git;a=commit;h=052368f194c9fc180b9b0335b60114a2f1fb88d8),
# which adds some vtable entries needed on newer qualcomm devices.

cyanogen_headers() {
    dir=android-headers-$2/$1
    if [ ! -d $dir ]; then
        echo "Fetching $1 for $2"
        git clone -b $2 --depth=1 git://github.com/CyanogenMod/android_`echo $1|tr / _`.git $dir
    else
        echo "Updating $1 for $2"
        pushd $dir > /dev/null
        git pull origin $2
        popd > /dev/null
    fi
}

cyanogen_headers frameworks/base gingerbread
cyanogen_headers system/core gingerbread
cyanogen_headers frameworks/base ics
cyanogen_headers system/core ics
cyanogen_headers hardware/libhardware ics

export ANDROID_SYS_HEADERS_GINGERBREAD=${PWD}/android-headers-gingerbread
export ANDROID_SYS_HEADERS_ICS=${PWD}/android-headers-ics

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
export VLC_BUILD_DIR=vlc/android

make distclean
make
