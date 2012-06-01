#!/bin/sh

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

ANDROID_API=android-9

VLC_SOURCEDIR=..

CFLAGS="-g -O2 -mlong-calls -fstrict-aliasing -mfloat-abi=softfp -funsafe-math-optimizations"
LDFLAGS="-Wl,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined"

if [ -z "$NO_NEON" ]; then
    CXX_TARGET="armeabi-v7a"
    CFLAGS="$CFLAGS -mfpu=neon -mcpu=cortex-a8 -mthumb"
    LDFLAGS="$LDFLAGS -Wl,--fix-cortex-a8"
    EXTRA_PARAMS=" --enable-neon"
elif [ -n "$TEGRA2" ]; then
    CXX_TARGET="armeabi-v7a"
    CFLAGS="$CFLAGS -mfpu=vfpv3-d16 -mcpu=cortex-a9 -mthumb"
    EXTRA_PARAMS=" --disable-neon"
else
    CXX_TARGET="armeabi"
    CFLAGS="$CFLAGS -mcpu=arm1136jf-s -mfpu=vfp"
    EXTRA_PARAMS=" --disable-neon"
fi


CPPFLAGS="-I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/include -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/libs/${CXX_TARGET}/include"
LDFLAGS="$LDFLAGS -L${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/libs/${CXX_TARGET}"

SYSROOT=$ANDROID_NDK/platforms/$ANDROID_API/arch-arm
ANDROID_BIN=$ANDROID_NDK/toolchains/arm-linux-androideabi-4.4.3/prebuilt/*-x86/bin/
CROSS_COMPILE=${ANDROID_BIN}/arm-linux-androideabi-

CPPFLAGS="$CPPFLAGS" \
CFLAGS="$CFLAGS" \
CXXFLAGS="$CFLAGS" \
LDFLAGS="$LDFLAGS" \
CC="${CROSS_COMPILE}gcc --sysroot=${SYSROOT}" \
CXX="${CROSS_COMPILE}g++ --sysroot=${SYSROOT}" \
NM="${CROSS_COMPILE}nm" \
STRIP="${CROSS_COMPILE}strip" \
RANLIB="${CROSS_COMPILE}ranlib" \
AR="${CROSS_COMPILE}ar" \
sh $VLC_SOURCEDIR/configure --host=arm-linux-androideabi --build=x86_64-unknown-linux $EXTRA_PARAMS \
                --enable-live555 --enable-realrtsp \
                --enable-avformat \
                --enable-swscale \
                --enable-avcodec \
                --enable-opensles \
                --enable-audiotrack \
                --enable-android-surface \
                --enable-debug \
                --enable-mkv \
                --enable-taglib \
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
                --disable-qt4 \
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
                --disable-x264 \
                --disable-schroedinger --disable-dirac \
                $*
