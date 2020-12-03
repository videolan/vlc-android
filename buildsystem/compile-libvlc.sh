#!/bin/sh

#############
# ARGUMENTS #
#############

AVLC_RELEASE=$RELEASE
# Indicated if prebuilt contribs package
# should be created
AVLC_MAKE_PREBUILT_CONTRIBS=0
# Indicates that prebuit contribs should be
# used instead of building the contribs from source
AVLC_USE_PREBUILT_CONTRIBS=0
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
        release|--release)
            AVLC_RELEASE=1
            ;;
        --package-contribs)
            AVLC_MAKE_PREBUILT_CONTRIBS=1
            ;;
        --with-prebuilt-contribs)
            AVLC_USE_PREBUILT_CONTRIBS=1
            ;;
    esac
    shift
done

# Validate arguments
if [ "$AVLC_MAKE_PREBUILT_CONTRIBS" -gt "0" ] &&
   [ "$AVLC_USE_PREBUILT_CONTRIBS" -gt "0" ]; then
    echo >&2 "ERROR: The --package-contribs and --with-prebuilt-contribs options"
    echo >&2 "       can not be used together."
    exit 1
fi

# Make in //
if [ -z "$MAKEFLAGS" ]; then
    UNAMES=$(uname -s)
    MAKEFLAGS=
    if which nproc >/dev/null; then
        MAKEFLAGS=-j$(nproc)
    elif [ "$UNAMES" = "Darwin" ] && which sysctl >/dev/null; then
        MAKEFLAGS=-j$(sysctl -n machdep.cpu.thread_count)
    fi
fi

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
    CLANG_PREFIX=${TARGET_TUPLE}
    PLATFORM_SHORT_ARCH="x86"
elif [ "${ANDROID_ABI}" = "x86_64" ] ; then
    TARGET_TUPLE="x86_64-linux-android"
    CLANG_PREFIX=${TARGET_TUPLE}
    PLATFORM_SHORT_ARCH="x86_64"
    HAVE_64=1
elif [ "${ANDROID_ABI}" = "arm64-v8a" ] ; then
    TARGET_TUPLE="aarch64-linux-android"
    CLANG_PREFIX=${TARGET_TUPLE}
    HAVE_ARM=1
    HAVE_64=1
    PLATFORM_SHORT_ARCH="arm64"
elif [ "${ANDROID_ABI}" = "armeabi-v7a" ] ; then
    TARGET_TUPLE="arm-linux-androideabi"
    CLANG_PREFIX="armv7a-linux-androideabi"
    HAVE_ARM=1
    PLATFORM_SHORT_ARCH="arm"
else
    echo "Please pass the ANDROID ABI to the correct architecture, using
                compile-libvlc.sh -a ARCH
    ARM:     (armeabi-v7a|arm)
    ARM64:   (arm64-v8a|arm64)
    X86:     x86, x86_64"
    exit 1
fi

# try to detect NDK version
REL=$(grep -o '^Pkg.Revision.*[0-9]*.*' $ANDROID_NDK/source.properties |cut -d " " -f 3 | cut -d "." -f 1)

if [ "$REL" -eq 21 ]; then
    if [ "${HAVE_64}" = 1 ]; then
        ANDROID_API=21
    else
        ANDROID_API=17
    fi
else
    echo "NDK v21 needed, cf. https://developer.android.com/ndk/downloads/"
    exit 1
fi

############
# VLC PATH #
############
SRC_DIR=$PWD
if [ -f $SRC_DIR/src/libvlc.h ];then
    VLC_SRC_DIR="$SRC_DIR"
elif [ -d $SRC_DIR/vlc ];then
    VLC_SRC_DIR=$SRC_DIR/vlc
else
    echo "Could not find vlc sources"
    exit 1
fi

VLC_BUILD_DIR=$(realpath $VLC_SRC_DIR/build-android-${TARGET_TUPLE})
VLC_OUT_PATH="$VLC_BUILD_DIR/ndk"
mkdir -p $VLC_OUT_PATH
VLC_OUT_LDLIBS="-L$VLC_OUT_PATH/libs/${ANDROID_ABI} -lvlc"

#################
# NDK TOOLCHAIN #
#################
host_tag=""
case $(uname | tr '[:upper:]' '[:lower:]') in
  linux*)   host_tag="linux" ;;
  darwin*)  host_tag="darwin" ;;
  msys*)    host_tag="windows" ;;
  *)        echo "host OS not handled"; exit 1 ;;
esac
NDK_TOOLCHAIN_DIR=${ANDROID_NDK}/toolchains/llvm/prebuilt/${host_tag}-x86_64
NDK_TOOLCHAIN_PATH=${NDK_TOOLCHAIN_DIR}/bin
# Add the NDK toolchain to the PATH, needed both for contribs and for building
# stub libraries
CROSS_TOOLS=${NDK_TOOLCHAIN_PATH}/${TARGET_TUPLE}-
CROSS_CLANG=${NDK_TOOLCHAIN_PATH}/${CLANG_PREFIX}${ANDROID_API}-clang

export PATH="${NDK_TOOLCHAIN_PATH}:${PATH}"
NDK_BUILD=$ANDROID_NDK/ndk-build
if [ ! -z "$MSYSTEM_PREFIX" ] ; then
    # The make.exe and awk.exe from the toolchain don't work in msys
    export PATH="$MSYSTEM_PREFIX/bin:/usr/bin:${NDK_TOOLCHAIN_PATH}:${PATH}"
    NDK_BUILD=$NDK_BUILD.cmd
fi

##########
# CFLAGS #
##########

if [ "$NO_OPTIM" = "1" ];
then
     VLC_CFLAGS="-g -O0"
else
     VLC_CFLAGS="-g -O2"
fi

# cf. GLOBAL_CFLAGS from ${ANDROID_NDK}/build/core/default-build-commands.mk
VLC_CFLAGS="${VLC_CFLAGS} -fPIC -fdata-sections -ffunction-sections -funwind-tables \
 -fstack-protector-strong -no-canonical-prefixes"
VLC_CXXFLAGS="-fexceptions -frtti"

# Release or not?
if [ "$AVLC_RELEASE" = 1 ]; then
    VLC_CFLAGS="${VLC_CFLAGS} -DNDEBUG "
    NDK_DEBUG=0
else
    NDK_DEBUG=1
fi

###############
# DISPLAY ABI #
###############

echo "ABI:        $ANDROID_ABI"
echo "API:        $ANDROID_API"
echo "PATH:       $PATH"
echo "VLC_CFLAGS:        ${VLC_CFLAGS}"
echo "VLC_CXXFLAGS:      ${VLC_CXXFLAGS}"

if [ -z "$ANDROID_NDK" ]; then
    echo "Please set the ANDROID_NDK environment variable with its path."
    exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
    echo "Please pass the ANDROID ABI to the correct architecture, using
                compile-libvlc.sh -a ARCH
    ARM:     (armeabi-v7a|arm)
    ARM64:   (arm64-v8a|arm64)
    X86:     x86, x86_64"
    exit 1
fi

avlc_checkfail()
{
    if [ ! $? -eq 0 ];then
        echo "$1"
        exit 1
    fi
}

avlc_find_modules()
{
    echo "$(find $1 -name 'lib*plugin.a' | grep -vE "lib(${blacklist_regexp})_plugin.a" | tr '\n' ' ')"
}

avlc_get_symbol()
{
    echo "$1" | grep vlc_entry_$2|cut -d" " -f 3
}

avlc_gen_pc_file()
{
echo "Generating $1 pkg-config file"
echo "Name: $1
Description: $1
Version: $2
Libs: -l$1
Cflags:" > contrib/${TARGET_TUPLE}/lib/pkgconfig/$(echo $1|tr 'A-Z' 'a-z').pc
}

avlc_pkgconfig()
{
    # Enforce pkg-config files coming from VLC contribs
    PKG_CONFIG_PATH="$VLC_CONTRIB/lib/pkgconfig/" \
    PKG_CONFIG_LIBDIR="$VLC_CONTRIB/lib/pkgconfig/" \
    pkg-config "$@"
}

avlc_build()
{
###########################
# VLC BOOTSTRAP ARGUMENTS #
###########################

VLC_BOOTSTRAP_ARGS="\
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
    --enable-smb2 \
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
    --disable-medialibrary \
"

###########################
# VLC CONFIGURE ARGUMENTS #
###########################

VLC_CONFIGURE_ARGS="\
    --with-pic \
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
    --enable-bluray \
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
    --disable-vnc \
    --enable-jpeg \
    --enable-smb2 \
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

###########################
# Build buildsystem tools #
###########################

export PATH="$VLC_SRC_DIR/extras/tools/build/bin:$PATH"
echo "Building tools"
cd $VLC_SRC_DIR/extras/tools
./bootstrap
avlc_checkfail "buildsystem tools: bootstrap failed"
make $MAKEFLAGS
avlc_checkfail "buildsystem tools: make failed"
make $MAKEFLAGS .gas || make $MAKEFLAGS .buildgas
avlc_checkfail "buildsystem tools: make failed"
cd ../../..

VLC_CONTRIB="$VLC_SRC_DIR/contrib/$TARGET_TUPLE"

cd $VLC_SRC_DIR

#############
# BOOTSTRAP #
#############

if [ ! -f configure ]; then
    echo "Bootstraping"
    ./bootstrap
    avlc_checkfail "vlc: bootstrap failed"
fi

############
# Contribs #
############

echo "Building the contribs"
mkdir -p contrib/contrib-android-${TARGET_TUPLE}

mkdir -p contrib/${TARGET_TUPLE}/lib/pkgconfig
avlc_gen_pc_file EGL 1.1
avlc_gen_pc_file GLESv2 2

cd contrib/contrib-android-${TARGET_TUPLE}

# TODO: VLC 4.0 won't rm config.mak after each call to bootstrap. Move it just
# before ">> config.make" when switching to VLC 4.0
rm -f config.mak

export USE_FFMPEG=1
ANDROID_ABI=${ANDROID_ABI} ANDROID_API=${ANDROID_API} \
    ../bootstrap --host=${TARGET_TUPLE} ${VLC_BOOTSTRAP_ARGS}
avlc_checkfail "contribs: bootstrap failed"

if [ "$AVLC_USE_PREBUILT_CONTRIBS" -gt "0" ]; then
    # Fetch prebuilt contribs
    if [ -z "$VLC_PREBUILT_CONTRIBS_URL" ]; then
        make prebuilt
        avlc_checkfail "Fetching prebuilt contribs failed"
    else
        make prebuilt PREBUILT_URL="$VLC_PREBUILT_CONTRIBS_URL"
        avlc_checkfail "Fetching prebuilt contribs from ${VLC_PREBUILT_CONTRIBS_URL} failed"
    fi
    make .luac
else
    # Some libraries have arm assembly which won't build in thumb mode
    # We append -marm to the CFLAGS of these libs to disable thumb mode
    [ ${ANDROID_ABI} = "armeabi-v7a" ] && echo "NOTHUMB := -marm" >> config.mak

    echo "EXTRA_CFLAGS=${VLC_CFLAGS}" >> config.mak
    echo "EXTRA_CXXFLAGS=${VLC_CXXFLAGS}" >> config.mak
    echo "CC=${CROSS_CLANG}" >> config.mak
    echo "CXX=${CROSS_CLANG}++" >> config.mak
    echo "AR=${CROSS_TOOLS}ar" >> config.mak
    echo "AS=${CROSS_TOOLS}as" >> config.mak
    echo "RANLIB=${CROSS_TOOLS}ranlib" >> config.mak
    echo "LD=${CROSS_TOOLS}ld" >> config.mak

    # fix modplug endianess check (narrowing error)
    export ac_cv_c_bigendian=no

    make $MAKEFLAGS fetch
    avlc_checkfail "contribs: make fetch failed"

    # gettext
    which autopoint >/dev/null || make $MAKEFLAGS .gettext
    #export the PATH
    # Make
    make $MAKEFLAGS
    avlc_checkfail "contribs: make failed"

    # Make prebuilt contribs package
    if [ "$AVLC_MAKE_PREBUILT_CONTRIBS" -gt "0" ]; then
        make package
        avlc_checkfail "Creating prebuilt contribs package failed"
    fi
fi

cd ../../

mkdir -p $VLC_BUILD_DIR && cd $VLC_BUILD_DIR

#############
# CONFIGURE #
#############

if [ ${ANDROID_API} -lt "26" ]; then
    # android APIs < 26 have empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    export ac_cv_header_sys_shm_h=no
fi

if [ ${ANDROID_API} -lt "21" ] ; then
    # force uselocale using libandroid_support since it's present in libc++
    export ac_cv_func_uselocale=yes

    VLC_LDFLAGS="-landroid_support"
fi

# always use fixups for search.h and tdestroy
export ac_cv_header_search_h=no
export ac_cv_func_tdestroy=no
export ac_cv_func_tfind=no

if [ ! -e ./config.h -o "$AVLC_RELEASE" = 1 ]; then
    VLC_CONFIGURE_DEBUG=""
    if [ ! "$AVLC_RELEASE" = 1 ]; then
        VLC_CONFIGURE_DEBUG="--enable-debug --disable-branch-protection"
    fi

    CFLAGS="${VLC_CFLAGS}" \
    CXXFLAGS="${VLC_CFLAGS} ${VLC_CXXFLAGS}" \
    CC="${CROSS_CLANG}" \
    CXX="${CROSS_CLANG}++" \
    NM="${CROSS_TOOLS}nm" \
    STRIP="${CROSS_TOOLS}strip" \
    RANLIB="${CROSS_TOOLS}ranlib" \
    AR="${CROSS_TOOLS}ar" \
    AS="${CROSS_TOOLS}as" \
    PKG_CONFIG_LIBDIR=$VLC_SRC_DIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
    PKG_CONFIG_PATH=$VLC_SRC_DIR/contrib/$TARGET_TUPLE/lib/pkgconfig \
    PATH=../contrib/bin:$PATH \
    sh ../configure --host=$TARGET_TUPLE --build=x86_64-unknown-linux \
        --with-contrib=${VLC_SRC_DIR}/contrib/${TARGET_TUPLE} \
        --prefix=${VLC_BUILD_DIR}/install/ \
        ${EXTRA_PARAMS} ${VLC_CONFIGURE_ARGS} ${VLC_CONFIGURE_DEBUG}
    avlc_checkfail "vlc: configure failed"
fi

############
# BUILDING #
############

echo "Building"
make $MAKEFLAGS
avlc_checkfail "vlc: make failed"
make install
avlc_checkfail "vlc: make install failed"

cd $SRC_DIR

##################
# libVLC modules #
##################

REDEFINED_VLC_MODULES_DIR=${VLC_BUILD_DIR}/install/lib/vlc/plugins
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

VLC_MODULES=$(avlc_find_modules ${VLC_BUILD_DIR}/modules)
DEFINITION="";
BUILTINS="const void *vlc_static_modules[] = {\n";
for file in $VLC_MODULES; do
    outfile=${REDEFINED_VLC_MODULES_DIR}/$(basename $file)
    name=$(echo $file | sed 's/.*\.libs\/lib//' | sed 's/_plugin\.a//');
    symbols=$("${CROSS_TOOLS}nm" -g $file)

    # assure that all modules have differents symbol names
    entry=$(avlc_get_symbol "$symbols" _)
    copyright=$(avlc_get_symbol "$symbols" copyright)
    license=$(avlc_get_symbol "$symbols" license)
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
    avlc_checkfail "objcopy failed"

    DEFINITION=$DEFINITION"int vlc_entry__$name (int (*)(void *, void *, int, ...), void *);\n";
    BUILTINS="$BUILTINS vlc_entry__$name,\n";
done;
BUILTINS="$BUILTINS NULL\n};\n"; \
printf "/* Autogenerated from the list of modules */\n#include <unistd.h>\n$DEFINITION\n$BUILTINS\n" > $VLC_OUT_PATH/libvlcjni-modules.c

DEFINITION=""
BUILTINS="const void *libvlc_functions[] = {\n";
for func in $(cat $VLC_SRC_DIR/lib/libvlc.sym)
do
    DEFINITION=$DEFINITION"int $func(void);\n";
    BUILTINS="$BUILTINS $func,\n";
done
BUILTINS="$BUILTINS NULL\n};\n"; \
printf "/* Autogenerated from the list of modules */\n#include <unistd.h>\n$DEFINITION\n$BUILTINS\n" > $VLC_OUT_PATH/libvlcjni-symbols.c

rm ${REDEFINED_VLC_MODULES_DIR}/syms

###########################
# NDK-Build for libvlc.so #
###########################

VLC_MODULES=$(avlc_find_modules ${REDEFINED_VLC_MODULES_DIR})
VLC_CONTRIB_LDFLAGS=$(for i in $(/bin/ls $VLC_CONTRIB/lib/pkgconfig/*.pc); do avlc_pkgconfig --libs $i; done |xargs)

# Lua contrib doesn't expose a pkg-config file with libvlc 3.x and is
# not probed by the previous command in VLC_CONTRIB_LDFLAGS, so probe
# whether it was detected or add it manually to the LDFLAGS.
if ! avlc_pkgconfig --exists lua; then
    VLC_CONTRIB_LDFLAGS="$VLC_CONTRIB_LDFLAGS '$VLC_CONTRIB/lib/liblua.a'"
fi

echo -e "ndk-build vlc"

touch $VLC_OUT_PATH/dummy.cpp

# This is ugly but it's better to use the linker from ndk-build that will use
# the proper linkflags depending on ABI/API
rm -rf $VLC_OUT_PATH/Android.mk
cat << 'EOF' > $VLC_OUT_PATH/Android.mk
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := libvlc
LOCAL_SRC_FILES := libvlcjni-modules.c libvlcjni-symbols.c dummy.cpp
LOCAL_LDFLAGS := -L$(VLC_CONTRIB)/lib
LOCAL_LDLIBS := \
    $(VLC_MODULES) \
    $(VLC_BUILD_DIR)/lib/.libs/libvlc.a \
    $(VLC_BUILD_DIR)/src/.libs/libvlccore.a \
    $(VLC_BUILD_DIR)/compat/.libs/libcompat.a \
    $(VLC_CONTRIB_LDFLAGS) \
    -ldl -lz -lm -llog \
    -la52 -ljpeg \
    $(VLC_LDFLAGS)
LOCAL_CXXFLAGS := -std=c++11
include $(BUILD_SHARED_LIBRARY)
EOF

$NDK_BUILD -C $VLC_OUT_PATH/.. \
    APP_STL="c++_shared" \
    APP_CPPFLAGS="-frtti -fexceptions" \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    VLC_BUILD_DIR="$VLC_BUILD_DIR" \
    VLC_CONTRIB="$VLC_CONTRIB" \
    VLC_CONTRIB_LDFLAGS="$VLC_CONTRIB_LDFLAGS" \
    VLC_MODULES="$VLC_MODULES" \
    VLC_LDFLAGS="$VLC_LDFLAGS" \
    APP_BUILD_SCRIPT=ndk/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    NDK_PROJECT_PATH=ndk \
    NDK_TOOLCHAIN_VERSION=clang \
    NDK_DEBUG=${NDK_DEBUG}
avlc_checkfail "ndk-build libvlc failed"

# Remove gdbserver to avoid conflict with libvlcjni.so debug options
rm -f $VLC_OUT_PATH/libs/${ANDROID_ABI}/gdb*

} # avlc_build()

if [ "$AVLC_SOURCED" != "1" ]; then
    avlc_build
fi
