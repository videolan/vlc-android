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

# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
export PATH=${ANDROID_NDK}/toolchains/arm-linux-androideabi-4.4.3/prebuilt/linux-x86/bin:${PATH}

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
    --disable-sidplay2

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

export ANDROID_SYS_HEADERS_GINGERBREAD=${PWD}/android-headers-gingerbread
export ANDROID_SYS_HEADERS_ICS=${PWD}/android-headers-ics

export ANDROID_LIBS=${PWD}/android-libs
export VLC_BUILD_DIR=vlc/android

make distclean
make
