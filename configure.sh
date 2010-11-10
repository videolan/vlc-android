#!/bin/sh

if [ "x$1" = "x" ]; then
    echo "Give the ndk path"
    exit 1
fi

ANDROID_NDK=$1
ANDROID_BIN="$ANDROID_NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/bin/"
ANDROID_INCLUDE="$ANDROID_NDK/build/platforms/android-8/arch-arm/usr/include"
ANDROID_LIB="$ANDROID_NDK/build/platforms/android-8/arch-arm/usr/lib"

PATH="$ANDROID_BIN":$PATH \
CPPFLAGS="-I$ANDROID_INCLUDE" \
LDFLAGS="-Wl,-rpath-link=$ANDROID_LIB,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined -Wl,-shared -L$ANDROID_LIB" \
CFLAGS="-nostdlib" \
LIBS="-lc -ldl -lgcc" \
CC=arm-eabi-gcc CXX=arm-eabi-g++ \
sh ../configure --host=arm-eabi --build=i386-linux \
                --enable-debug \
                --disable-qt4 \
                --disable-skins2 \
                --disable-mad \
                --disable-avcodec \
                --disable-postproc \
                --disable-mkv \
                --disable-live555 \
                --disable-a52 \
                --disable-libgcrypt \
                --disable-remoteosd
