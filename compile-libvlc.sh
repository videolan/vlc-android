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

MEDIALIBRARY_HASH=082216a

BUILD_ML=1
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
        --no-ml)
            BUILD_ML=0
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
    ARM:     (armeabi-v7a|arm)
    ARM64:   (arm64-v8a|arm64)
    X86:     x86, x86_64
    MIPS:    mips, mips64."
    exit 1
fi

###########################
# VLC BOOTSTRAP ARGUMENTS #
###########################

VLC_BOOTSTRAP_ARGS="\
    --disable-disc \
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
    --disable-vncclient \
    --disable-vnc \
    --enable-jpeg \
    --enable-libplacebo \
    --enable-ad-clauses \
    --disable-srt \
    --enable-vpx \
    --disable-x265 \
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
    --enable-matroska \
    --enable-taglib \
    --enable-dvbpsi \
    --disable-vlc --disable-shared \
    --disable-update-check \
    --disable-vlm \
    --disable-dbus \
    --enable-lua \
    --disable-vcd \
    --disable-v4l2 \
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
    --disable-qt \
    --disable-skins2 \
    --disable-mtp \
    --disable-notify \
    --enable-libass \
    --disable-svg \
    --disable-udev \
    --enable-libxml2 \
    --disable-caca \
    --enable-gles2 \
    --disable-goom \
    --disable-projectm \
    --enable-sout \
    --enable-vorbis \
    --disable-faad \
    --disable-schroedinger \
    --disable-vncclient \
    --disable-vnc \
    --enable-jpeg \
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
if [ "${ANDROID_ABI}" = "arm" ] ; then
    ANDROID_ABI="armeabi-v7a"
elif [ "${ANDROID_ABI}" = "arm64" ] ; then
    ANDROID_ABI="arm64-v8a"
fi

# Set up ABI variables
if [ "${ANDROID_ABI}" = "x86" ] ; then
    TARGET_TUPLE="i686-linux-android"
    PLATFORM_SHORT_ARCH="x86"
elif [ "${ANDROID_ABI}" = "x86_64" ] ; then
    TARGET_TUPLE="x86_64-linux-android"
    PLATFORM_SHORT_ARCH="x86_64"
    HAVE_64=1
elif [ "${ANDROID_ABI}" = "mips" ] ; then
    TARGET_TUPLE="mipsel-linux-android"
    PLATFORM_SHORT_ARCH="mips"
elif [ "${ANDROID_ABI}" = "mips64" ] ; then
    TARGET_TUPLE="mips64el-linux-android"
    PLATFORM_SHORT_ARCH="mips64"
    HAVE_64=1
elif [ "${ANDROID_ABI}" = "arm64-v8a" ] ; then
    TARGET_TUPLE="aarch64-linux-android"
    HAVE_ARM=1
    HAVE_64=1
    PLATFORM_SHORT_ARCH="arm64"
elif [ "${ANDROID_ABI}" = "armeabi-v7a" ] ; then
    TARGET_TUPLE="arm-linux-androideabi"
    HAVE_ARM=1
    PLATFORM_SHORT_ARCH="arm"
else
    echo "Unknown ABI: '${ANDROID_ABI}'. Die, die, die!"
    exit 2
fi

SRC_DIR=$PWD
VLC_SRC_DIR="$SRC_DIR/vlc"
VLC_CONTRIB="$VLC_SRC_DIR/contrib/$TARGET_TUPLE"

# try to detect NDK version
REL=$(grep -o '^Pkg.Revision.*[0-9]*.*' $ANDROID_NDK/source.properties |cut -d " " -f 3 | cut -d "." -f 1)

# NDK 15 and after drops support for old android platforms (bellow
# ANDROID_API=14) but these platforms are still supported by VLC 3.0.
# TODO: Switch to NDK 15 when we drop support for old android plaftorms (for VLC 4.0)
if [ "$REL" -eq 14 ]; then
    if [ "${HAVE_64}" = 1 ];then
        ANDROID_API=21
    else
        ANDROID_API=9
    fi
else
    echo "NDK v14 needed, cf. https://developer.android.com/ndk/downloads/older_releases.html#ndk-14-downloads"
    exit 1
fi

NDK_FORCE_ARG=
NDK_TOOLCHAIN_DIR=${PWD}/toolchains/${PLATFORM_SHORT_ARCH}
NDK_TOOLCHAIN_PROPS=${NDK_TOOLCHAIN_DIR}/source.properties
NDK_TOOLCHAIN_PATH=${NDK_TOOLCHAIN_DIR}/bin

if [ "`cat \"${NDK_TOOLCHAIN_PROPS}\" 2>/dev/null`" != "`cat \"${ANDROID_NDK}/source.properties\"`" ];then
     echo "NDK changed, making new toolchain"
     NDK_FORCE_ARG="--force"
fi

$ANDROID_NDK/build/tools/make_standalone_toolchain.py \
    --arch ${PLATFORM_SHORT_ARCH} \
    --api ${ANDROID_API} \
    --stl libc++ \
    ${NDK_FORCE_ARG} \
    --install-dir ${NDK_TOOLCHAIN_DIR} 2> /dev/null
if [ ! -d ${NDK_TOOLCHAIN_PATH} ];
then
    echo "make_standalone_toolchain.py failed"
    exit 1
fi

if [ ! -z "${NDK_FORCE_ARG}" ];then
    # Don't mess up nl_langinfo() detection since this symbol is not present for 64
    # bits
    if [ "${HAVE_64}" = 1 ];then
        rm ${NDK_TOOLCHAIN_DIR}/sysroot/usr/local/include/langinfo.h
    fi
fi

if [ ! -z "${NDK_FORCE_ARG}" ];then
    cp "$ANDROID_NDK/source.properties" "${NDK_TOOLCHAIN_PROPS}"
fi

# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
CROSS_TOOLS=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-

export PATH="${NDK_TOOLCHAIN_PATH}:${PATH}"
if [ ! -z "$MSYSTEM_PREFIX" ] ; then
    # The make.exe and awk.exe from the toolchain don't work in msys
    export PATH="$MSYSTEM_PREFIX/bin:/usr/bin:${NDK_TOOLCHAIN_PATH}:${PATH}"
fi

ON_WINDOWS=0
if [ ! -z "$MSYSTEM_PREFIX" ] ; then
    # The make.exe and awk.exe from the toolchain don't work in msys
    export PATH="$MSYSTEM_PREFIX/bin:/usr/bin:${NDK_TOOLCHAIN_PATH}:${PATH}"
    ON_WINDOWS=1
fi

###############
# DISPLAY ABI #
###############

echo "ABI:        $ANDROID_ABI"
echo "API:        $ANDROID_API"
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
     VLC_CXXFLAGS="${VLC_CXXFLAGS} -g -O0"
else
     VLC_CFLAGS="${VLC_CFLAGS} -g -O2"
     VLC_CXXFLAGS="${VLC_CXXFLAGS} -g -O2"
fi

VLC_CFLAGS="${VLC_CFLAGS} -fstrict-aliasing -funsafe-math-optimizations"
VLC_CXXFLAGS="${VLC_CXXFLAGS} -fstrict-aliasing -funsafe-math-optimizations"

# Setup CFLAGS per ABI
if [ "${ANDROID_ABI}" = "armeabi-v7a" ] ; then
    EXTRA_CFLAGS="-march=armv7-a -mfpu=vfpv3-d16 -mcpu=cortex-a8"
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -mthumb -mfloat-abi=softfp"
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

EXTRA_LDFLAGS="${VLC_LDFLAGS}"
if [ ${ANDROID_ABI} = "armeabi-v7a" ]; then
        EXTRA_PARAMS=" --enable-neon"
        EXTRA_LDFLAGS="${EXTRA_LDFLAGS} -Wl,--fix-cortex-a8"
fi
NDK_LIB_DIR="${NDK_TOOLCHAIN_DIR}/${TARGET_TUPLE}/lib"
if [ "${PLATFORM_SHORT_ARCH}" = "x86_64" -o "${PLATFORM_SHORT_ARCH}" = "mips64" ];then
    NDK_LIB_DIR="${NDK_LIB_DIR}64"
fi

EXTRA_LDFLAGS="${EXTRA_LDFLAGS} -L${NDK_LIB_DIR} -lc++abi -lc++_static"
VLC_LDFLAGS="${EXTRA_LDFLAGS}"

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
    VLC_CXXFLAGS="${VLC_CXXFLAGS} -O0 -fno-omit-frame-pointer -fsanitize=address"
    VLC_LDFLAGS="${VLC_LDFLAGS} -ldl -fsanitize=address"
    # ugly, sorry
    if [ "${ANDROID_API}" = "9" ];then
        if [ ! -f vlc/contrib/${TARGET_TUPLE}/include/stdlib.h ]; then
            mkdir -p vlc/contrib/${TARGET_TUPLE}/include
            printf "#include_next <stdlib.h>\n"
                   "#ifdef __cplusplus\n"
                   "extern \"C\" {\n"
                   "#endif\n"
                   "extern int posix_memalign(void **memptr, size_t alignment, size_t size);\n"
                   "#ifdef __cplusplus\n"
                   "}\n"
                   "#endif\n" \
                > vlc/contrib/${TARGET_TUPLE}/include/stdlib.h
        fi
    fi
fi

echo "EXTRA_CFLAGS:      ${EXTRA_CFLAGS}"
echo "VLC_CFLAGS:        ${VLC_CFLAGS}"
echo "VLC_CXXFLAGS:      ${VLC_CXXFLAGS}"

cd vlc

###########################
# Build buildsystem tools #
###########################

export PATH="`pwd`/extras/tools/build/bin:$PATH"
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

# don't use the dummy uchar.c
if [ ! -f contrib/${TARGET_TUPLE}/include/uchar.h ]; then
    mkdir -p contrib/${TARGET_TUPLE}/include
    cp ${ANDROID_NDK}/platforms/android-24/arch-${PLATFORM_SHORT_ARCH}/usr/include/uchar.h \
        contrib/${TARGET_TUPLE}/include/uchar.h
fi

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

export USE_FFMPEG=1
ANDROID_ABI=${ANDROID_ABI} ANDROID_API=android-${ANDROID_API} \
    ../bootstrap --host=${TARGET_TUPLE} ${VLC_BOOTSTRAP_ARGS}
checkfail "contribs: bootstrap failed"

# Some libraries have arm assembly which won't build in thumb mode
# We append -marm to the CFLAGS of these libs to disable thumb mode
[ ${ANDROID_ABI} = "armeabi-v7a" ] && echo "NOTHUMB := -marm" >> config.mak

echo "EXTRA_CFLAGS=${EXTRA_CFLAGS}" >> config.mak
echo "EXTRA_CXXFLAGS=${EXTRA_CXXFLAGS}" >> config.mak
echo "EXTRA_LDFLAGS=${EXTRA_LDFLAGS}" >> config.mak
echo "CC=${NDK_TOOLCHAIN_PATH}/clang" >> config.mak
echo "CXX=${NDK_TOOLCHAIN_PATH}/clang++" >> config.mak
echo "AR=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-ar" >> config.mak
echo "RANLIB=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-ranlib" >> config.mak
echo "LD=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-ld" >> config.mak

make $MAKEFLAGS fetch
checkfail "contribs: make fetch failed"

# gettext
which autopoint >/dev/null || make $MAKEFLAGS .gettext
#export the PATH
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

if [ ${ANDROID_API} = "21" ] ; then
    # android-21 has empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    export ac_cv_header_sys_shm_h=no
else
    # force nanf and uselocale using libandroid_support since it's present in libc++
    export ac_cv_lib_m_nanf=yes
    export ac_cv_func_uselocale=yes

    VLC_LDFLAGS="${VLC_LDFLAGS} -L${NDK_LIB_DIR} -landroid_support"
fi

if [ ! -e ./config.h -o "$RELEASE" = 1 ]; then
CFLAGS="${VLC_CFLAGS} ${EXTRA_CFLAGS}" \
CXXFLAGS="${VLC_CXXFLAGS} ${EXTRA_CFLAGS} ${EXTRA_CXXFLAGS}" \
CC="${CROSS_TOOLS}clang" \
CXX="${CROSS_TOOLS}clang++" \
NM="${CROSS_TOOLS}nm" \
STRIP="${CROSS_TOOLS}strip" \
RANLIB="${CROSS_TOOLS}ranlib" \
AR="${CROSS_TOOLS}ar" \
PKG_CONFIG_LIBDIR=$VLC_SRC_DIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
PKG_CONFIG_PATH=$VLC_SRC_DIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
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
OpenDemux OpenDemux__$name
CloseDemux CloseDemux__$name
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

################
# MEDIALIBRARY #
################

if [ ${BUILD_ML} = "1" ];then

if [ ! -d "${SRC_DIR}/medialibrary" ]; then
    mkdir "${SRC_DIR}/medialibrary"
fi

##########
# SQLITE #
##########

MEDIALIBRARY_MODULE_DIR=${SRC_DIR}/medialibrary
MEDIALIBRARY_BUILD_DIR=${MEDIALIBRARY_MODULE_DIR}/medialibrary
OUT_LIB_DIR=$MEDIALIBRARY_MODULE_DIR/jni/libs/${ANDROID_ABI}
SQLITE_RELEASE="sqlite-autoconf-3180200"
SQLITE_SHA1="47f3cb34d6919e1162ed85264917c9e42a455639"

if [ ! -d "${MEDIALIBRARY_MODULE_DIR}/${SQLITE_RELEASE}" ]; then
    echo -e "\e[1m\e[32msqlite source not found, downloading\e[0m"
    cd ${MEDIALIBRARY_MODULE_DIR}
    rm -rf ${MEDIALIBRARY_BUILD_DIR}/build-android*
    rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/libs
    rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/obj
    wget https://download.videolan.org/pub/contrib/sqlite/${SQLITE_RELEASE}.tar.gz
    if [ ! "`sha1sum ${SQLITE_RELEASE}.tar.gz`" = "${SQLITE_SHA1}  ${SQLITE_RELEASE}.tar.gz" ]; then
        echo "Wrong sha1 for ${SQLITE_RELEASE}.tar.gz"
        exit 1
    fi
    tar -xzf ${SQLITE_RELEASE}.tar.gz
    rm -f ${SQLITE_RELEASE}.tar.gz
fi
cd ${MEDIALIBRARY_MODULE_DIR}/${SQLITE_RELEASE}
if [ ! -d "build-$ANDROID_ABI" ]; then
    mkdir "build-$ANDROID_ABI";
fi;
cd "build-$ANDROID_ABI";

if [ ! -e ./config.status -o "$RELEASE" = 1 ]; then
../configure \
    --host=$TARGET_TUPLE \
    --disable-shared \
    CFLAGS="${VLC_CFLAGS} ${EXTRA_CFLAGS}" \
    CXXFLAGS="${VLC_CXXFLAGS} ${EXTRA_CFLAGS} ${EXTRA_CXXFLAGS}" \
    CC="clang" \
    CXX="clang++"
fi

make $MAKEFLAGS

cd ${SRC_DIR}
checkfail "sqlite build failed"

##############################
# FETCH MEDIALIBRARY SOURCES #
##############################

if [ ! -d "${MEDIALIBRARY_MODULE_DIR}/medialibrary" ]; then
    echo -e "\e[1m\e[32mmedialibrary source not found, cloning\e[0m"
    git clone http://code.videolan.org/videolan/medialibrary.git "${SRC_DIR}/medialibrary/medialibrary"
    checkfail "medialibrary source: git clone failed"
    cd ${MEDIALIBRARY_MODULE_DIR}/medialibrary
    git submodule update --init libvlcpp
else
    cd ${MEDIALIBRARY_MODULE_DIR}/medialibrary
    if ! git cat-file -e ${MEDIALIBRARY_HASH}; then
      git pull --rebase
      rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/libs
      rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/obj
    fi
fi
if [ "$RELEASE" = 1 ]; then
    git reset --hard ${MEDIALIBRARY_HASH}
fi
cd ${SRC_DIR}
echo -e "\e[1m\e[36mCFLAGS:            ${CFLAGS}\e[0m"
echo -e "\e[1m\e[36mEXTRA_CFLAGS:      ${EXTRA_CFLAGS}\e[0m"

#################
# Setup folders #
#################


#############
# CONFIGURE #
#############

cd ${MEDIALIBRARY_BUILD_DIR}

sed "s#@prefix@#${MEDIALIBRARY_MODULE_DIR}/medialibrary/libvlcpp#g" $SRC_DIR/pkgs/libvlcpp.pc.in > \
    $SRC_DIR/pkgs/libvlcpp.pc;
sed "s#@libdir@#$SRC_DIR/libvlc/jni/libs/$ANDROID_ABI#g" $SRC_DIR/pkgs/libvlc.pc.in > \
    $SRC_DIR/pkgs/libvlc.pc;
sed -i".backup" "s#@includedirs@#-I${SRC_DIR}/vlc/include \
-I${SRC_DIR}/vlc/build-android-$TARGET_TUPLE/include#g" $SRC_DIR/pkgs/libvlc.pc;

if [ ! -d "build-android-$ANDROID_ABI/" ]; then
    mkdir "build-android-$ANDROID_ABI/";
fi;
cd "build-android-$ANDROID_ABI/";

if [ "$RELEASE" = 1 ]; then
    MEDIALIBRARY_MODE=--disable-debug
fi
if [ ! -e ./config.h -o "$RELEASE" = 1 ]; then
../bootstrap
../configure \
    --host=$TARGET_TUPLE \
    --disable-shared \
    ${MEDIALIBRARY_MODE} \
    CFLAGS="${VLC_CFLAGS} ${EXTRA_CFLAGS}" \
    CXXFLAGS="${VLC_CXXFLAGS} ${EXTRA_CFLAGS} ${EXTRA_CXXFLAGS}" \
    CC="clang" \
    CXX="clang++" \
    NM="${CROSS_TOOLS}nm" \
    STRIP="${CROSS_TOOLS}strip" \
    RANLIB="${CROSS_TOOLS}ranlib" \
    PKG_CONFIG_LIBDIR="$SRC_DIR/pkgs/" \
    LIBJPEG_LIBS="-L$SRC_DIR/vlc/contrib/contrib-android-$TARGET_TUPLE/jpeg/.libs -ljpeg" \
    LIBJPEG_CFLAGS="-I$SRC_DIR/vlc/contrib/$TARGET_TUPLE/include/" \
    SQLITE_LIBS="-L$MEDIALIBRARY_MODULE_DIR/$SQLITE_RELEASE/build-$ANDROID_ABI/.libs -lsqlite3" \
    SQLITE_CFLAGS="-I$MEDIALIBRARY_MODULE_DIR/$SQLITE_RELEASE" \
    AR="${CROSS_TOOLS}ar"
checkfail "medialibrary: autoconf failed"
fi

############
# BUILDING #
############

echo -e "\e[1m\e[32mBuilding medialibrary\e[0m"
make $MAKEFLAGS

checkfail "medialibrary: make failed"

cd ${SRC_DIR}

MEDIALIBRARY_LDLIBS="-L${MEDIALIBRARY_BUILD_DIR}/build-android-$ANDROID_ABI/.libs -lmedialibrary \
-L$SRC_DIR/vlc/contrib/contrib-android-$TARGET_TUPLE/jpeg/.libs -ljpeg \
-L$MEDIALIBRARY_MODULE_DIR/$SQLITE_RELEASE/build-$ANDROID_ABI/.libs -lsqlite3"

if [ $ON_WINDOWS -eq 1 ]; then
    OSCMD=.cmd
fi

###########
# LINKING #
###########

fi # ${BUILD_ML} = "1"

echo -e "ndk-build vlc"

$ANDROID_NDK/ndk-build$OSCMD -C libvlc \
    APP_STL="c++_static" \
    APP_CPPFLAGS="-frtti -fexceptions" \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    VLC_BUILD_DIR="$VLC_SRC_DIR/$VLC_BUILD_DIR" \
    VLC_CONTRIB="$VLC_CONTRIB" \
    VLC_CONTRIB_LDFLAGS="$VLC_CONTRIB_LDFLAGS" \
    VLC_MODULES="$VLC_MODULES" \
    VLC_LDFLAGS="$VLC_LDFLAGS -latomic" \
    MEDIALIBRARY_LDLIBS="${MEDIALIBRARY_LDLIBS}" \
    MEDIALIBRARY_INCLUDE_DIR=${MEDIALIBRARY_BUILD_DIR}/include \
    APP_BUILD_SCRIPT=jni/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    NDK_PROJECT_PATH=jni \
    NDK_TOOLCHAIN_VERSION=clang \
    NDK_DEBUG=${NDK_DEBUG} \
    BUILD_ML=${BUILD_ML}

checkfail "ndk-build failed"

$ANDROID_NDK/ndk-build$OSCMD -C libvlc \
    APP_BUILD_SCRIPT=jni/loader/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    NDK_PROJECT_PATH=jni/loader \
    NDK_TOOLCHAIN_VERSION=clang

checkfail "ndk-build failed for libvlc"

$ANDROID_NDK/ndk-build$OSCMD -C libvlc \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    ANDROID_SYS_HEADERS="$ANDROID_SYS_HEADERS" \
    LIBIOMX_LIBS="$LIBIOMX_LIBS" \
    LIBANW_LIBS="$LIBANW_LIBS" \
    APP_BUILD_SCRIPT=private_libs/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    TARGET_TUPLE=$TARGET_TUPLE \
    NDK_PROJECT_PATH=private_libs \
    NDK_TOOLCHAIN_VERSION=clang 2>/dev/null

echo "Dumping dbg symbols info ${OUT_DBG_DIR}"

cd ${SRC_DIR}
OUT_DBG_DIR=.dbg/${ANDROID_ABI}

mkdir -p $OUT_DBG_DIR
cp -a libvlc/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
