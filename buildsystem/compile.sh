#! /bin/sh
set -e


#############
# FUNCTIONS #
#############

diagnostic()
{
    echo "$@" 1>&2;
}

fail()
{
    diagnostic "$1"
    exit 1
}

# Try to check whether a patch file has already been applied to the current directory tree
# Warning: this function assumes:
# - The patch file contains a Message-Id header. This can be generated with `git format-patch --thread ...` option
# - The patch has been applied with `git am --message-id ...` option to keep the Message-Id in the commit description
check_patch_is_applied()
{
    patch_file=$1
    diagnostic "Checking presence of patch $1"
    message_id=$(grep -E '^Message-Id: [^ ]+' "$patch_file" | sed 's/^Message-Id: \([^\ ]+\)/\1/')
    if [ -z "$message_id" ]; then
        diagnostic "Error: patch $patch_file does not contain a Message-Id."
        diagnostic "Please consider generating your patch files with the 'git format-patch --thread ...' option."
        diagnostic ""
        exit 1
    fi
    if [ -z "$(git log --grep="$message_id")" ]; then
        diagnostic "Cannot find patch $patch_file in tree, aborting."
        diagnostic "There can be two reasons for that:"
        diagnostic "- you forgot to apply the patch on this tree, or"
        diagnostic "- you applied the patch without the 'git am --message-id ...' option."
        diagnostic ""
        exit 1
    fi
}

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

RELEASE=0
while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -a to set the ARCH:"
            echo "  ARM:     (armeabi-v7a|arm)"
            echo "  ARM64:   (arm64-v8a|arm64)"
            echo "  X86:     x86, x86_64"
            echo "Use --release to build in release mode"
            echo "Use --signrelease to build in release mode and sign apk, see vlc-android/build.gradle"
            echo "Use -s to set your keystore file and -p for the password"
            echo "Use -c to get a ChromeOS build"
            echo "Use -l to build only LibVLC"
            echo "Use -b to bypass libvlc source checks (vlc custom sources)"
            exit 0
            ;;
        a|-a)
            ANDROID_ABI=$2
            shift
            ;;
        -r|release|--release)
            RELEASE=1
            ;;
        signrelease|--signrelease)
            SIGNED_RELEASE=1
            RELEASE=1
            ;;
        -s|--signature)
            KEYSTORE_FILE=$2
            shift
            ;;
        -p|--password)
            PASSWORD_KEYSTORE=$2
            shift
            ;;
        -l)
            BUILD_LIBVLC=1
            NO_ML=1
            ;;
        -ml)
            BUILD_MEDIALIB=1
            ;;
        run)
            RUN=1
            ;;
        test)
            TEST=1
            ;;
        stub)
            STUB=1
            ;;
        --no-ml)
            NO_ML=1
            ;;
        --publish)
            RELEASE=1
            PUBLISH=1
            ;;
        --init)
            GRADLE_SETUP=1
            ;;
        -b)
            BYPASS_VLC_SRC_CHECKS=1
            ;;
        *)
            diagnostic "$0: Invalid option '$1'."
            diagnostic "$0: Try --help for more information."
            exit 1
            ;;
    esac
    shift
done

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   diagnostic "You must define ANDROID_NDK, ANDROID_SDK before starting."
   diagnostic "They must point to your NDK and SDK directories."
   exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
   diagnostic "*** No ANDROID_ABI defined architecture: using arm64-v8a"
   ANDROID_ABI="arm64-v8a"
fi

if [ "$ANDROID_ABI" = "armeabi-v7a" -o "$ANDROID_ABI" = "arm" ]; then
    ANDROID_ABI="armeabi-v7a"
    GRADLE_ABI="ARMv7"
elif [ "$ANDROID_ABI" = "arm64-v8a" -o "$ANDROID_ABI" = "arm64" ]; then
    ANDROID_ABI="arm64-v8a"
    GRADLE_ABI="ARMv8"
elif [ "$ANDROID_ABI" = "x86" ]; then
    GRADLE_ABI="x86"
elif [ "$ANDROID_ABI" = "x86_64" ]; then
    GRADLE_ABI="x86_64"
elif [ "$ANDROID_ABI" = "all" ]; then
    GRADLE_ABI="all"
else
    diagnostic "Invalid arch specified: '$ANDROID_ABI'."
    diagnostic "Try --help for more information"
    exit 1
fi

####################
# Configure gradle #
####################

if [ -z "$KEYSTORE_FILE" ]; then
    KEYSTORE_FILE="$HOME/.android/debug.keystore"
    STOREALIAS="androiddebugkey"
else
    if [ -z "$PASSWORD_KEYSTORE" ]; then
        diagnostic "No password"
        exit 1
    fi
    rm -f gradle.properties
    STOREALIAS="vlc"
fi

if [ ! -f gradle.properties ]; then
    echo android.enableJetifier=true > gradle.properties
    echo android.useAndroidX=true >> gradle.properties
    echo kapt.incremental.apt=true >> gradle.properties
    echo kapt.use.worker.api=true >> gradle.properties
    echo kapt.include.compile.classpath=false >> gradle.properties
    echo keyStoreFile=$KEYSTORE_FILE >> gradle.properties
    echo storealias=$STOREALIAS >> gradle.properties
    if [ -z "$PASSWORD_KEYSTORE" ]; then
        echo storepwd=android >> gradle.properties
    fi
fi

init_local_props() {
    (
    # initialize the local.properties file,
    # or fix it if it was modified (by Android Studio, for example).
    echo_props() {
        echo "sdk.dir=$ANDROID_SDK"
        echo "ndk.dir=$ANDROID_NDK"
    }
    # first check if the file just needs to be created for the first time
    if [ ! -f "$1" ]; then
        echo_props > "$1"
        return 0
    fi
    # escape special chars to get regex that matches string
    make_regex() {
        echo "$1" | sed -e 's/\([[\^$.*]\)/\\\1/g' -
    }
    android_sdk_regex=`make_regex "${ANDROID_SDK}"`
    android_ndk_regex=`make_regex "${ANDROID_NDK}"`
    # check for lines setting the SDK directory
    sdk_line_start="^sdk\.dir="
    total_sdk_count=`grep -c "${sdk_line_start}" "$1"`
    good_sdk_count=`grep -c "${sdk_line_start}${android_sdk_regex}\$" "$1"`
    # check for lines setting the NDK directory
    ndk_line_start="^ndk\.dir="
    total_ndk_count=`grep -c "${ndk_line_start}" "$1"`
    good_ndk_count=`grep -c "${ndk_line_start}${android_ndk_regex}\$" "$1"`
    # if one of each is found and both match the environment vars, no action needed
    if [ "$total_sdk_count" -eq "1" -a "$good_sdk_count" -eq "1" \
	    -a "$total_ndk_count" -eq "1" -a "$good_ndk_count" -eq "1" ]
    then
        return 0
    fi
    # if neither property is set they can simply be appended to the file
    if [ "$total_sdk_count" -eq "0" -a "$total_ndk_count" -eq "0" ]; then
        echo_props >> "$1"
        return 0
    fi
    # if a property is set incorrectly or too many times,
    # remove all instances of both properties and append correct ones.
    replace_props() {
        temp_props="$1.tmp"
        while IFS= read -r LINE || [ -n "$LINE" ]; do
            line_sdk_dir="${LINE#sdk.dir=}"
            line_ndk_dir="${LINE#ndk.dir=}"
            if [ "x$line_sdk_dir" = "x$LINE" -a "x$line_ndk_dir" = "x$LINE" ]; then
                echo "$LINE"
            fi
        done <"$1" >"$temp_props"
        echo_props >> "$temp_props"
        mv -f -- "$temp_props" "$1"
    }
    echo "local.properties: Contains incompatible sdk.dir and/or ndk.dir properties. Replacing..."
    replace_props "$1"
    echo "local.properties: Finished replacing sdk.dir and/or ndk.dir with current environment variables."
    )
}
init_local_props local.properties || { echo "Error initializing local.properties"; exit $?; }

if [ ! -d "$ANDROID_SDK/licenses" ]; then
    mkdir "$ANDROID_SDK/licenses"
    echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_SDK/licenses/android-sdk-license"
    echo "d56f5187479451eabf01fb78af6dfcb131a6481e" >> "$ANDROID_SDK/licenses/android-sdk-license"
    echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" >> "$ANDROID_SDK/licenses/android-sdk-license"
fi

##########
# GRADLE #
##########

if [ ! -d "gradle/wrapper" ]; then
    diagnostic "Downloading gradle"
    GRADLE_VERSION=5.6.4
    GRADLE_URL=https://download.videolan.org/pub/contrib/gradle/gradle-${GRADLE_VERSION}-bin.zip
    wget ${GRADLE_URL} 2>/dev/null || curl -O ${GRADLE_URL} || fail "gradle: download failed"

    unzip -o gradle-${GRADLE_VERSION}-bin.zip || fail "gradle: unzip failed"

    ./gradle-${GRADLE_VERSION}/bin/gradle wrapper || fail "gradle: wrapper failed"

    ./gradlew -version || fail "gradle: wrapper failed"
    chmod a+x gradlew
    rm -rf gradle-${GRADLE_VERSION}-bin.zip
fi

if [ "$GRADLE_SETUP" = 1 ]; then
    exit 0
fi
####################
# Fetch VLC source #
####################

TESTED_HASH=fc941df
VLC_REPOSITORY=https://git.videolan.org/git/vlc/vlc-3.0.git
if [ ! -d "vlc" ]; then
    diagnostic "VLC sources: not found, cloning"
    git clone "${VLC_REPOSITORY}" vlc || fail "VLC sources: git clone failed"
    cd vlc
    diagnostic "VLC sources: resetting to the TESTED_HASH commit (${TESTED_HASH})"
    git reset --hard ${TESTED_HASH} || fail "VLC sources: TESTED_HASH ${TESTED_HASH} not found"
    diagnostic "VLC sources: applying custom patches"
    # Keep Message-Id inside commits description to track them afterwards
    git am --message-id ../libvlc/patches/vlc3/*.patch || fail "VLC sources: cannot apply custom patches"
    cd ..
else
    diagnostic "VLC source: found sources, leaving untouched"
fi
if [ "$BYPASS_VLC_SRC_CHECKS" = 1 ]; then
    diagnostic "VLC sources: Bypassing checks (required by option)"
else
    diagnostic "VLC sources: Checking TESTED_HASH and patches presence"
    diagnostic "NOTE: checks can be bypass by adding '-b' option to this script."
    cd vlc
    git cat-file -e ${TESTED_HASH} 2> /dev/null || \
        fail "Error: Your vlc checkout does not contain the latest tested commit: ${TESTED_HASH}"
    for patch_file in ../libvlc/patches/vlc3/*.patch; do
        check_patch_is_applied "$patch_file"
    done
    cd ..
fi

############
# Make VLC #
############
diagnostic "Configuring"
compile() {
    # Build LibVLC if asked for it, or needed by medialibrary
    copy_tmp="$1"

    OUT_DBG_DIR=.dbg/${ANDROID_ABI}
    mkdir -p $OUT_DBG_DIR

    if [ "$BUILD_MEDIALIB" != 1 -o ! -d "libvlc/jni/libs/$1" ]; then
        AVLC_SOURCED=1 . buildsystem/compile-libvlc.sh
        avlc_build

        $NDK_BUILD -C libvlc \
            VLC_SRC_DIR="$VLC_SRC_DIR" \
            VLC_BUILD_DIR="$VLC_BUILD_DIR" \
            VLC_OUT_LDLIBS="$VLC_OUT_LDLIBS" \
            APP_BUILD_SCRIPT=jni/Android.mk \
            APP_PLATFORM=android-${ANDROID_API} \
            APP_ABI=${ANDROID_ABI} \
            NDK_PROJECT_PATH=jni \
            NDK_TOOLCHAIN_VERSION=clang \
            NDK_DEBUG=${NDK_DEBUG}

        if [ "$copy_tmp" = "--copy-tmp=libvlc" ];then
            cp -r $VLC_OUT_PATH/libs/${ANDROID_ABI} libvlc/jni/libs/${ANDROID_ABI} build/tmp
        fi

        cp -a $VLC_OUT_PATH/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
        cp -a ./libvlc/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
    fi

    if [ "$NO_ML" != 1 ]; then
        ANDROID_ABI=$ANDROID_ABI RELEASE=$RELEASE buildsystem/compile-medialibrary.sh
        if [ "$copy_tmp" = "--copy-tmp=medialibrary" ];then
            cp -r medialibrary/jni/libs/${ANDROID_ABI} build/tmp
        fi

        cp -a medialibrary/jni/obj/local/${ANDROID_ABI}/*.so ${OUT_DBG_DIR}
    fi
}

if [ "$ANDROID_ABI" = "all" ]; then
    if [ -d build/tmp ]; then
        rm -rf build/tmp
    fi
    mkdir -p build/tmp
    LIB_DIR="libvlc"
    if [ "$NO_ML" != 1 ]; then
        LIB_DIR="medialibrary"
    fi
    copy_tmp="--copy-tmp=$LIB_DIR"

    # The compile function is sourcing ./compile-libvlc.sh and is configured
    # with env variables (ANDROID_ABI), therefore it need to be run from a new
    # context for each ABI

    (ANDROID_ABI=armeabi-v7a RELEASE=$RELEASE compile $copy_tmp)
    (ANDROID_ABI=arm64-v8a RELEASE=$RELEASE compile $copy_tmp)
    (ANDROID_ABI=x86 RELEASE=$RELEASE compile $copy_tmp)
    (ANDROID_ABI=x86_64 RELEASE=$RELEASE compile $copy_tmp)
    rm -rf $LIB_DIR/jni/libs/
    mv build/tmp $LIB_DIR/jni/libs/

    GRADLE_VLC_SRC_DIRS="''"
else
    compile
    GRADLE_VLC_SRC_DIRS="$VLC_OUT_PATH/libs"
fi

##################
# Compile the UI #
##################
BUILDTYPE="Dev"
if [ "$TEST" = 1 ]; then
    BUILDTYPE="Debug"
elif [ "$SIGNED_RELEASE" = 1 ]; then
    BUILDTYPE="signedRelease"
elif [ "$RELEASE" = 1 ]; then
    BUILDTYPE="Release"
fi

if [ "$BUILD_LIBVLC" = 1 ];then
    GRADLE_VLC_SRC_DIRS="$GRADLE_VLC_SRC_DIRS" GRADLE_ABI=$GRADLE_ABI ./gradlew -p libvlc assemble${BUILDTYPE}
    RUN=0
    if [ "$PUBLISH" = 1 ];then
        GRADLE_VLC_SRC_DIRS="$GRADLE_VLC_SRC_DIRS" GRADLE_ABI=$GRADLE_ABI ./gradlew -p libvlc install bintrayUpload
    fi
elif [ "$BUILD_MEDIALIB" = 1 ]; then
    GRADLE_ABI=$GRADLE_ABI ./gradlew -p medialibrary assemble${BUILDTYPE}
    RUN=0
    if [ "$PUBLISH" = 1 ];then
        GRADLE_ABI=$GRADLE_ABI ./gradlew -p medialibrary install bintrayUpload
    fi
else
    if [ "$TEST" = 1 -o "$RUN" = 1 ]; then
        ACTION="install"
    else
        ACTION="assemble"
    fi
    TARGET="${ACTION}${BUILDTYPE}"
    GRADLE_VLC_SRC_DIRS="$GRADLE_VLC_SRC_DIRS" CLI="" GRADLE_ABI=$GRADLE_ABI ./gradlew $TARGET

    if [ "$TEST" = 1 ]; then
        TARGET="application:vlc-android:install${BUILDTYPE}AndroidTest"
        GRADLE_VLC_SRC_DIRS="$GRADLE_VLC_SRC_DIRS" CLI="" GRADLE_ABI=$GRADLE_ABI ./gradlew $TARGET

        echo -e "\n===================================\nRun following for UI tests:"
        echo "adb shell am instrument -w -e package org.videolan.vlc.gui org.videolan.vlc.debug.test/org.videolan.vlc.MultidexTestRunner 1> result_UI_test.txt"
    fi
fi

#######
# RUN #
#######
if [ "$RUN" = 1 ]; then
    export PATH="${ANDROID_SDK}/platform-tools/:$PATH"
        EXTRA=""
        if [ "$STUB" = 1 ]; then
        EXTRA="--ez 'extra_test_stubs' true"
    fi
    adb wait-for-device
    if [ "$RELEASE" = 1 ]; then
        adb shell am start -n org.videolan.vlc/org.videolan.vlc.StartActivity $EXTRA
    else
        adb shell am start -n org.videolan.vlc.debug/org.videolan.vlc.StartActivity $EXTRA
    fi
fi
