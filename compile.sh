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
            echo "Use -c to get a ChromeOS build"
            echo "Use -l to build only LibVLC"
            exit 0
            ;;
        a|-a)
            ANDROID_ABI=$2
            shift
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
        -l)
            BUILD_LIBVLC=1
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
    echo "Downloading gradle"
    GRADLE_VERSION=2.10
    GRADLE_URL=http://download.videolan.org/pub/contrib/gradle-${GRADLE_VERSION}-all.zip
    wget ${GRADLE_URL} 2>/dev/null || curl -O ${GRADLE_URL}
    checkfail "gradle: download failed"

    unzip -o gradle-${GRADLE_VERSION}-all.zip
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
    chmod a+x gradlew
    rm -rf gradle-${GRADLE_VERSION}-all.zip gradle-${GRADLE_VERSION}
fi

###############
# SDK Manager #
###############

if [ ! -f "gradle/plugins/sdk-manager-plugin.jar" ]; then
    cd gradle/
    if [ -d "sdk-manager-plugin" ]; then
        rm -rf sdk-manager-plugin
    fi
    git clone https://github.com/JakeWharton/sdk-manager-plugin.git
    cd sdk-manager-plugin
    git reset --hard 1d29782dd6ffa18880c8cbada748fb3dea45e7be
    ./gradlew assemble
    cd ..
    if [ ! -d "plugins" ]; then
        mkdir plugins
    fi
    mv sdk-manager-plugin/build/libs/sdk-manager-plugin-1.5.0-SNAPSHOT.jar plugins/sdk-manager-plugin.jar
    rm -rf sdk-manager-plugin
    cd ..
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

TESTED_HASH=3b82f9f
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
if [ "$CHROME_OS" = 1 ]; then
    PLATFORM="Chrome"
fi

if [ "$ANDROID_ABI" = "armeabi-v5" ]; then
    GRADLE_ABI="ARMv5"
elif [ "$ANDROID_ABI" = "armeabi" ]; then
    GRADLE_ABI="ARMv6fpu"
elif [ "$ANDROID_ABI" = "armeabi-nofpu" ]; then
    GRADLE_ABI="ARMv6nofpu"
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
else
    GRADLE_ABI="ARMv7"
fi

if [ "$BUILD_LIBVLC" = 1 ];then
    ./gradlew -p libvlc assemble${BUILDTYPE}
    RUN=0
    CHROME_OS=0
else
    if [ "$RUN" = 1 ]; then
        ACTION="install"
    else
        ACTION="assemble"
    fi
    TARGET="${ACTION}${PLATFORM}${GRADLE_ABI}${BUILDTYPE}"
    PASSWORD_KEYSTORE="$PASSWORD_KEYSTORE" ./gradlew $TARGET
fi

#######
# RUN #
#######
if [ "$RUN" = 1 ]; then
    export PATH=${ANDROID_SDK}/platform-tools/:$PATH
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
