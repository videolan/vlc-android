#!/bin/sh

#############
# ARGUMENTS #
#############

MEDIALIBRARY_HASH=312cd83e3444e10b77e82b1cb4e1cf6393f8948e

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

AVLC_SOURCED=1 . buildsystem/compile-libvlc.sh

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
  patch -1 ${SRC_DIR}/buildsystem/sqlite/sqlite-no-shell.patch
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

if [ ! -d "${MEDIALIBRARY_MODULE_DIR}/medialibrary" ]; then
  echo -e "\e[1m\e[32mmedialibrary source not found, cloning\e[0m"
  git clone http://code.videolan.org/videolan/medialibrary.git "${SRC_DIR}/medialibrary/medialibrary"
  avlc_checkfail "medialibrary source: git clone failed"
  cd ${MEDIALIBRARY_MODULE_DIR}/medialibrary
  #    git checkout 0.5.x
  git reset --hard ${MEDIALIBRARY_HASH}
  git submodule update --init libvlcpp
else
  cd ${MEDIALIBRARY_MODULE_DIR}/medialibrary
  if ! git cat-file -e ${MEDIALIBRARY_HASH}; then
    git pull --rebase
    rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/libs
    rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/obj
  fi
fi
cd ${SRC_DIR}

#################
# Setup folders #
#################


#############
# CONFIGURE #
#############

if [ "$RELEASE" = "1" ]; then
  MEDIALIBRARY_MODE=release
else
  MEDIALIBRARY_MODE=debug
fi

cd ${MEDIALIBRARY_BUILD_DIR}

if [ ! -d "build-android-$ANDROID_ABI/" -o ! -f "build-android-$ANDROID_ABI/build.ninja" ]; then
    PKG_CONFIG_LIBDIR="$SRC_DIR/vlc/build-android-${TARGET_TUPLE}/install/lib/pkgconfig" \
    PKG_CONFIG_PATH="$SRC_DIR/medialibrary/prefix/${TARGET_TUPLE}/lib/pkgconfig:$SRC_DIR/vlc/contrib/$TARGET_TUPLE/lib/pkgconfig/" \
    meson --buildtype=${MEDIALIBRARY_MODE} \
        -Ddefault_library=static \
        --cross-file ${SRC_DIR}/buildsystem/crossfiles/${ANDROID_ABI}.crossfile \
        -Dlibjpeg_prefix="$SRC_DIR/vlc/contrib/$TARGET_TUPLE/" \
        -Dtests=disabled \
        -Dforce_attachment_api=true \
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

MEDIALIBRARY_LDLIBS="$VLC_OUT_LDLIBS \
-L${MEDIALIBRARY_BUILD_DIR}/build-android-$ANDROID_ABI/src/ -lmedialibrary \
-L$SRC_DIR/vlc/contrib/contrib-android-$TARGET_TUPLE/jpeg/.libs -ljpeg \
-L$MEDIALIBRARY_MODULE_DIR/$SQLITE_RELEASE/build-$ANDROID_ABI/.libs -lsqlite3 \
-L${NDK_LIB_DIR} -lc++abi ${NDK_LIB_UNWIND}"

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
  NDK_DEBUG=${NDK_DEBUG}

avlc_checkfail "nkd-build medialibrary failed"
