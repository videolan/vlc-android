#!/bin/sh

set -e

if test -z "$ANDROID_NDK"
then
    echo "Set ANDROID_NDK"
    exit 1
fi

PATCHDIR="`dirname $0`"
cd $ANDROID_NDK

ls -l ./build/tools/download-toolchain-sources.sh src
patch -p0 < "$PATCHDIR"/binutils-2.21.diff
(cd src/gcc/gcc-4.6/ patch -p0 < "$PATCHDIR"/../../../gcc/gcc-4.6.patch)
ls -l ./build/tools/build-gcc.sh $(pwd)/src $(pwd) arm-linux-androideabi-4.6
