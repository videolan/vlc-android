#!/bin/sh

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

ANDROID_API=android-9

ANDROID_BIN=$ANDROID_NDK/toolchains/arm-linux-androideabi-4.4.3/prebuilt/*-x86/bin/
ANDROID_INCLUDE=$ANDROID_NDK/platforms/$ANDROID_API/arch-arm/usr/include
ANDROID_LIB=$ANDROID_NDK/platforms/$ANDROID_API/arch-arm/usr/lib
GCC_PREFIX=${ANDROID_BIN}/arm-linux-androideabi-

VLC_SOURCEDIR="`dirname $0`/../../.."

# needed for old ndk: change all the arm-linux-androideabi to arm-eabi
# the --host is kept on purpose because otherwise libtool complains..

EXTRA_CFLAGS="-mlong-calls -fstrict-aliasing -fprefetch-loop-arrays -ffast-math"
EXTRA_LDFLAGS=""
EXTRA_PARAMS=""
if [ -z "$NO_NEON" ]; then
	EXTRA_CFLAGS+=" -mfpu=neon -mtune=cortex-a8 -ftree-vectorize -mvectorize-with-neon-quad"
	EXTRA_LDFLAGS="-Wl,--fix-cortex-a8"
else
	EXTRA_CFLAGS+=" -march=armv6j -mtune=arm1136j-s -msoft-float"
	EXTRA_PARAMS=" --disable-neon"
fi

PATH="$ANDROID_BIN:$PATH" \
CPPFLAGS="-I$ANDROID_INCLUDE" \
LDFLAGS="-Wl,-rpath-link=$ANDROID_LIB,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined -Wl,-shared -L$ANDROID_LIB $EXTRA_LDFLAGS" \
CFLAGS="-nostdlib $EXTRA_CFLAGS -O2" \
CXXFLAGS="-nostdlib $EXTRA_CFLAGS -O2" \
LIBS="-lc -ldl -lgcc" \
CC="${GCC_PREFIX}gcc" \
CXX="${GCC_PREFIX}g++" \
NM="${GCC_PREFIX}nm" \
STRIP="${GCC_PREFIX}strip" \
RANLIB="${GCC_PREFIX}ranlib" \
AR="${GCC_PREFIX}ar" \
PKG_CONFIG_LIBDIR="$VLC_SOURCEDIR/extras/contrib/hosts/arm-eabi/lib/pkgconfig" \
sh ../configure --host=arm-eabi-linux --build=x86_64-unknown-linux $EXTRA_PARAMS \
                --disable-alsa \
                --disable-atmo \
                --disable-caca \
                --disable-dbus \
                --disable-dv \
                --disable-dvdnav \
                --disable-dvdread \
                --disable-egl \
                --disable-freetype \
                --disable-gl \
                --disable-glx \
                --disable-gnomevfs \
                --disable-jack \
                --disable-libgcrypt \
                --disable-libva \
                --disable-libxml2 \
                --disable-linsys \
                --disable-lua \
                --disable-mad \
                --disable-mkv \
                --disable-mtp \
                --disable-notify \
                --disable-pulse \
                --disable-qt4 \
                --disable-schroedinger \
                --disable-sdl \
                --disable-sdl-image \
                --disable-skins2 \
                --disable-sqlite \
                --disable-svg \
                --disable-taglib \
                --disable-udev \
                --disable-v4l2 \
                --disable-vcd \
                --disable-vlc \
                --disable-x264 \
                --disable-xcb \
                --enable-android-vout \
                --enable-avcodec \
                --enable-avformat \
                --enable-debug \
                --enable-live555 \
                --enable-opensles \
                --enable-realrtsp \
                --enable-static-modules \
                --enable-swscale
