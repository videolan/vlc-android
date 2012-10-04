#! /bin/sh

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and vlc-android/default.properties.

set -e

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" -o -z "$ANDROID_ABI" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK and ANDROID_ABI before starting."
   echo "They must point to your NDK and SDK directories.\n"
   echo "ANDROID_ABI should match your ABI: armeabi-v7a, armeabi or ..."
   exit 1
fi
# try to detect NDK version
REL=$(grep -o '^r[0-9]*.*' $ANDROID_NDK/RELEASE.TXT 2>/dev/null|cut -b2-)
case "$REL" in
    7|8)
        # NDK > v7, only gcc 4.4.3 available
        GCCVER=4.4.3
        CXXSTL=""
    ;;
    8?)
        # NDK >= v8b, both 4.4.3 and 4.6 available,
        # we use 4.4.3 because there is a regression in gcc 4.6, DECL_USER_ALIGN is not handled properly
        GCCVER=4.4.3
        CXXSTL="/4.4.3"
    ;;
    *)
        echo "You need the NDKv7 or later"
        exit 1
    ;;
esac

export GCCVER
export CXXSTL

# XXX : important!
cat << EOF
For an ARMv7-A device without NEON, you need a build without NEON:
$ export NO_NEON=1
For an ARMv6 device without FPU, you need a build without FPU:
$ export NO_FPU=1
For an ARMv5 device or the Android emulator, you need an ARMv5 build:
$ export NO_ARMV6=1

If you plan to use a release build, run 'compile.sh release'
EOF

# Set up ABI variables
if [ ${ANDROID_ABI} = "x86" ] ; then
    TARGET_TUPLE="i686-android-linux"
    PATH_HOST="x86"
    HAVE_X86=1
    PLATFORM_SHORT_ARCH="x86"
elif [ ${ANDROID_ABI} = "mips" ] ; then
    TARGET_TUPLE="mipsel-linux-android"
    PATH_HOST=$TARGET_TUPLE
    HAVE_MIPS=1
    PLATFORM_SHORT_ARCH="mips"
else
    TARGET_TUPLE="arm-linux-androideabi"
    PATH_HOST=$TARGET_TUPLE
    HAVE_ARM=1
    PLATFORM_SHORT_ARCH="arm"
fi

if [ ${ANDROID_ABI} = "x86" ] ; then
    # x86 toolchain location changes in NDK r8b
    case "$REL" in
        8?)
            TARGET_TUPLE="i686-linux-android"
        ;;
        *)
            TARGET_TUPLE="i686-android-linux"
        ;;
    esac
fi

export TARGET_TUPLE
export PATH_HOST
export HAVE_ARM
export HAVE_X86
export HAVE_MIPS
export PLATFORM_SHORT_ARCH

# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
export PATH=${ANDROID_NDK}/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/`uname|tr A-Z a-z`-x86/bin:${PATH}

# 1/ libvlc, libvlccore and its plugins
TESTED_HASH=45adae3485
if [ ! -d "vlc" ]; then
    echo "VLC source not found, cloning"
    git clone git://git.videolan.org/vlc.git vlc
    cd vlc
    git checkout -B android ${TESTED_HASH}
    echo "Applying the patches"
    git am -3 ../patches/*.patch
    if [ $? -ne 0 ]; then
        git am --abort
        echo "Applying the patches failed, aborting git-am"
        exit 1
    fi
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
git am -3 ../patches/*

*** : This will delete any changes you made to the current branch ***

EOF
        exit 1
    fi
fi

if [ ${ANDROID_ABI} = "armeabi-v7a" ] ; then
    if test -z "${NO_NEON}" ; then
        EXTRA_CFLAGS="-mfpu=neon -mcpu=cortex-a8"
    else
        EXTRA_CFLAGS="-mfpu=vfpv3-d16 -mcpu=cortex-a9"
    fi
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -mthumb -mfloat-abi=softfp"
elif [ ${ANDROID_ABI} = "armeabi" ] ; then
    export NO_NEON=1
    if [ -n "${NO_ARMV6}" ]; then
        EXTRA_CFLAGS="-march=armv5te -mtune=arm9tdmi -msoft-float"
    else
        if [ -n "${NO_FPU}" ]; then
            EXTRA_CFLAGS="-march=armv6j -mtune=arm1136j-s -msoft-float"
        else
            EXTRA_CFLAGS="-mfpu=vfp -mcpu=arm1136jf-s -mfloat-abi=softfp"
        fi
    fi
elif [ ${ANDROID_ABI} = "x86" ] ; then
    EXTRA_CFLAGS="-march=pentium -ffunction-sections -funwind-tables -frtti -fno-exceptions"
else
    echo "Unknown ABI. Die, die, die!"
    exit 2
fi

EXTRA_CFLAGS="${EXTRA_CFLAGS} -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/include"
EXTRA_CFLAGS="${EXTRA_CFLAGS} -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/libs/${ANDROID_ABI}/include"

echo "Building the contribs"
mkdir -p contrib/android
cd contrib/android
../bootstrap --host=${TARGET_TUPLE} --disable-disc --disable-sout --enable-small \
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
    --disable-faad2 \
    --disable-harfbuzz \
    --enable-iconv

# TODO: mpeg2, theora

# Some libraries have arm assembly which won't build in thumb mode
# We append -marm to the CFLAGS of these libs to disable thumb mode
[ ${ANDROID_ABI} = "armeabi-v7a" ] && echo "NOTHUMB := -marm" >> config.mak

# Release or not?
if [ $# -ne 0 ] && [ "$1" == "release" ]; then
    OPTS=""
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -DNDEBUG "
    RELEASEFLAG="RELEASE=1"
else
    OPTS="--enable-debug"
fi

MAKEFLAGS=
if which nproc >/dev/null
then
MAKEFLAGS=-j`nproc`
fi

echo "EXTRA_CFLAGS= -g ${EXTRA_CFLAGS}" >> config.mak
export VLC_EXTRA_CFLAGS="${EXTRA_CFLAGS}"

make fetch
make $MAKEFLAGS

cd ../.. && mkdir -p android && cd android

if test ! -s "../configure" ; then
    echo "Bootstraping"
    ../bootstrap
fi

echo "Configuring"
../../configure.sh $OPTS

echo "Building"
make $MAKEFLAGS


# 2/ VLC android UI and specific code

echo "Building Android"
cd ../../

export ANDROID_SYS_HEADERS_GINGERBREAD=${PWD}/android-headers-gingerbread
export ANDROID_SYS_HEADERS_HC=${PWD}/android-headers-hc
export ANDROID_SYS_HEADERS_ICS=${PWD}/android-headers-ics

export ANDROID_LIBS=${PWD}/android-libs
export VLC_BUILD_DIR=vlc/android

make distclean
make TARGET_TUPLE=$TARGET_TUPLE PLATFORM_SHORT_ARCH=$PLATFORM_SHORT_ARCH CXXSTL=$CXXSTL $RELEASEFLAG
