#! /bin/sh

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

set -e

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK and ANDROID_ABI before starting."
   echo "They must point to your NDK and SDK directories.\n"
   exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
   echo "Please set ANDROID_ABI to your architecture: armeabi-v7a, armeabi, arm64-v8a, x86, x86_64 or mips."
   exit 1
fi

if [ -z "$NO_FPU" ];then
    NO_FPU=0
fi
if [ -z "$NO_ARMV6" ];then
    NO_ARMV6=0
fi

BUILD=0
FETCH=0
RELEASE=0
JNI=0

for i in ${@}; do
    case "$i" in
        --fetch)
        FETCH=1
        ;;
        --build)
        BUILD=1
        ;;
        release|--release)
        RELEASE=1
        ;;
        jni|--jni)
        JNI=1
        ;;
        *)
        ;;
    esac
done

if [ "$BUILD" = 0 -a "$FETCH" = 0 ];then
    BUILD=1
    FETCH=1
fi

HAVE_ARM=0
HAVE_X86=0
HAVE_MIPS=0
HAVE_64=0

# Set up ABI variables
if [ ${ANDROID_ABI} = "x86" ] ; then
    TARGET_TUPLE="i686-linux-android"
    PATH_HOST="x86"
    HAVE_X86=1
    PLATFORM_SHORT_ARCH="x86"
elif [ ${ANDROID_ABI} = "x86_64" ] ; then
    TARGET_TUPLE="x86_64-linux-android"
    PATH_HOST="x86_64"
    HAVE_X86=1
    HAVE_64=1
    PLATFORM_SHORT_ARCH="x86_64"
elif [ ${ANDROID_ABI} = "mips" ] ; then
    TARGET_TUPLE="mipsel-linux-android"
    PATH_HOST=$TARGET_TUPLE
    HAVE_MIPS=1
    PLATFORM_SHORT_ARCH="mips"
elif [ ${ANDROID_ABI} = "arm64-v8a" ] ; then
    TARGET_TUPLE="aarch64-linux-android"
    PATH_HOST=$TARGET_TUPLE
    HAVE_ARM=1
    HAVE_64=1
    PLATFORM_SHORT_ARCH="arm64"
else
    TARGET_TUPLE="arm-linux-androideabi"
    PATH_HOST=$TARGET_TUPLE
    HAVE_ARM=1
    PLATFORM_SHORT_ARCH="arm"
fi

# try to detect NDK version
REL=$(grep -o '^r[0-9]*.*' $ANDROID_NDK/RELEASE.TXT 2>/dev/null|cut -b2-)
case "$REL" in
    10*)
        if [ "${HAVE_64}" = 1 ];then
            GCCVER=4.9
            ANDROID_API=android-21
        else
            GCCVER=4.8
            ANDROID_API=android-9
        fi
        CXXSTL="/"${GCCVER}
    ;;
    *)
        echo "You need the NDKv10 or later"
        exit 1
    ;;
esac

export GCCVER
export CXXSTL
export ANDROID_API

# XXX : important!
[ "$HAVE_ARM" = 1 ] && cat << EOF
For an ARMv6 device without FPU:
$ export NO_FPU=1
For an ARMv5 device:
$ export NO_ARMV6=1

If you plan to use a release build, run 'compile.sh release'
EOF

export TARGET_TUPLE
export PATH_HOST
export HAVE_ARM
export HAVE_X86
export HAVE_MIPS
export HAVE_64
export PLATFORM_SHORT_ARCH

# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
NDK_TOOLCHAIN_PATH=`echo ${ANDROID_NDK}/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/\`uname|tr A-Z a-z\`-*/bin`
export PATH=${NDK_TOOLCHAIN_PATH}:${PATH}

ANDROID_PATH="`pwd`"

# Fetch VLC source
if [ "$FETCH" = 1 ]
then
    # 1/ libvlc, libvlccore and its plugins
    TESTED_HASH=18e445a
    if [ ! -d "vlc" ]; then
        echo "VLC source not found, cloning"
        git clone git://git.videolan.org/vlc.git vlc
        cd vlc
        git checkout -B android ${TESTED_HASH}
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

*** : This will delete any changes you made to the current branch ***

EOF
            exit 1
        fi
    fi
else
    cd vlc
fi

if [ "$BUILD" = 0 ]
then
    echo "Not building anything, please run $0 --build"
    exit 0
fi

# Make in //
if [ -z "$MAKEFLAGS" ]; then
    UNAMES=$(uname -s)
    MAKEFLAGS=
    if which nproc >/dev/null; then
        MAKEFLAGS=-j`nproc`
    elif [ "$UNAMES" == "Darwin" ] && which sysctl >/dev/null; then
        MAKEFLAGS=-j`sysctl -n machdep.cpu.thread_count`
    fi
fi

############
# Make VLC #
############
echo "Configuring"
${ANDROID_PATH}/compile-libvlc.sh $*

####################################
# VLC android UI and specific code
####################################
echo "Building VLC for Android"
cd ../

if [ "$JNI" = 1 ]; then
    CLEAN="jniclean"
    TARGET="libvlc/obj/local/${ANDROID_ABI}/libvlcjni.so"
else
    CLEAN="distclean"
    TARGET=
fi

export ANDROID_SYS_HEADERS=${PWD}/android-headers

export ANDROID_LIBS=${PWD}/android-libs
export VLC_BUILD_DIR=vlc/build-android-${TARGET_TUPLE}

make $CLEAN
make -j1 TARGET_TUPLE=$TARGET_TUPLE PLATFORM_SHORT_ARCH=$PLATFORM_SHORT_ARCH CXXSTL=$CXXSTL RELEASE=$RELEASE $TARGET

#
# Exporting a environment script with all the necessary variables
#
echo "Generating environment script."
cat <<EOF
This is a script that will export many of the variables used in this
script. It will allow you to compile parts of the build without having
to rebuild the entire build (e.g. recompile only the Java part).

To use it, include the script into your shell, like this:
    source env.sh

Now, you can use this command to build the Java portion:
    make -e

The file will be automatically regenerated by compile.sh, so if you change
your NDK/SDK locations or any build configurations, just re-run this
script (sh compile.sh) and it will automatically update the file.

EOF

echo "# This file was automatically generated by compile.sh" > env.sh
echo "# Re-run 'sh compile.sh' to update this file." >> env.sh

# The essentials
cat <<EssentialsA >> env.sh
export ANDROID_API=$ANDROID_API
export ANDROID_ABI=$ANDROID_ABI
export ANDROID_SDK=$ANDROID_SDK
export ANDROID_NDK=$ANDROID_NDK
export GCCVER=$GCCVER
export CXXSTL=$CXXSTL
export ANDROID_SYS_HEADERS=$ANDROID_SYS_HEADERS
export ANDROID_LIBS=$ANDROID_LIBS
export VLC_BUILD_DIR=$VLC_BUILD_DIR
export TARGET_TUPLE=$TARGET_TUPLE
export PATH_HOST=$PATH_HOST
export PLATFORM_SHORT_ARCH=$PLATFORM_SHORT_ARCH
export RELEASE=$RELEASE
EssentialsA

# PATH
echo "export PATH=$NDK_TOOLCHAIN_PATH:\${ANDROID_SDK}/platform-tools:\${PATH}" >> env.sh

# CPU flags
echo "export HAVE_ARM=${HAVE_ARM}" >> env.sh
echo "export HAVE_X86=${HAVE_X86}" >> env.sh
echo "export HAVE_MIPS=${HAVE_MIPS}" >> env.sh

echo "export NO_ARMV6=${NO_ARMV6}" >> env.sh
echo "export NO_FPU=${NO_FPU}" >> env.sh
echo "export HAVE_64=${HAVE_64}" >> env.sh
