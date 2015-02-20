#! /bin/sh
set -e

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -a to set the ARCH:"
            echo "  ARM:     armeabi-v7a, armeabi, armeabi-v5, armeabi-nofpu"
            echo "  ARM64:   arm64-v8a"
            echo "  X86:     x86, x86_64"
            echo "  MIPS:    mips, mips64."
            echo "Use --release to build in release mode"
            echo "Use -s to set your keystore file and -p for the password"
            echo "Use -t to get an AndroidTv build"
            echo "Use -c to get a ChromeOS build"
            exit 0
            ;;
        a|-a)
            ANDROID_ABI=$2
            shift
            ;;
        -t)
            ANDROID_TV=1
            ;;
        -c)
            CHROME_OS=1
            ;;
        -r|release|--release)
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
        run)
            RUN=1
            ;;
    esac
    shift
done

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
   echo "They must point to your NDK and SDK directories.\n"
   exit 1
fi

if [ -z "$ANDROID_ABI" ]; then
   echo "*** No ANDROID_ABI defined architecture: using ARMv7"
   ANDROID_ABI="armeabi-v7a"
fi

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

##########
# GRADLE #
##########

if [ ! -d "gradle/wrapper" ]; then
    GRADLE_VERSION=2.2.1
    GRADLE_URL=http://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip
    wget ${GRADLE_URL}
    checkfail "gradle: download failed"

    unzip gradle-${GRADLE_VERSION}-all.zip
    checkfail "gradle: unzip failed"

    cd gradle-${GRADLE_VERSION}

    ./bin/gradle wrapper
    checkfail "gradle: wrapper failed"

    ./gradlew -version
    checkfail "gradle: wrapper failed"
    cd ..
    mkdir -p gradle
    mv gradle-${GRADLE_VERSION}/gradle/wrapper/ gradle
    mv gradle-${GRADLE_VERSION}/gradlew .
    chmod +x gradlew
    rm -rf gradle-${GRADLE_VERSION}-all.zip gradle-${GRADLE_VERSION}
fi

####################
# Configure gradle #
####################

if [ -z "$KEYSTORE_FILE" ]; then
    KEYSTORE_FILE="$HOME/.android/debug.keystore"
    STOREALIAS="androiddebugkey"
else
    if [ -z "$PASSWORD_KEYSTORE" ]; then
        echo "No password"
        exit 1
    fi
    rm -f gradle.properties
    STOREALIAS="vlc"
fi

if [ ! -f gradle.properties ]; then
    echo keyStoreFile=$KEYSTORE_FILE > gradle.properties
    echo storealias=$STOREALIAS >> gradle.properties
    if [ -z "$PASSWORD_KEYSTORE" ]; then
        echo storepwd=android >> gradle.properties
    fi
fi
if [ ! -f local.properties ]; then
    echo sdk.dir=$ANDROID_SDK > local.properties
    echo ndk.dir=$ANDROID_NDK >> local.properties
fi

####################
# Fetch VLC source #
####################

TESTED_HASH=35fc569
if [ ! -d "vlc" ]; then
    echo "VLC source not found, cloning"
    git clone git://git.videolan.org/vlc.git vlc
    checkfail "vlc source: git clone failed"
else
    echo "VLC source found"
    cd vlc
    if ! git cat-file -e ${TESTED_HASH}; then
        cat << EOF
***
*** Error: Your vlc checkout does not contain the latest tested commit: ${TESTED_HASH}
***
EOF
        exit 1
    fi
    cd ..
fi

############
# Make VLC #
############

echo "Configuring"
OPTS="-a ${ANDROID_ABI}"
if [ "$RELEASE" = 1 ]; then
    OPTS="$OPTS release"
fi
if [ "$CHROME_OS" = 1 ]; then
    OPTS="$OPTS -c"
fi

./compile-libvlc.sh $OPTS

##################
# Compile the UI #
##################
PLATFORM="Vanilla"
BUILDTYPE="Debug"
if [ "$RELEASE" = 1 ]; then
    BUILDTYPE="Release"
fi
if [ "$ANDROID_TV" = 1 ]; then
    PLATFORM="Tv"
elif [ "$CHROME_OS" = 1 ]; then
    PLATFORM="Chrome"
fi

if [ "$ANDROID_ABI" = "armeabi-v5" ]; then
    GRADLE_ABI="ARMv5"
elif [ "$ANDROID_ABI" = "armeabi" ]; then
    GRADLE_ABI="ARMv6fpu"
elif [ "$ANDROID_ABI" = "armeabi-nofpu" ]; then
    GRADLE_ABI="ARMv6nofpu"
elif [ "$ANDROID_ABI" = "armeabi-v7a" ]; then
    GRADLE_ABI="ARMv7"
elif [ "$ANDROID_ABI" = "arm64-v8a" ]; then
    GRADLE_ABI="ARMv8"
elif [ "$ANDROID_ABI" = "x86" ]; then
    GRADLE_ABI="x86"
elif [ "$ANDROID_ABI" = "x86_64" ]; then
    GRADLE_ABI="x86_64"
elif [ "$ANDROID_ABI" = "mips" ]; then
    GRADLE_ABI="MIPS"
elif [ "$ANDROID_ABI" = "mips64" ]; then
    GRADLE_ABI="MIPS64"
fi

TARGET="assemble${PLATFORM}${GRADLE_ABI}${BUILDTYPE}"

PASSWORD_KEYSTORE="$PASSWORD_KEYSTORE" ./gradlew $TARGET

#######
# RUN #
#######
if [ "$RUN" = 1 ]; then
    export PATH=${ANDROID_SDK}/platform-tools/:$PATH
    adb wait-for-device
    if [ "$RELEASE" = 1 ]; then
        adb install -r vlc-android/build/outputs/apk/vlc-android-vanilla-release.apk
        adb shell am start -n org.videolan.vlc/org.videolan.vlc.gui.MainActivity
    else
        adb install -r vlc-android/build/outputs/apk/vlc-android-vanilla-debug.apk
        adb shell am start -n org.videolan.vlc.debug/org.videolan.vlc.gui.MainActivity
    fi
fi
