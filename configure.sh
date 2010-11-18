#!/bin/sh

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

ANDROID_BIN="$ANDROID_NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/bin/"
ANDROID_INCLUDE="$ANDROID_NDK/build/platforms/android-8/arch-arm/usr/include"
ANDROID_LIB="$ANDROID_NDK/build/platforms/android-8/arch-arm/usr/lib"

VLC_SOURCEDIR="`pwd`/.."

if [ -e "$VLC_SOURCEDIR/extras/contrib/hosts/arm-eabi/lib/libavcodec.a" ]; then
 FFMPEG_SWITCH="--enable-swscale --enable-avcodec --enable-avformat"
else
 FFMPEG_SWITCH="--disable-swscale --disable-avcodec --disable-avformat"
fi


DEPS_LIBDIR="$VLC_SOURCEDIR/extras/contrib/hosts/arm-eabi/lib"
DEPS_INCLDIR="$VLC_SOURCEDIR/extras/contrib/hosts/arm-eabi/include"



PATH="$ANDROID_BIN":$PATH \
CPPFLAGS="-I$ANDROID_INCLUDE" \
LDFLAGS="-Wl,-rpath-link=$ANDROID_LIB,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined -Wl,-shared -L$ANDROID_LIB" \
CFLAGS="" \
LIBS="-lc -ldl -lgcc" \
CC="arm-eabi-gcc -nostdlib" CXX="arm-eabi-g++ -nostdlib" NM="arm-eabi-nm" STRIP="arm-eabi-strip" \
PKG_CONFIG_LIBDIR="$VLC_SOURCEDIR/extras/contrib/hosts/arm-eabi/lib/pkgconfig" \
sh ../configure --host=arm-eabi-linux --build=x86_64-unknown-linux \
                --enable-debug \
                --enable-shared \
                --disable-qt4 \
                --disable-skins2 \
                --disable-mad \
                --disable-avcodec \
                --disable-postproc \
                --disable-mkv \
                --disable-live555 \
                --disable-a52 \
                --disable-libgcrypt \
                --disable-remoteosd \
                --disable-lua \
                $FFMPEG_SWITCH \
                --disable-xcb \
                --disable-dbus \
                --disable-vlc
