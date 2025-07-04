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
    echo "Use --reset to reset code from git"
    exit 1
    ;;
  a | -a)
    ANDROID_ABI=$2
    shift
    ;;
  release | --release)
    RELEASE=1
    ;;
  reset | --reset)
    RESET=1
    ;;
  esac
  shift
done

SRC_DIR=$PWD
# gets TARGET_TUPLE / ANDROID_API / CLANG_PREFIX / CROSS_CLANG / VLC_CFLAGS / VLC_CXXFLAGS / NDK_DEBUG / MAKEFLAGS / LIBVLCJNI_SRC_DIR
AVLC_SOURCED=1 . libvlcjni/buildsystem/compile-libvlc.sh

################
# MEDIALIBRARY #
################

if [ ! -d "${SRC_DIR}/medialibrary" ]; then
  mkdir "${SRC_DIR}/medialibrary"
fi

MEDIALIBRARY_PREFIX="${SRC_DIR}/medialibrary/prefix/android-${ANDROID_API}-${ANDROID_ABI}"

##########
# SQLITE #
##########

MEDIALIBRARY_MODULE_DIR=${SRC_DIR}/medialibrary
MEDIALIBRARY_BUILD_DIR=${MEDIALIBRARY_MODULE_DIR}/medialibrary
SQLITE_RELEASE="sqlite-autoconf-3460100"
SQLITE_SHA512SUM="a5ba5af9c8d6440d39ba67e3d5903c165df3f1d111e299efbe7c1cca4876d4d5aecd722e0133670daa6eb5cbf8a85c6a3d9852ab507a393615fb5245a3e1a743"

if [ ! -d "${MEDIALIBRARY_MODULE_DIR}/${SQLITE_RELEASE}" ]; then
  echo -e "\e[1m\e[32msqlite source not found, downloading\e[0m"
  cd ${MEDIALIBRARY_MODULE_DIR}
  rm -rf ${MEDIALIBRARY_BUILD_DIR}/build-android*
  rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/libs
  rm -rf ${MEDIALIBRARY_MODULE_DIR}/jni/obj
  wget https://download.videolan.org/pub/contrib/sqlite/${SQLITE_RELEASE}.tar.gz 2>/dev/null || curl -L -O https://download.videolan.org/pub/contrib/sqlite/${SQLITE_RELEASE}.tar.gz
  if [ ! "$(sha512sum ${SQLITE_RELEASE}.tar.gz)" = "${SQLITE_SHA512SUM}  ${SQLITE_RELEASE}.tar.gz" ]; then
    echo "Wrong sha1 for ${SQLITE_RELEASE}.tar.gz"
    exit 1
  fi
  tar -xozf ${SQLITE_RELEASE}.tar.gz
  rm -f ${SQLITE_RELEASE}.tar.gz
fi
cd ${MEDIALIBRARY_MODULE_DIR}/${SQLITE_RELEASE}
if [ ! -d "build-$ANDROID_ABI" ]; then
    mkdir "build-$ANDROID_ABI";
fi;
cd "build-$ANDROID_ABI";

if [ ! -e ./config.status ] || [ "$RELEASE" = "1" ] || [ ! -e "${MEDIALIBRARY_PREFIX}/lib/libsqlite3.a" ]; then
  ../configure \
    --host=$TARGET_TUPLE \
    --prefix="${MEDIALIBRARY_PREFIX}" \
    --disable-shared \
    CFLAGS="${VLC_CFLAGS}" \
    CXXFLAGS="${VLC_CFLAGS} ${VLC_CXXFLAGS}" \
    CC="${CROSS_CLANG}" \
    CXX="${CROSS_CLANG}++"
fi

make $MAKEFLAGS bin_PROGRAMS=
avlc_checkfail "sqlite build failed"

make bin_PROGRAMS= install
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

if [ "$ANDROID_ABI" = "armeabi-v7a" ]; then
    MESON_CPU="arm"
elif [ "$ANDROID_ABI" = "arm64-v8a" ]; then
    MESON_CPU="aarch64"
elif [ "$ANDROID_ABI" = "x86" ]; then
    MESON_CPU="i686"
elif [ "$ANDROID_ABI" = "x86_64" ]; then
    MESON_CPU="x86_64"
else
    diagnostic "Invalid arch specified: '$ANDROID_ABI'."
    diagnostic "Try --help for more information"
    exit 1
fi

echo "generate meson ${ANDROID_ABI}-${ANDROID_API} crossfile"
exec 3>crossfile-${ANDROID_ABI}-android-${ANDROID_API}.meson || return $?

printf '[binaries]\n' >&3
printf 'c = '"'"'%s'"'"'\n' "${CLANG_PREFIX}${ANDROID_API}-clang" >&3
printf 'cpp = '"'"'%s'"'"'\n' "${CLANG_PREFIX}${ANDROID_API}-clang++" >&3
printf 'ar = '"'"'llvm-ar'"'"'\n' >&3
printf 'strip = '"'"'llvm-strip'"'"'\n' >&3
printf 'pkgconfig = '"'"'pkg-config'"'"'\n' >&3
if [ $(command -v cmake) >/dev/null 2>&1 ]; then
  printf 'cmake = '"'"'%s'"'"'\n' "$(command -v cmake)" >&3
fi

printf '\n[host_machine]\n' >&3
printf 'system = '"'"'android'"'"'\n' >&3
printf 'endian = '"'"'little'"'"'\n' >&3
if [ "${MESON_CPU}" = "i686" ]; then
    printf 'cpu_family = '"'"'%s'"'"'\n' "x86" >&3
else
    printf 'cpu_family = '"'"'%s'"'"'\n' "${MESON_CPU}" >&3
fi
printf 'cpu = '"'"'%s'"'"'\n' "${MESON_CPU}" >&3

if [ ! -d "build-android-$ANDROID_ABI/" ] || [ ! -f "build-android-$ANDROID_ABI/build.ninja" ]; then
    export PATH="$LIBVLCJNI_SRC_DIR/vlc/extras/tools/build/bin:$PATH"

    PKG_CONFIG_LIBDIR="$LIBVLCJNI_SRC_DIR/vlc/build-android-${TARGET_TUPLE}/install/lib/pkgconfig" \
    PKG_CONFIG_PATH="${MEDIALIBRARY_PREFIX}/lib/pkgconfig:$LIBVLCJNI_SRC_DIR/vlc/contrib/$TARGET_TUPLE/lib/pkgconfig/" \
    meson setup \
        -Ddebug=true \
        -Doptimization=${MEDIALIBRARY_OPTIMIZATION} \
        -Db_ndebug=${MEDIALIBRARY_NDEBUG} \
        -Ddefault_library=static \
        --prefix "${MEDIALIBRARY_PREFIX}" \
        --cross-file crossfile-${ANDROID_ABI}-android-${ANDROID_API}.meson \
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
meson compile -C "build-android-$ANDROID_ABI"
meson install -C "build-android-$ANDROID_ABI"

avlc_checkfail "medialibrary: build failed"

cd ${SRC_DIR}

MEDIALIBRARY_LDLIBS="-L$LIBVLCJNI_SRC_DIR/libvlc/jni/libs/${ANDROID_ABI}/ -lvlc \
-L$LIBVLCJNI_SRC_DIR/vlc/contrib/$TARGET_TUPLE/lib -ljpeg \
-lc++abi"

$NDK_BUILD -C medialibrary \
  APP_STL="c++_shared" \
  LOCAL_CPP_FEATURES="rtti exceptions" \
  LOCAL_LDFLAGS="-Wl,-z,max-page-size=16384" \
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
