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

MEDIALIBRARY_HASH=0403774

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

SRC_DIR=$PWD

. ./build-common


################
# MEDIALIBRARY #
################

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

MEDIALIBRARY_LDLIBS="-L$SRC_DIR/libvlc/jni/libs/$ANDROID_ABI -lvlc \
-L${MEDIALIBRARY_BUILD_DIR}/build-android-$ANDROID_ABI/.libs -lmedialibrary \
-L$SRC_DIR/vlc/contrib/contrib-android-$TARGET_TUPLE/jpeg/.libs -ljpeg \
-L$MEDIALIBRARY_MODULE_DIR/$SQLITE_RELEASE/build-$ANDROID_ABI/.libs -lsqlite3 \
-L${NDK_LIB_DIR} -lc++abi ${NDK_LIB_UNWIND}"

if [ $ON_WINDOWS -eq 1 ]; then
    OSCMD=.cmd
fi

$ANDROID_NDK/ndk-build$OSCMD -C medialibrary \
    APP_STL="c++_shared" \
    LOCAL_CPP_FEATURES="rtti exceptions" \
    APP_BUILD_SCRIPT=jni/Android.mk \
    APP_PLATFORM=android-${ANDROID_API} \
    APP_ABI=${ANDROID_ABI} \
    NDK_PROJECT_PATH=jni \
    NDK_TOOLCHAIN_VERSION=clang \
    MEDIALIBRARY_LDLIBS="${MEDIALIBRARY_LDLIBS}" \
    MEDIALIBRARY_INCLUDE_DIR=${MEDIALIBRARY_BUILD_DIR}/include \
    NDK_DEBUG=${NDK_DEBUG}

echo "Dumping dbg symbols info ${OUT_DBG_DIR}"

cd ${SRC_DIR}
OUT_DBG_DIR=.dbg/${ANDROID_ABI}

mkdir -p $OUT_DBG_DIR
cp -a medialibrary/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
