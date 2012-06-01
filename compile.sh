#! /bin/sh

# Read the Android HOWTO and setup all that stuff correctly.
# Get the Android SDK Platform 2.1, 2.2 and 2.3 API : version 7, 8 and (9 or 10)
# or modify numbers in configure.sh and vlc-android/default.properties.
# Create an AVD with this platform.

set -e

# XXX : important!
cat << EOF
If you plan to use a device without NEON (e.g. the emulator), you need a build without NEON:
$ export NO_NEON=1
Make sure it is set throughout the entire process.

The script will attempt to automatically detect if you have NDK v7, but you can override this.
If you do not have NDK v7 or later:
export NO_NDK_V7=1
or if you are sure you have NDK v7:
export NO_NDK_V7=0

EOF

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK and ANDROID_SDK before starting."
   echo "They must point to your NDK and SDK directories."
   exit 1
fi

if [ -z "$NO_NDK_V7" ]; then
    # try to detect NDK version
    REL=$(grep -o '^r[0-9]*' $ANDROID_NDK/RELEASE.TXT 2>/dev/null|cut -b2-)
    if [ -z $REL ]; then
        export NO_NDK_V7=1
    fi
fi

# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
export PATH=${ANDROID_NDK}/toolchains/arm-linux-androideabi-4.4.3/prebuilt/linux-x86/bin:${PATH}

# 1/ libvlc, libvlccore and its plugins
TESTED_HASH=defdb5a9e1
if [ ! -d "vlc" ]; then
    echo "VLC source not found, cloning"
    git clone git://git.videolan.org/vlc.git vlc
    cd vlc
    git checkout -B android ${TESTED_HASH}
    echo "Applying the patches"
    git am ../patches/*.patch
else
    echo "VLC source found"
    cd vlc
    if ! git cat-file -e ${TESTED_HASH}; then
        cat << EOF
***
*** Error: Your vlc checkout does not contain the latest tested commit ***
***

Please update your source with something like:

cd vlc
git reset --hard origin
git pull origin master
git checkout -B android ${TESTED_HASH}
git am ../patches/*

*** : This will delete any changes you made to the current branch ***

EOF
        exit 1
    fi
fi

echo "Building the contribs"
mkdir -p contrib/android
cd contrib/android
../bootstrap --host=arm-linux-androideabi --disable-disc --disable-sout --enable-small \
    --disable-sdl \
    --disable-SDL_image \
    --disable-fontconfig \
    --disable-zvbi \
    --disable-kate \
    --disable-caca \
    --disable-gettext \
    --disable-mpcdec \
    --disable-upnp \
    --disable-gme \
    --disable-tremor \
    --disable-vorbis \
    --disable-sidplay2 \
    --disable-samplerate \
    --enable-iconv

# TODO: mpeg2, theora

if test -z "${NO_NEON}" -o -n "${TEGRA2}"; then
    # assumes armv7-a
    echo "EXTRA_CFLAGS += -mthumb" >> config.mak
    echo "NOTHUMB := -marm" >> config.mak
fi

make fetch
make

cd ../.. && mkdir -p android && cd android

if test ! -s "../configure" ; then
    echo "Bootstraping"
    ../bootstrap
fi

echo "Configuring"
../../configure.sh

echo "Building"
make


# 2/ VLC android UI and specific code

echo "Building Android"
cd ../../

export ANDROID_SYS_HEADERS_GINGERBREAD=${PWD}/android-headers-gingerbread
export ANDROID_SYS_HEADERS_HC=${PWD}/android-headers-hc
export ANDROID_SYS_HEADERS_ICS=${PWD}/android-headers-ics

export ANDROID_LIBS=${PWD}/android-libs
export VLC_BUILD_DIR=vlc/android

make distclean
make
