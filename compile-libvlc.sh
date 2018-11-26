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

MEDIALIBRARY_HASH=740e21f

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
    ARM:     (armeabi-v7a|arm)
    ARM64:   (arm64-v8a|arm64)
    X86:     x86, x86_64"
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
    --disable-medialibrary \
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

SRC_DIR=$PWD

. ./build-common

###########################
# Build buildsystem tools #
###########################

export PATH="`pwd`/vlc/extras/tools/build/bin:$PATH"
echo "Building tools"
cd vlc/extras/tools
./bootstrap
checkfail "buildsystem tools: bootstrap failed"
make $MAKEFLAGS
checkfail "buildsystem tools: make failed"
make $MAKEFLAGS .gas || make $MAKEFLAGS .buildgas
checkfail "buildsystem tools: make failed"
cd ../../..

VLC_SRC_DIR="$SRC_DIR/vlc"
VLC_CONTRIB="$VLC_SRC_DIR/contrib/$TARGET_TUPLE"

cd vlc

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

# fix modplug endianess check (narrowing error)
export ac_cv_c_bigendian=no

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

if [ ${ANDROID_API} -lt "26" ]; then
    # android APIs < 26 have empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    export ac_cv_header_sys_shm_h=no
fi

if [ ${ANDROID_API} -lt "21" ] ; then
    # force uselocale using libandroid_support since it's present in libc++
    export ac_cv_func_uselocale=yes

    VLC_LDFLAGS="${VLC_LDFLAGS} -L${NDK_LIB_DIR} -landroid_support"
fi

# always use fixups for search.h and tdestroy
export ac_cv_header_search_h=no
export ac_cv_func_tdestroy=no
export ac_cv_func_tfind=no

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

VLC_MODULES=$(find_modules ${REDEFINED_VLC_MODULES_DIR})
VLC_CONTRIB_LDFLAGS=`for i in $(/bin/ls $VLC_CONTRIB/lib/pkgconfig/*.pc); do PKG_CONFIG_PATH="$VLC_CONTRIB/lib/pkgconfig/" pkg-config --libs $i; done |xargs`
echo -e "ndk-build vlc"

$ANDROID_NDK/ndk-build$OSCMD -C libvlc \
    APP_STL="c++_shared" \
    APP_CPPFLAGS="-frtti -fexceptions" \
    VLC_SRC_DIR="$VLC_SRC_DIR" \
    VLC_BUILD_DIR="$VLC_SRC_DIR/$VLC_BUILD_DIR" \
    VLC_CONTRIB="$VLC_CONTRIB" \
    VLC_CONTRIB_LDFLAGS="$VLC_CONTRIB_LDFLAGS" \
    VLC_MODULES="$VLC_MODULES" \
    VLC_LDFLAGS="$VLC_LDFLAGS -latomic" \
    APP_BUILD_SCRIPT=jni/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    NDK_PROJECT_PATH=jni \
    NDK_TOOLCHAIN_VERSION=clang \
    NDK_DEBUG=${NDK_DEBUG}

checkfail "ndk-build libvlc failed"

cd ${SRC_DIR}
OUT_DBG_DIR=.dbg/${ANDROID_ABI}
echo "Dumping dbg symbols info ${OUT_DBG_DIR}"

mkdir -p $OUT_DBG_DIR
cp -a libvlc/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
