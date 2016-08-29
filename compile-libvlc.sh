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

RELEASE=0
ASAN=0
while [ $# -gt 0 ]; do
    case $1 in
        help|--help)
            echo "Use -a to set the ARCH"
            echo "Use --release to build in release mode"
            exit 1
            ;;
        a|-a)
            ANDROID_ABI=$2
            shift
            ;;
        -c)
            CHROME_OS=1
            ;;
        --asan)
            ASAN=1
            ;;
        release|--release)
            RELEASE=1
            ;;
    esac
    shift
done

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
    echo "Please pass the ANDROID ABI to the correct architecture, using
                compile-libvlc.sh -a ARCH
    ARM:     armeabi-v7a, armeabi, armeabi-v5, armeabi-nofpu
    ARM64:   arm64-v8a
    X86:     x86, x86_64
    MIPS:    mips, mips64."
    exit 1
fi

###########################
# VLC BOOTSTRAP ARGUMENTS #
###########################

VLC_BOOTSTRAP_ARGS="\
    --disable-disc \
    --disable-sout \
    --enable-dvdread \
    --enable-dvdnav \
    --disable-dca \
    --disable-goom \
    --disable-chromaprint \
    --enable-lua \
    --disable-schroedinger \
    --disable-sdl \
    --disable-SDL_image \
    --disable-fontconfig \
    --enable-zvbi \
    --disable-kate \
    --disable-caca \
    --disable-gettext \
    --disable-mpcdec \
    --enable-upnp \
    --disable-gme \
    --disable-tremor \
    --enable-vorbis \
    --disable-sidplay2 \
    --disable-samplerate \
    --disable-faad2 \
    --enable-harfbuzz \
    --enable-iconv \
    --disable-aribb24 \
    --disable-aribb25 \
    --enable-mpg123 \
    --enable-libdsm \
    --enable-libarchive \
    --disable-libmpeg2 \
    --enable-soxr \
    --enable-nfs \
    --enable-microdns \
    --enable-fluidlite \
    --disable-mad \
"

###########################
# VLC CONFIGURE ARGUMENTS #
###########################

VLC_CONFIGURE_ARGS="\
    --disable-nls \
    --enable-live555 --enable-realrtsp \
    --enable-avformat \
    --enable-swscale \
    --enable-avcodec \
    --enable-opus \
    --enable-opensles \
    --enable-mkv \
    --enable-taglib \
    --enable-dvbpsi \
    --disable-vlc --disable-shared \
    --disable-update-check \
    --disable-vlm \
    --disable-dbus \
    --enable-lua \
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
    --enable-mod \
    --disable-sid \
    --disable-gme \
    --disable-tremor \
    --disable-mad \
    --enable-mpg123 \
    --disable-dca \
    --disable-sdl-image \
    --enable-zvbi \
    --disable-fluidsynth \
    --enable-fluidlite \
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
    --disable-schroedinger \
"

########################
# VLC MODULE BLACKLIST #
########################

VLC_MODULE_BLACKLIST="
    addons.*
    stats
    access_(bd|shm|imem)
    oldrc
    real
    hotkeys
    gestures
    sap
    dynamicoverlay
    rss
    ball
    audiobargraph_[av]
    clone
    mosaic
    osdmenu
    puzzle
    mediadirs
    t140
    ripple
    motion
    sharpen
    grain
    posterize
    mirror
    wall
    scene
    blendbench
    psychedelic
    alphamask
    netsync
    audioscrobbler
    motiondetect
    motionblur
    export
    smf
    podcast
    bluescreen
    erase
    stream_filter_record
    speex_resampler
    remoteosd
    magnify
    gradient
    dtstofloat32
    logger
    visual
    fb
    aout_file
    yuv
    .dummy
"

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
use "-a armeabi-nofpu"
For an ARMv5 device:
use "-a armeabi-v5"
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
elif [ "${ANDROID_ABI}" = "armeabi-v7a" -o "${ANDROID_ABI}" = "armeabi" ] ; then
    TARGET_TUPLE="arm-linux-androideabi"
    PATH_HOST=$TARGET_TUPLE
    HAVE_ARM=1
    PLATFORM_SHORT_ARCH="arm"
else
    echo "Unknown ABI: '${ANDROID_ABI}'. Die, die, die!"
    exit 2
fi

# try to detect NDK version
REL=$(grep -o '^Pkg.Revision.*[0-9]*.*' $ANDROID_NDK/source.properties |cut -d " " -f 3 | cut -d "." -f 1)

if [ "$REL" -ge 12 ]; then
    if [ "${HAVE_64}" = 1 ];then
        ANDROID_API=21
    else
        ANDROID_API=9
    fi
else
    echo "You need the NDKv12 or later"
    exit 1
fi

NDK_TOOLCHAIN_DIR=${PWD}/toolchains/${PLATFORM_SHORT_ARCH}
NDK_TOOLCHAIN_PATH=${NDK_TOOLCHAIN_DIR}/bin
NDK_SUPPORT_DIR=${NDK_TOOLCHAIN_DIR}/include/support
$ANDROID_NDK/build/tools/make_standalone_toolchain.py \
    --arch ${PLATFORM_SHORT_ARCH} \
    --api ${ANDROID_API} \
    --stl libc++ \
    --install-dir ${NDK_TOOLCHAIN_DIR} 2> /dev/null
if [ ! -d ${NDK_TOOLCHAIN_PATH} ];
then
    echo "make_standalone_toolchain.py failed"
    exit 1
fi
if [ ! -f ${NDK_TOOLCHAIN_DIR}/sysroot/usr/include/uchar.h ];
then
    cp ${ANDROID_NDK}/platforms/android-24/arch-${PLATFORM_SHORT_ARCH}/usr/include/uchar.h \
        ${NDK_TOOLCHAIN_DIR}/sysroot/usr/include
fi

SRC_DIR=$PWD
# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
CROSS_TOOLS=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-

export PATH=${NDK_TOOLCHAIN_PATH}:${PATH}

###############
# DISPLAY ABI #
###############

echo "ABI:        $ANDROID_ABI"
echo "API:        $ANDROID_API"
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

VLC_CFLAGS="-std=gnu11"
VLC_CXXFLAGS="-std=gnu++11"
if [ "$NO_OPTIM" = "1" ];
then
     VLC_CFLAGS="${VLC_CFLAGS} -g -O0"
else
     VLC_CFLAGS="${VLC_CFLAGS} -g -O2"
fi

VLC_CFLAGS="${VLC_CFLAGS} -fstrict-aliasing -funsafe-math-optimizations"

# Setup CFLAGS per ABI
if [ "${ANDROID_ABI}" = "armeabi-v7a" ] ; then
    EXTRA_CFLAGS="-march=armv7-a -mfpu=vfpv3-d16 -mcpu=cortex-a8"
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -mthumb -mfloat-abi=softfp"
elif [ "${ANDROID_ABI}" = "armeabi" ] ; then
    if [ -n "${ARMV5}" ]; then
        EXTRA_CFLAGS="-march=armv5te -mtune=arm9tdmi -msoft-float"
    else
        if [ -n "${NO_FPU}" ]; then
            EXTRA_CFLAGS="-march=armv6j -mtune=arm1136j-s -msoft-float"
        else
            EXTRA_CFLAGS="-mfpu=vfp -mcpu=arm1136jf-s -mfloat-abi=softfp"
        fi
    fi
elif [ "${ANDROID_ABI}" = "x86" ] ; then
    EXTRA_CFLAGS="-mtune=atom -msse3 -mfpmath=sse -m32"
elif [ "${ANDROID_ABI}" = "mips" ] ; then
    EXTRA_CFLAGS="-march=mips32 -mtune=mips32r2 -mhard-float"
    # All MIPS Linux kernels since 2.4.4 will trap any unimplemented FPU
    # instruction and emulate it, so we select -mhard-float.
    # See http://www.linux-mips.org/wiki/Floating_point#The_Linux_kernel_and_floating_point
fi

EXTRA_CFLAGS="${EXTRA_CFLAGS} -MMD -MP -fpic -ffunction-sections -funwind-tables \
-fstack-protector-strong -Wno-invalid-command-line-argument -Wno-unused-command-line-argument \
-no-canonical-prefixes -fno-integrated-as"
EXTRA_CXXFLAGS="${EXTRA_CXXFLAGS} -fexceptions -frtti"
EXTRA_CXXFLAGS="${EXTRA_CXXFLAGS} -D__STDC_FORMAT_MACROS=1 -D__STDC_CONSTANT_MACROS=1 -D__STDC_LIMIT_MACROS=1"

#################
# Setup LDFLAGS #
#################

VLC_LDFLAGS=""
if [ -n "$HAVE_ARM" ]; then
if [ ${ANDROID_ABI} = "armeabi-v7a" ]; then
        EXTRA_PARAMS=" --enable-neon"
        VLC_LDFLAGS="${VLC_LDFLAGS} -Wl,--fix-cortex-a8"
    fi
fi
NDK_LIB_DIR="${NDK_TOOLCHAIN_DIR}/${TARGET_TUPLE}/lib"
if [ "${PLATFORM_SHORT_ARCH}" = "x86_64" ];then
    NDK_LIB_DIR="${NDK_LIB_DIR}64"
fi
if [ "${ANDROID_ABI}" = "armeabi-v7a" ];then
    NDK_LIB_DIR="${NDK_LIB_DIR}/armv7-a"
fi

VLC_LDFLAGS="${VLC_LDFLAGS} -L${NDK_LIB_DIR} -lc++abi"

# Release or not?
if [ "$RELEASE" = 1 ]; then
    OPTS=""
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -DNDEBUG "
    NDK_DEBUG=0
else
    OPTS="--enable-debug"
    NDK_DEBUG=1
fi

if [ "${ASAN}" = 1 ];then
    VLC_CFLAGS="${VLC_CFLAGS} -O0 -fno-omit-frame-pointer -fsanitize=address"
    VLC_CXXFLAGS="${VLC_CXXLAGS} -O0 -fno-omit-frame-pointer -fsanitize=address"
    VLC_LDFLAGS="${VLC_LDFLAGS} -ldl -fsanitize=address"
    # ugly, sorry
    if [ "${ANDROID_API}" = "9" ];then
        cp ${ANDROID_NDK}/platforms/android-9/arch-${PLATFORM_SHORT_ARCH}/usr/include/stdlib.h \
            ${NDK_TOOLCHAIN_DIR}/sysroot/usr/include
        echo "extern int posix_memalign(void **memptr, size_t alignment, size_t size);" \
            >> ${NDK_TOOLCHAIN_DIR}/sysroot/usr/include/stdlib.h
    fi
fi

echo "EXTRA_CFLAGS:      ${EXTRA_CFLAGS}"
echo "VLC_CFLAGS:        ${VLC_CFLAGS}"

cd vlc

###########################
# Build buildsystem tools #
###########################
VLC_SRC_DIR="$SRC_DIR/vlc"

export PATH=`pwd`/extras/tools/build/bin:$PATH
echo "Building tools"
cd extras/tools
./bootstrap
checkfail "buildsystem tools: bootstrap failed"
make $MAKEFLAGS
make $MAKEFLAGS .gas
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

ANDROID_ABI=${ANDROID_ABI} ANDROID_API=android-${ANDROID_API} \
    ../bootstrap --host=${TARGET_TUPLE} ${VLC_BOOTSTRAP_ARGS}
checkfail "contribs: bootstrap failed"

# Some libraries have arm assembly which won't build in thumb mode
# We append -marm to the CFLAGS of these libs to disable thumb mode
[ ${ANDROID_ABI} = "armeabi-v7a" ] && echo "NOTHUMB := -marm" >> config.mak

echo "EXTRA_CFLAGS=${EXTRA_CFLAGS}" >> config.mak
echo "EXTRA_CXXFLAGS=${EXTRA_CXXFLAGS}" >> config.mak

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

if [ "${CHROME_OS}" = "1" ];then
    VLC_BUILD_DIR=build-chrome-${TARGET_TUPLE}
else
    VLC_BUILD_DIR=build-android-${TARGET_TUPLE}
fi
if [ "${ASAN}" = 1 ];then
    VLC_BUILD_DIR=${VLC_BUILD_DIR}-asan
fi
mkdir -p $VLC_BUILD_DIR && cd $VLC_BUILD_DIR

#############
# CONFIGURE #
#############

if [ "${CHROME_OS}" = "1" ];then
    # chrome OS doesn't have eventfd
    export ac_cv_func_eventfd=no
    export ac_cv_header_sys_eventfd_h=no
    export ac_cv_func_pipe2=no
fi

NDK_SUPPORT_INCLUDES=""
if [ ${ANDROID_API} = "21" ] ; then
    # android-21 has empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    export ac_cv_header_sys_shm_h=no
else
    # force nanf and uselocale using libandroid_support since it's present in libc++
    if [ ! -d ${NDK_SUPPORT_DIR} ];then
        mkdir -p ${NDK_SUPPORT_DIR}
        cp ${ANDROID_NDK}/sources/android/support/include/locale.h ${NDK_SUPPORT_DIR}
        cp ${ANDROID_NDK}/sources/android/support/include/xlocale.h ${NDK_SUPPORT_DIR}
        cp ${ANDROID_NDK}/sources/android/support/include/math.h ${NDK_SUPPORT_DIR}
    fi
    export ac_cv_lib_m_nanf=yes
    export ac_cv_func_uselocale=yes
    NDK_SUPPORT_INCLUDES="-I${NDK_SUPPORT_DIR}"
    VLC_LDFLAGS="${VLC_LDFLAGS} -L${NDK_LIB_DIR} -landroid_support"
fi

if [ ! -e ./config.h -o "$RELEASE" = 1 ]; then
CPPFLAGS="${CONTRIB_INCLUDES}" \
CFLAGS="${VLC_CFLAGS} ${EXTRA_CFLAGS} ${NDK_SUPPORT_INCLUDES}" \
CXXFLAGS="${VLC_CXXFLAGS} ${EXTRA_CFLAGS} ${EXTRA_CXXFLAGS}" \
CC="${CROSS_TOOLS}clang" \
CXX="${CROSS_TOOLS}clang++" \
NM="${CROSS_TOOLS}nm" \
STRIP="${CROSS_TOOLS}strip" \
RANLIB="${CROSS_TOOLS}ranlib" \
AR="${CROSS_TOOLS}ar" \
PKG_CONFIG_LIBDIR=$VLC_SRC_DIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
PATH=../contrib/bin:$PATH \
sh ../configure --host=$TARGET_TUPLE --build=x86_64-unknown-linux \
    --with-contrib=${VLC_SRC_DIR}/contrib/${TARGET_TUPLE} \
    ${EXTRA_PARAMS} ${VLC_CONFIGURE_ARGS} ${OPTS}
checkfail "vlc: configure failed"
fi

############
# BUILDING #
############

echo "Building"
make $MAKEFLAGS
checkfail "vlc: make failed"

cd $SRC_DIR

echo ok

##################
# libVLC modules #
##################

REDEFINED_VLC_MODULES_DIR=$SRC_DIR/.modules/${VLC_BUILD_DIR}
rm -rf ${REDEFINED_VLC_MODULES_DIR}
mkdir -p ${REDEFINED_VLC_MODULES_DIR}

echo "Generating static module list"
blacklist_regexp=
for i in ${VLC_MODULE_BLACKLIST}
do
    if [ -z "${blacklist_regexp}" ]
    then
        blacklist_regexp="${i}"
    else
        blacklist_regexp="${blacklist_regexp}|${i}"
    fi
done

find_modules()
{
    echo "`find $1 -name 'lib*plugin.a' | grep -vE "lib(${blacklist_regexp})_plugin.a" | tr '\n' ' '`"
}

get_symbol()
{
    echo "$1" | grep vlc_entry_$2|cut -d" " -f 3
}

VLC_MODULES=$(find_modules vlc/$VLC_BUILD_DIR/modules)
DEFINITION="";
BUILTINS="const void *vlc_static_modules[] = {\n";
for file in $VLC_MODULES; do
    outfile=${REDEFINED_VLC_MODULES_DIR}/`basename $file`
    name=`echo $file | sed 's/.*\.libs\/lib//' | sed 's/_plugin\.a//'`;
    symbols=$("${CROSS_TOOLS}nm" -g $file)

    # assure that all modules have differents symbol names
    entry=$(get_symbol "$symbols" _)
    copyright=$(get_symbol "$symbols" copyright)
    license=$(get_symbol "$symbols" license)
    cat <<EOF > ${REDEFINED_VLC_MODULES_DIR}/syms
AccessOpen AccessOpen__$name
AccessClose AccessClose__$name
StreamOpen StreamOpen__$name
StreamClose StreamClose__$name
DemuxOpen DemuxOpen__$name
DemuxClose DemuxClose__$name
OpenFilter OpenFilter__$name
CloseFilter CloseFilter__$name
Open Open__$name
Close Close__$name
$entry vlc_entry__$name
$copyright vlc_entry_copyright__$name
$license vlc_entry_license__$name
EOF
    ${CROSS_TOOLS}objcopy --redefine-syms ${REDEFINED_VLC_MODULES_DIR}/syms $file $outfile
    checkfail "objcopy failed"

    DEFINITION=$DEFINITION"int vlc_entry__$name (int (*)(void *, void *, int, ...), void *);\n";
    BUILTINS="$BUILTINS vlc_entry__$name,\n";
done;
BUILTINS="$BUILTINS NULL\n};\n"; \
printf "/* Autogenerated from the list of modules */\n#include <unistd.h>\n$DEFINITION\n$BUILTINS\n" > libvlc/jni/libvlcjni-modules.c

DEFINITION=""
BUILTINS="const void *libvlc_functions[] = {\n";
for func in `cat vlc/lib/libvlc.sym`
do
    DEFINITION=$DEFINITION"int $func(void);\n";
    BUILTINS="$BUILTINS $func,\n";
done
BUILTINS="$BUILTINS NULL\n};\n"; \
printf "/* Autogenerated from the list of modules */\n#include <unistd.h>\n$DEFINITION\n$BUILTINS\n" > libvlc/jni/libvlcjni-symbols.c

rm ${REDEFINED_VLC_MODULES_DIR}/syms

############################################
# NDK-Build for libvlc.so and libvlcjni.so #
############################################

LIBVLC_LIBS="libvlcjni"
VLC_MODULES=$(find_modules ${REDEFINED_VLC_MODULES_DIR})
ANDROID_SYS_HEADERS="$SRC_DIR/android-headers"
VLC_CONTRIB="$VLC_SRC_DIR/contrib/$TARGET_TUPLE"
VLC_CONTRIB_LDFLAGS=`for i in $(/bin/ls $VLC_CONTRIB/lib/pkgconfig/*.pc); do PKG_CONFIG_PATH="$VLC_CONTRIB/lib/pkgconfig/" pkg-config --libs $i; done |xargs`

if [ "${CHROME_OS}" != "1" ];then
    if [ "${HAVE_64}" != 1 ];then
        # Can't link with 32bits symbols.
        # Not a problem since MediaCodec should work on 64bits devices (android-21)
        LIBIOMX_LIBS="libiomx.14 libiomx.13 libiomx.10"
        LIBANW_LIBS="libanw.10 libanw.13 libanw.14 libanw.18"
    fi
    # (after android Jelly Bean, we prefer to use MediaCodec instead of iomx)
    # LIBIOMX_LIBS="${LIBIOMX_LIBS} libiomx.19 libiomx.18"

    LIBANW_LIBS="$LIBANW_LIBS libanw.21"
fi

echo "Building NDK"

HAVE_LIBCOMPAT=
if [ "${ANDROID_API}" = "9" ] && [ "${ANDROID_ABI}" = "armeabi-v7a" -o "${ANDROID_ABI}" = "armeabi" ] ; then
    HAVE_LIBCOMPAT=1
fi

$ANDROID_NDK/ndk-build -C libvlc \
    APP_STL="c++_shared" \
    LOCAL_CPP_FEATURES="rtti exceptions" \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    VLC_BUILD_DIR="$VLC_SRC_DIR/$VLC_BUILD_DIR" \
    VLC_CONTRIB="$VLC_CONTRIB" \
    VLC_CONTRIB_LDFLAGS="$VLC_CONTRIB_LDFLAGS" \
    VLC_MODULES="$VLC_MODULES" \
    VLC_LDFLAGS="$VLC_LDFLAGS" \
    LIBVLC_LIBS="$LIBVLC_LIBS" \
    APP_BUILD_SCRIPT=jni/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    TARGET_TUPLE=$TARGET_TUPLE \
    NDK_PROJECT_PATH=jni \
    NDK_TOOLCHAIN_VERSION=clang \
    NDK_DEBUG=${NDK_DEBUG} \
    HAVE_LIBCOMPAT=${HAVE_LIBCOMPAT}

checkfail "ndk-build failed"

$ANDROID_NDK/ndk-build -C libvlc \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    ANDROID_SYS_HEADERS="$ANDROID_SYS_HEADERS" \
    LIBIOMX_LIBS="$LIBIOMX_LIBS" \
    LIBANW_LIBS="$LIBANW_LIBS" \
    APP_BUILD_SCRIPT=private_libs/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    TARGET_TUPLE=$TARGET_TUPLE \
    NDK_PROJECT_PATH=private_libs \
    NDK_TOOLCHAIN_VERSION=clang

checkfail "ndk-build failed"

if [ "${HAVE_LIBCOMPAT}" = "1" ];then
    $ANDROID_NDK/ndk-build -C libvlc \
        APP_BUILD_SCRIPT=libcompat/Android.mk \
        APP_PLATFORM=android-${ANDROID_API} \
        APP_ABI="armeabi" \
        NDK_PROJECT_PATH=libcompat \
        NDK_TOOLCHAIN_VERSION=clang
    checkfail "ndk-build compat failed"
fi

VERSION=$(grep "android:versionName" vlc-android/AndroidManifest.xml|cut -d\" -f 2)
OUT_DBG_DIR=.dbg/${ANDROID_ABI}/$VERSION

echo "Dumping dbg symbols info ${OUT_DBG_DIR}"

mkdir -p $OUT_DBG_DIR
cp -a libvlc/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
cp -a libvlc/private_libs/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
