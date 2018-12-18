#! /bin/sh
set -e

#############
# FUNCTIONS #
#############

diagnostic()
{
    echo "$@" 1>&2;
}

checkfail()
{
    if [ ! $? -eq 0 ];then
        diagnostic "$1"
        exit 1
    fi
}

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

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
        --asan)
            ASAN=1
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
   diagnostic "*** No ANDROID_ABI defined architecture: using ARMv7"
   ANDROID_ABI="armeabi-v7a"
fi

if [ "$ANDROID_ABI" = "armeabi-v7a" -o "$ANDROID_ABI" = "arm" ]; then
    GRADLE_ABI="ARMv7"
elif [ "$ANDROID_ABI" = "arm64-v8a" -o "$ANDROID_ABI" = "arm64" ]; then
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

##########
# GRADLE #
##########

if [ ! -d "gradle/wrapper" ]; then
    diagnostic "Downloading gradle"
    GRADLE_VERSION=4.6
    GRADLE_URL=https://download.videolan.org/pub/contrib/gradle/gradle-${GRADLE_VERSION}-bin.zip
    wget ${GRADLE_URL} 2>/dev/null || curl -O ${GRADLE_URL}
    checkfail "gradle: download failed"

    unzip -o gradle-${GRADLE_VERSION}-bin.zip
    checkfail "gradle: unzip failed"

    cd gradle-${GRADLE_VERSION}

    ./bin/gradle --offline wrapper
    checkfail "gradle: wrapper failed"

    ./gradlew -version
    checkfail "gradle: wrapper failed"
    cd ..
    mkdir -p gradle
    mv gradle-${GRADLE_VERSION}/gradle/wrapper/ gradle
    mv gradle-${GRADLE_VERSION}/gradlew .
    chmod a+x gradlew
    rm -rf gradle-${GRADLE_VERSION}-bin.zip
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
    echo "8933bad161af4178b1185d1a37fbf41ea5269c55" > "$ANDROID_SDK/licenses/android-sdk-license"
    echo "d56f5187479451eabf01fb78af6dfcb131a6481e" >> "$ANDROID_SDK/licenses/android-sdk-license"
    echo "84831b9409646a918e30573bab4c9c91346d8abd" > "$ANDROID_SDK/licenses/android-sdk-preview-license"
fi

if [ "$GRADLE_SETUP" = 1 ]; then
    exit 0
fi
####################
# Fetch VLC source #
####################

TESTED_HASH=814aeb7
if [ ! -d "vlc" ]; then
    diagnostic "VLC source not found, cloning"
    git clone https://git.videolan.org/git/vlc/vlc-3.0.git vlc
    checkfail "vlc source: git clone failed"
fi
diagnostic "VLC source found"
cd vlc
if ! git cat-file -e ${TESTED_HASH}; then
    cat 1>&2 << EOF
***
*** Error: Your vlc checkout does not contain the latest tested commit: ${TESTED_HASH}
***
EOF
    exit 1
fi
if [ "$RELEASE" = 1 ]; then
    git reset --hard ${TESTED_HASH}
fi
cd ..


############
# Make VLC #
############

diagnostic "Configuring"
compile() {
    OPTS="-a $1"
    if [ "$RELEASE" = 1 ]; then
        OPTS="$OPTS release"
    fi
    if [ "$CHROME_OS" = 1 ]; then
        OPTS="$OPTS -c"
    fi
    if [ "$ASAN" = 1 ]; then
        OPTS="$OPTS --asan"
    fi

    # Build LibVLC if asked for it, or needed by medialibrary
    if [ "$BUILD_MEDIALIB" != 1 -o ! -d "libvlc/jni/libs/$1" ]; then
        ./compile-libvlc.sh $OPTS
    fi

    if [ "$NO_ML" != 1 ]; then
        ./compile-medialibrary.sh $OPTS
    fi
}
if [ "$BUILD_MEDIALIB" = 1 -o "$BUILD_LIBVLC" = 1 -o "$RELEASE" != 1 ]; then
    if [ "$ANDROID_ABI" = "all" ]; then
        if [ -d tmp ]; then
            rm -rf tmp
        fi
        mkdir tmp
        LIB_DIR="libvlc"
        if [ "$NO_ML" != 1 ]; then
            LIB_DIR="medialibrary"
        fi
        compile armeabi-v7a
        cp -r $LIB_DIR/jni/libs/armeabi-v7a tmp
        compile arm64-v8a
        cp -r $LIB_DIR/jni/libs/arm64-v8a tmp
        compile x86
        cp -r $LIB_DIR/jni/libs/x86 tmp
        compile x86_64
        mv tmp/* $LIB_DIR/jni/libs
        rm -rf tmp
    else
        compile $ANDROID_ABI
    fi
fi

##################
# Compile the UI #
##################
BUILDTYPE="Dev"
if [ "$SIGNED_RELEASE" = 1 ]; then
    BUILDTYPE="signedRelease"
elif [ "$RELEASE" = 1 ]; then
    BUILDTYPE="Release"
fi
if [ "$BUILD_LIBVLC" = 1 ];then
    GRADLE_ABI=$GRADLE_ABI ./gradlew -p libvlc assemble${BUILDTYPE}
    RUN=0
    CHROME_OS=0
    if [ "$PUBLISH" = 1 ];then
        GRADLE_ABI=$GRADLE_ABI ./gradlew -p libvlc install bintrayUpload
    fi
elif [ "$BUILD_MEDIALIB" = 1 ]; then
    GRADLE_ABI=$GRADLE_ABI ./gradlew -p medialibrary assemble${BUILDTYPE}
    RUN=0
    CHROME_OS=0
    if [ "$PUBLISH" = 1 ];then
        GRADLE_ABI=$GRADLE_ABI ./gradlew -p medialibrary install bintrayUpload
    fi
else
    if [ "$RUN" = 1 ]; then
        ACTION="install"
    else
        ACTION="assemble"
    fi
    TARGET="${ACTION}${BUILDTYPE}"
    CLI="" GRADLE_ABI=$GRADLE_ABI ./gradlew $TARGET
fi

#######
# RUN #
#######
if [ "$RUN" = 1 ]; then
    export PATH="${ANDROID_SDK}/platform-tools/:$PATH"
    adb wait-for-device
    if [ "$RELEASE" = 1 ]; then
        adb shell am start -n org.videolan.vlc/org.videolan.vlc.StartActivity
    else
        adb shell am start -n org.videolan.vlc.debug/org.videolan.vlc.StartActivity
    fi
fi

#########################
# Chrome OS repackaging #
#########################
# You need to run the armv7 version first, then relaunch this script for x86
if [ "$CHROME_OS" = 1 -a "$ANDROID_ABI" = "x86" ]; then
    unzip -o vlc-android/build/outputs/apk/VLC-Android-CHROME-*-ARMv7.apk lib/armeabi-v7a/libvlcjni.so
    zip -rv vlc-android/build/outputs/apk/VLC-Android-CHROME-*-x86.apk lib/armeabi-v7a/libvlcjni.so
    rm -rf lib/
    apk_to_crx.py --zip vlc-android/build/outputs/apk/VLC-Android-CHROME-*-x86.apk --metadata vlc-android/flavors/chrome/VLC-Android-CHROME.crx.json
    mv vlc-android/build/outputs/apk/VLC-Android-CHROME-*-x86.zip vlc-android/build/outputs/apk/VLC-Android-CHROME-ALL.zip
fi
