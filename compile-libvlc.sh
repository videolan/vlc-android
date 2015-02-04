#!/bin/sh

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
    echo "Please set ANDROID_ABI to your architecture: armeabi-v7a, armeabi, arm64-v8a, x86, x86_64 or mips."
    exit 1
fi

# ANDROID_API must be previously set by compile.sh or env.sh
if [ -z "$ANDROID_API" ];then
    echo "ANDROID_API not set, call ./compile.sh first"
    exit 1
fi

VLC_SOURCEDIR=..

CFLAGS="-g -O2 -fstrict-aliasing -funsafe-math-optimizations"
if [ -n "$HAVE_ARM" -a ! -n "$HAVE_64" ]; then
    CFLAGS="${CFLAGS} -mlong-calls"
fi

LDFLAGS="-Wl,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined"

if [ -n "$HAVE_ARM" ]; then
    if [ ${ANDROID_ABI} = "armeabi-v7a" ]; then
        EXTRA_PARAMS=" --enable-neon"
        LDFLAGS="$LDFLAGS -Wl,--fix-cortex-a8"
    fi
fi

CPPFLAGS="-I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/include -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/libs/${ANDROID_ABI}/include"
LDFLAGS="$LDFLAGS -L${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/libs/${ANDROID_ABI}"

SYSROOT=$ANDROID_NDK/platforms/$ANDROID_API/arch-$PLATFORM_SHORT_ARCH
ANDROID_BIN=`echo $ANDROID_NDK/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/\`uname|tr A-Z a-z\`-*/bin/`
CROSS_COMPILE=${ANDROID_BIN}/${TARGET_TUPLE}-

CPPFLAGS="$CPPFLAGS" \
CFLAGS="$CFLAGS ${VLC_EXTRA_CFLAGS}" \
CXXFLAGS="$CFLAGS" \
LDFLAGS="$LDFLAGS" \
CC="${CROSS_COMPILE}gcc --sysroot=${SYSROOT}" \
CXX="${CROSS_COMPILE}g++ --sysroot=${SYSROOT}" \
NM="${CROSS_COMPILE}nm" \
STRIP="${CROSS_COMPILE}strip" \
RANLIB="${CROSS_COMPILE}ranlib" \
AR="${CROSS_COMPILE}ar" \
PKG_CONFIG_LIBDIR=$VLC_SOURCEDIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
sh $VLC_SOURCEDIR/configure --host=$TARGET_TUPLE --build=x86_64-unknown-linux $EXTRA_PARAMS \
                --disable-nls \
                --enable-live555 --enable-realrtsp \
                --enable-avformat \
                --enable-swscale \
                --enable-avcodec \
                --enable-opus \
                --enable-opensles \
                --enable-android-surface \
                --enable-mkv \
                --enable-taglib \
                --enable-dvbpsi \
                --disable-vlc --disable-shared \
                --disable-update-check \
                --disable-vlm \
                --disable-dbus \
                --disable-lua \
                --disable-vcd \
                --disable-v4l2 \
                --disable-gnomevfs \
                --enable-dvdread \
                --enable-dvdnav \
                --disable-bluray \
                --disable-linsys \
                --disable-decklink \
                --disable-libva \
                --disable-dv1394 \
                --disable-mod \
                --disable-sid \
                --disable-gme \
                --disable-tremor \
                --enable-mad \
                --disable-dca \
                --disable-sdl-image \
                --enable-zvbi \
                --disable-fluidsynth \
                --disable-jack \
                --disable-pulse \
                --disable-alsa \
                --disable-samplerate \
                --disable-sdl \
                --disable-xcb \
                --disable-atmo \
                --disable-qt \
                --disable-skins2 \
                --disable-mtp \
                --disable-notify \
                --enable-libass \
                --disable-svg \
                --disable-udev \
                --enable-libxml2 \
                --disable-caca \
                --disable-glx \
                --enable-egl \
                --enable-gles2 \
                --disable-goom \
                --disable-projectm \
                --disable-sout \
                --enable-vorbis \
                --disable-faad \
                --disable-x264 \
                --disable-schroedinger --disable-dirac \
                $*

# ANDROID NDK FIXUP (BLAME GOOGLE)
config_undef ()
{
    unamestr=`uname`
    if [[ "$unamestr" == 'Darwin' ]]; then
        previous_change=`stat -f "%Sm" -t "%y%m%d%H%M.%S" config.h`
        sed -i '' 's,#define '$1' 1,/\* #undef '$1' \*/,' config.h
        touch -t "$previous_change" config.h
    else
        previous_change=`stat -c "%y" config.h`
        sed -i 's,#define '$1' 1,/\* #undef '$1' \*/,' config.h
        # don't change modified date in order to don't trigger a full build
        touch -d "$previous_change" config.h
    fi
}

# if config dependencies change, ./config.status
# is run and overwrite previously hacked config.h. So call make config.h here
# and hack config.h after.

make $MAKEFLAGS config.h

if [ ${ANDROID_ABI} = "x86" -a ${ANDROID_API} != "android-21" ] ; then
    # NDK x86 libm.so has nanf symbol but no nanf definition, we don't known if
    # intel devices has nanf. Assume they don't have it.
    config_undef HAVE_NANF
fi
if [ ${ANDROID_API} = "android-21" ] ; then
    # android-21 has empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    config_undef HAVE_SYS_SHM_H
fi
# END OF ANDROID NDK FIXUP

