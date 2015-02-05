#!/bin/sh

#############
# FUNCTIONS #
#############

checkfail()
{
    if [ ! $? -eq 0 ];then
        echo "$1"
        exit 1
    fi
}

#############
# ARGUMENTS #
#############
if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
    echo "Please set ANDROID_ABI to your architecture:
    ARM:     armeabi-v7a, armeabi, armeabi-v5, armeabi-nofpu
    ARM64:   arm64-v8a
    X86:     x86, x86_64
    MIPS:    mips, mips64."
    exit 1
fi

RELEASE=0
for i in ${@}; do
    case "$i" in
        release|--release)
        RELEASE=1
        ;;
        *)
        ;;
    esac
done


#########
# FLAGS #
#########
# ARMv5 and ARMv6-nofpu are not really ABIs
if [ "${ANDROID_ABI}" = "armeabi-nofpu" ];then
    NO_FPU=0
    ANDROID_ABI="armeabi"
fi
if [ "${ANDROID_ABI}" = "armeabi-v5" ];then
    ARMV5=1
    NO_FPU=0
    ANDROID_ABI="armeabi"
fi
[ "${ANDROID_ABI}" = "armeabi" ] && cat << EOF
For an ARMv6 device without FPU:
$ export ANDROID_ABI="armeabi-nofpu"
For an ARMv5 device:
$ export ANDROID_ABI="armeabi-v5"
EOF

# Set up ABI variables
if [ "${ANDROID_ABI}" = "x86" ] ; then
    TARGET_TUPLE="i686-linux-android"
    PATH_HOST="x86"
    PLATFORM_SHORT_ARCH="x86"
elif [ "${ANDROID_ABI}" = "x86_64" ] ; then
    TARGET_TUPLE="x86_64-linux-android"
    PATH_HOST="x86_64"
    PLATFORM_SHORT_ARCH="x86_64"
    HAVE_64=1
elif [ "${ANDROID_ABI}" = "mips" ] ; then
    TARGET_TUPLE="mipsel-linux-android"
    PATH_HOST=$TARGET_TUPLE
    PLATFORM_SHORT_ARCH="mips"
elif [ "${ANDROID_ABI}" = "mips64" ] ; then
    TARGET_TUPLE="mips64el-linux-android"
    PATH_HOST=$TARGET_TUPLE
    PLATFORM_SHORT_ARCH="mips64"
    HAVE_64=1
elif [ "${ANDROID_ABI}" = "arm64-v8a" ] ; then
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
    ;;
    *)
        echo "You need the NDKv10 or later"
        exit 1
    ;;
esac

SYSROOT=$ANDROID_NDK/platforms/$ANDROID_API/arch-$PLATFORM_SHORT_ARCH
SRC_DIR=$PWD
# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
NDK_TOOLCHAIN_PATH=`echo ${ANDROID_NDK}/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/\`uname|tr A-Z a-z\`-*/bin`
export PATH=${NDK_TOOLCHAIN_PATH}:${PATH}


###############
# DISPLAY ABI #
###############

echo "ABI:        $ANDROID_ABI"
echo "API:        $ANDROID_API"
echo "SYSROOT:    $SYSROOT"
if [ ! -z "$NO_FPU" ]; then
echo "FPU:        NO"
fi
if [ ! -z "$ARMV5" ]; then
echo "ARMv5:       YES"
fi
echo "PATH:       $PATH"

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

##########
# CFLAGS #
##########
CFLAGS="-g -O2 -fstrict-aliasing -funsafe-math-optimizations"
if [ -n "$HAVE_ARM" -a ! -n "$HAVE_64" ]; then
    CFLAGS="${CFLAGS} -mlong-calls"
fi

EXTRA_CFLAGS=""
# Setup CFLAGS per ABI
if [ ${ANDROID_ABI} = "armeabi-v7a" ] ; then
    EXTRA_CFLAGS="-mfpu=vfpv3-d16 -mcpu=cortex-a8"
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -mthumb -mfloat-abi=softfp"
elif [ ${ANDROID_ABI} = "armeabi" ] ; then
    if [ -n "${ARMV5}" ]; then
        EXTRA_CFLAGS="-march=armv5te -mtune=arm9tdmi -msoft-float"
    else
        if [ -n "${NO_FPU}" ]; then
            EXTRA_CFLAGS="-march=armv6j -mtune=arm1136j-s -msoft-float"
        else
            EXTRA_CFLAGS="-mfpu=vfp -mcpu=arm1136jf-s -mfloat-abi=softfp"
        fi
    fi
elif [ ${ANDROID_ABI} = "x86" ] ; then
    EXTRA_CFLAGS="-march=pentium -m32"
elif [ ${ANDROID_ABI} = "mips" ] ; then
    EXTRA_CFLAGS="-march=mips32 -mtune=mips32r2 -mhard-float"
    # All MIPS Linux kernels since 2.4.4 will trap any unimplemented FPU
    # instruction and emulate it, so we select -mhard-float.
    # See http://www.linux-mips.org/wiki/Floating_point#The_Linux_kernel_and_floating_point
else
    echo "Unknown ABI. Die, die, die!"
    exit 2
fi

EXTRA_CFLAGS="${EXTRA_CFLAGS} -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/include"
EXTRA_CFLAGS="${EXTRA_CFLAGS} -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/libs/${ANDROID_ABI}/include"

CPPFLAGS="-I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/include -I${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/libs/${ANDROID_ABI}/include"

#################
# Setup LDFLAGS #
#################
EXTRA_LDFLAGS="-L${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/libs/${ANDROID_ABI} -lgnustl_static"

LDFLAGS="-Wl,-Bdynamic,-dynamic-linker=/system/bin/linker -Wl,--no-undefined"

if [ -n "$HAVE_ARM" ]; then
    if [ ${ANDROID_ABI} = "armeabi-v7a" ]; then
        EXTRA_PARAMS=" --enable-neon"
        LDFLAGS="$LDFLAGS -Wl,--fix-cortex-a8"
    fi
fi

LDFLAGS="$LDFLAGS -L${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${GCCVER}/libs/${ANDROID_ABI}"

# Release or not?
if [ "$RELEASE" = 1 ]; then
    OPTS=""
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -DNDEBUG "
else
    OPTS="--enable-debug"
fi


echo "CFLAGS:            ${CFLAGS}"
echo "EXTRA_CFLAGS:      ${EXTRA_CFLAGS}"


ANDROID_BIN=`echo $ANDROID_NDK/toolchains/${PATH_HOST}-${GCCVER}/prebuilt/\`uname|tr A-Z a-z\`-*/bin/`
export PATH=$ANDROID_BIN:$PATH
####################################################################################################

cd vlc

###########################
# Build buildsystem tools #
###########################
export PATH=`pwd`/extras/tools/build/bin:$PATH
echo "Building tools"
cd extras/tools
./bootstrap
checkfail "buildsystem tools: bootstrap failed"
make $MAKEFLAGS
checkfail "buildsystem tools: make"
cd ../..

#############
# BOOTSTRAP #
#############

if [ ! -f configure ]; then
    echo "Bootstraping"
    ./bootstrap
    checkfail "vlc: bootstrap failed"
fi

############
# Contribs #
############
echo "Building the contribs"
mkdir -p contrib/contrib-android-${TARGET_TUPLE}

gen_pc_file() {
    echo "Generating $1 pkg-config file"
    echo "Name: $1
Description: $1
Version: $2
Libs: -l$1
Cflags:" > contrib/${TARGET_TUPLE}/lib/pkgconfig/`echo $1|tr 'A-Z' 'a-z'`.pc
}

mkdir -p contrib/${TARGET_TUPLE}/lib/pkgconfig
gen_pc_file EGL 1.1
gen_pc_file GLESv2 2

cd contrib/contrib-android-${TARGET_TUPLE}

ANDROID_API=${ANDROID_API} ../bootstrap --host=${TARGET_TUPLE} --disable-disc --disable-sout \
    --enable-dvdread \
    --enable-dvdnav \
    --disable-dca \
    --disable-goom \
    --disable-chromaprint \
    --disable-lua \
    --disable-schroedinger \
    --disable-sdl \
    --disable-SDL_image \
    --disable-fontconfig \
    --enable-zvbi \
    --disable-kate \
    --disable-caca \
    --disable-gettext \
    --disable-mpcdec \
    --disable-upnp \
    --disable-gme \
    --disable-tremor \
    --enable-vorbis \
    --disable-sidplay2 \
    --disable-samplerate \
    --disable-faad2 \
    --disable-harfbuzz \
    --enable-iconv \
    --disable-aribb24 \
    --disable-aribb25 \
    --disable-mpg123 \
    --enable-libdsm
checkfail "contribs: bootstrap failed"

# TODO: mpeg2, theora

# Some libraries have arm assembly which won't build in thumb mode
# We append -marm to the CFLAGS of these libs to disable thumb mode
[ ${ANDROID_ABI} = "armeabi-v7a" ] && echo "NOTHUMB := -marm" >> config.mak

echo "EXTRA_CFLAGS= -g ${EXTRA_CFLAGS}" >> config.mak
echo "EXTRA_LDFLAGS= ${EXTRA_LDFLAGS}" >> config.mak
export VLC_EXTRA_CFLAGS="${EXTRA_CFLAGS}"                   # Makefile
export VLC_EXTRA_LDFLAGS="${EXTRA_LDFLAGS}"                 # Makefile

make fetch
checkfail "contribs: make fetch failed"

# We already have zlib available in the NDK
[ -e .zlib ] || (mkdir -p zlib; touch .zlib)
# gettext
which autopoint >/dev/null || make $MAKEFLAGS .gettext
#export the PATH
export PATH="$PATH:$PWD/../$TARGET_TUPLE/bin"
# Make
make $MAKEFLAGS
checkfail "contribs: make failed"

cd ../../

###################
# BUILD DIRECTORY #
###################
VLC_BUILD_DIR=build-android-${TARGET_TUPLE}
mkdir -p $VLC_BUILD_DIR && cd $VLC_BUILD_DIR

#############
# CONFIGURE #
#############

CROSS_COMPILE=${ANDROID_BIN}/${TARGET_TUPLE}-

if [ ! -e ./config.h ]; then
CPPFLAGS="$CPPFLAGS" \
CFLAGS="$CFLAGS ${VLC_EXTRA_CFLAGS} ${EXTRA_CFLAGS}" \
CXXFLAGS="$CFLAGS" \
LDFLAGS="$LDFLAGS" \
CC="${CROSS_COMPILE}gcc --sysroot=${SYSROOT}" \
CXX="${CROSS_COMPILE}g++ --sysroot=${SYSROOT}" \
NM="${CROSS_COMPILE}nm" \
STRIP="${CROSS_COMPILE}strip" \
RANLIB="${CROSS_COMPILE}ranlib" \
AR="${CROSS_COMPILE}ar" \
PKG_CONFIG_LIBDIR=../contrib/$TARGET_TUPLE/lib/pkgconfig \
sh ../configure --host=$TARGET_TUPLE --build=x86_64-unknown-linux $EXTRA_PARAMS \
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
                $OPTS
checkfail "vlc: configure failed"

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

# if config dependencies change, ./config.status
# is run and overwrite previously hacked config.h. So call make config.h here
# and hack config.h after.

make $MAKEFLAGS config.h
checkfail "vlc: make config.h failed"

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

fi

############
# BUILDING #
############

echo "Building"
make $MAKEFLAGS
checkfail "vlc: make failed"

cd $SRC_DIR


######################################################################################
# libvlcJNI
######################################################################################

##################
# libVLC modules #
##################
echo "Generating static module list"
VLC_MODULES=`./find_modules.sh vlc/$VLC_BUILD_DIR`

DEFINITION="";
BUILTINS="const void *vlc_static_modules[] = {\n";
for file in $VLC_MODULES; do
	name=`echo $file | sed 's/.*\.libs\/lib//' | sed 's/_plugin\.a//'`; \
	DEFINITION=$DEFINITION"int vlc_entry__$name (int (*)(void *, void *, int, ...), void *);\n"; \
	BUILTINS="$BUILTINS vlc_entry__$name,\n"; \
done; \
BUILTINS="$BUILTINS NULL\n};\n"; \
printf "/* Autogenerated from the list of modules */\n $DEFINITION\n $BUILTINS\n" > libvlc/jni/libvlcjni.h

###############################
# NDK-Build for libvlcjni.so  #
###############################

LIBVLC_LIBS="libvlcjni"
VLC_MODULES=`echo $VLC_MODULES|sed "s|vlc/$VLC_BUILD_DIR|../vlc/$VLC_BUILD_DIR|g"`
VLC_SRC_DIR="$SRC_DIR/vlc"
ANDROID_SYS_HEADERS="$SRC_DIR/android-headers"
VLC_CONTRIB="$VLC_SRC_DIR/contrib/$TARGET_TUPLE"

echo "Building NDK"

$ANDROID_NDK/ndk-build -C libvlc \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    ANDROID_SYS_HEADERS="$ANDROID_SYS_HEADERS" \
    VLC_BUILD_DIR="$VLC_SRC_DIR/$VLC_BUILD_DIR" \
    VLC_CONTRIB="$VLC_CONTRIB" \
    VLC_MODULES="$VLC_MODULES" \
    TARGET_CFLAGS="$VLC_EXTRA_CFLAGS" \
    EXTRA_LDFLAGS="$EXTRA_LDFLAGS" \
    LIBVLC_LIBS="$LIBVLC_LIBS" \
    APP_BUILD_SCRIPT=jni/Android.mk \
    APP_PLATFORM=${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    SYSROOT=${SYSROOT} \
    TARGET_TUPLE=$TARGET_TUPLE \
    HAVE_64=${HAVE_64} \
    NDK_PROJECT_PATH=jni V=1
