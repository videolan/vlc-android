#! /bin/sh
set -e

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
   echo "They must point to your NDK and SDK directories.\n"
   exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
   echo "*** No ANDROID_ABI defined architecture: using ARMv7"
   ANDROID_ABI="armeabi-v7a"
fi

# Fetch VLC source
# 1/ libvlc, libvlccore and its plugins
TESTED_HASH=18e445a
if [ ! -d "vlc" ]; then
    echo "VLC source not found, cloning"
    git clone git://git.videolan.org/vlc.git vlc
else
    echo "VLC source found"
    cd vlc
    if ! git cat-file -e ${TESTED_HASH}; then
        cat << EOF
***
*** Error: Your vlc checkout does not contain the latest tested commit: ${TESTED_HASH}
***
EOF
        exit 1
    fi
    cd ..
fi

############
# Make VLC #
############
echo "Configuring"
./compile-libvlc.sh $*
