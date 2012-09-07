#!/bin/sh

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_ABI" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path.\n"
    echo "ANDROID_ABI should match your ABI: armeabi-v7a, armeabi or ..."
    exit 1
fi

# Must use android-9 here. Any replacement functions needed are in the vlc-android/jni
# folder.
ANDROID_API=android-9

VLC_SOURCEDIR=..

CFLAGS="-g -O2 -fstrict-aliasing -funsafe-math-optimizations"
if [ -n "$HAVE_ARM" ]; then
    CFLAGS="${CFLAGS} -mlong-calls"
fi

LDFLAGS="-Wl,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined"

if [ -n "$HAVE_ARM" ]; then
    if [ -z "$NO_NEON" ]; then
        NO_NEON=0
    fi

    if [ $NO_NEON -gt 0 ]; then
        EXTRA_PARAMS=" --disable-neon"
    else
        EXTRA_PARAMS=" --enable-neon"
        LDFLAGS="$LDFLAGS -Wl,--fix-cortex-a8"
    fi
fi

CPPFLAGS="-I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/include -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/libs/${ANDROID_ABI}/include"
LDFLAGS="$LDFLAGS -L${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++${CXXSTL}/libs/${ANDROID_ABI}"

SYSROOT=$ANDROID_NDK/platforms/$ANDROID_API/arch-$PLATFORM_SHORT_ARCH
ANDROID_BIN=$ANDROID_NDK/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/`uname|tr A-Z a-z`-x86/bin/
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
sh $VLC_SOURCEDIR/configure --host=$TARGET_TUPLE --build=x86_64-unknown-linux $EXTRA_PARAMS \
                --enable-live555 --enable-realrtsp \
                --enable-avformat \
                --enable-swscale \
                --enable-avcodec \
                --enable-opus \
                --enable-opensles \
                --enable-audiotrack \
                --enable-android-surface \
                --enable-mkv \
                --enable-taglib \
                --enable-dvbpsi \
                --enable-iomx \
                --disable-vlc --disable-shared \
                --disable-vlm \
                --disable-dbus \
                --disable-lua \
                --disable-vcd \
                --disable-v4l2 \
                --disable-gnomevfs \
                --disable-dvdread \
                --disable-dvdnav \
                --disable-bluray \
                --disable-linsys \
                --disable-decklink \
                --disable-libva \
                --disable-dv1394 \
                --disable-mod \
                --disable-sid \
                --disable-gme \
                --disable-tremor \
                --disable-mad \
                --disable-dca \
                --disable-sdl-image \
                --disable-zvbi \
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
                --disable-sqlite \
                --disable-udev \
                --enable-libxml2 \
                --disable-caca \
                --disable-glx \
                --disable-egl \
                --disable-goom \
                --disable-projectm \
                --disable-sout \
                --disable-vorbis \
                --disable-faad \
                --disable-x264 \
                --disable-schroedinger --disable-dirac \
                $*
