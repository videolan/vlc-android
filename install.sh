#! /bin/sh

GIT=git

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK and ANDROID_SDK before starting";
   exit 1;
fi;

echo "Cloning and updating VLC"
$GIT clone git://git.videolan.org/vlc.git

echo "Applying the patches"
cd vlc
$GIT am ../patches/*.patch

echo "Building the contribs"
cd extras/contrib && ./bootstrap -t arm-eabi -d android && make

cd ../.. && mkdir -p android && cd android

if [ -e configure ]; then
    echo "Bootstraping"
    ../bootstrap
fi;

echo "Configuring"
sh ../extras/package/android/configure.sh

echo "Building"
make

echo "Building Android"
cd ../../
make distclean
VLC_BUILD_DIR=vlc/android make
