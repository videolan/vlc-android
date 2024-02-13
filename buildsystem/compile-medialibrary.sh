#!/bin/sh

set -e

#############
# ARGUMENTS #
#############

MEDIALIBRARY_HASH=8c56e26c625d757994cffeea84d2a0a2e6033dee

while [ $# -gt 0 ]; do
  case $1 in
  help | --help)
    echo "Use -a to set the ARCH"
    echo "Use --release to build in release mode"
    exit 1
    ;;
  a | -a)
    ANDROID_ABI=$2
    shift
    ;;
  release | --release)
    RELEASE=1
    ;;
  esac
  shift
done

SRC_DIR=$PWD
AVLC_SOURCED=1 . libvlcjni/buildsystem/compile-libvlc.sh

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
SQLITE_RELEASE="sqlite-autoconf-3340100"
SQLITE_SHA1="c20286e11fe5c2e3712ce74890e1692417de6890"

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
  tar -xozf ${SQLITE_RELEASE}.tar.gz
  rm -f ${SQLITE_RELEASE}.tar.gz
  cd ${SQLITE_RELEASE}
  patch -p1 < ${SRC_DIR}/buildsystem/patches/sqlite/sqlite-no-shell.patch
  autoreconf -vif
fi
cd ${MEDIALIBRARY_MODULE_DIR}/${SQLITE_RELEASE}
if [ ! -d "build-$ANDROID_ABI" ]; then
    mkdir "build-$ANDROID_ABI";
fi;
cd "build-$ANDROID_ABI";

if [ ! -e ./config.status -o "$RELEASE" = "1" ]; then
  ../configure \
    --host=$TARGET_TUPLE \
    --prefix=${SRC_DIR}/medialibrary/prefix/${TARGET_TUPLE} \
    --disable-shell \
    --disable-shared \
    CFLAGS="${VLC_CFLAGS}" \
    CXXFLAGS="${VLC_CFLAGS} ${VLC_CXXFLAGS}" \
    CC="${CROSS_CLANG}" \
    CXX="${CROSS_CLANG}++"
fi

make $MAKEFLAGS
avlc_checkfail "sqlite build failed"

make install
avlc_checkfail "sqlite installation failed"

cd ${SRC_DIR}

##############################
# FETCH MEDIALIBRARY SOURCES #
##############################

# Source directory doesn't exist: checking out the TAG.
# The CI will always use this block
if [ ! -d "${MEDIALIBRARY_MODULE_DIR}/medialibrary" ]; then
  echo -e "\e[1m\e[32mmedialibrary source not found, cloning\e[0m"
  git clone http://code.videolan.org/videolan/medialibrary.git "${MEDIALIBRARY_MODULE_DIR}/medialibrary"
  avlc_checkfail "medialibrary source: git clone failed"
  cd ${MEDIALIBRARY_MODULE_DIR}/medialibrary
  git reset --hard ${MEDIALIBRARY_HASH}
  avlc_checkfail "medialibrary source: Failed to switch to expected commit hash"
  git submodule update --init libvlcpp
  # TODO: remove when switching to VLC 4.0
  cd libvlcpp
  git am ${SRC_DIR}/buildsystem/patches/libvlcpp/*
elif [ "$RESET" = "1" ]; then
    cd ${SRC_DIR}/medialibrary/medialibrary
    git fetch --all --tags
    git reset --hard ${MEDIALIBRARY_HASH}
    avlc_checkfail "medialibrary source: Failed to switch to expected commit hash"
    git submodule update --init libvlcpp
    # TODO: remove when switching to VLC 4.0
    cd libvlcpp
    git am ${SRC_DIR}/buildsystem/patches/libvlcpp/*
fi
cd ${SRC_DIR}

#################
# Setup folders #
#################


#############
# CONFIGURE #
#############

if [ "$RELEASE" = "1" ]; then
  MEDIALIBRARY_NDEBUG=true
  MEDIALIBRARY_OPTIMIZATION=3
else
  MEDIALIBRARY_NDEBUG=false
  MEDIALIBRARY_OPTIMIZATION=0
fi

cd ${MEDIALIBRARY_BUILD_DIR}

if [ "$RELEASE" = "1" ]; then
    git describe --exact-match HEAD > /dev/null || \
        avlc_checkfail "Release builds must use tags"
fi

if [ ! -d "build-android-$ANDROID_ABI/" -o ! -f "build-android-$ANDROID_ABI/build.ninja" ]; then
    PKG_CONFIG_LIBDIR="$LIBVLCJNI_SRC_DIR/vlc/build-android-${TARGET_TUPLE}/install/lib/pkgconfig" \
    PKG_CONFIG_PATH="$SRC_DIR/medialibrary/prefix/${TARGET_TUPLE}/lib/pkgconfig:$LIBVLCJNI_SRC_DIR/vlc/contrib/$TARGET_TUPLE/lib/pkgconfig/" \
    meson \
        -Ddebug=true \
        -Doptimization=${MEDIALIBRARY_OPTIMIZATION} \
        -Db_ndebug=${MEDIALIBRARY_NDEBUG} \
        -Ddefault_library=static \
        --cross-file ${SRC_DIR}/buildsystem/crossfiles/${ANDROID_ABI}-ndk${REL}.crossfile \
        -Dlibjpeg_prefix="$LIBVLCJNI_SRC_DIR/vlc/contrib/$TARGET_TUPLE/" \
        -Dtests=disabled \
        -Dforce_attachment_api=true \
        -Dlibvlc=enabled \
        build-android-${ANDROID_ABI}
fi

avlc_checkfail "medialibrary: meson failed"

############
# BUILDING #
############

echo -e "\e[1m\e[32mBuilding medialibrary\e[0m"
cd "build-android-$ANDROID_ABI/";
ninja

avlc_checkfail "medialibrary: build failed"

cd ${SRC_DIR}

MEDIALIBRARY_LDLIBS="-L$LIBVLCJNI_SRC_DIR/libvlc/jni/libs/${ANDROID_ABI}/ -lvlc \
-L$LIBVLCJNI_SRC_DIR/vlc/contrib/$TARGET_TUPLE/lib -ljpeg \
-L${NDK_LIB_DIR} -lc++abi"

$NDK_BUILD -C medialibrary \
  APP_STL="c++_shared" \
  LOCAL_CPP_FEATURES="rtti exceptions" \
  APP_BUILD_SCRIPT=jni/Android.mk \
  APP_PLATFORM=android-${ANDROID_API} \
  APP_ABI=${ANDROID_ABI} \
  NDK_PROJECT_PATH=jni \
  NDK_TOOLCHAIN_VERSION=clang \
  MEDIALIBRARY_LDLIBS="${MEDIALIBRARY_LDLIBS}" \
  MEDIALIBRARY_INCLUDE_DIR=${MEDIALIBRARY_BUILD_DIR}/include \
  NDK_DEBUG=${NDK_DEBUG} \
  SQLITE_RELEASE=$SQLITE_RELEASE

avlc_checkfail "nkd-build medialibrary failed"
