#! /bin/sh
set -e

# Read the Android Wiki http://wiki.videolan.org/AndroidCompile
# Setup all that stuff correctly.
# Get the latest Android SDK Platform or modify numbers in configure.sh and libvlc/default.properties.

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
   echo "They must point to your NDK and SDK directories.\n"
   exit 1
fi

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
            RELEASE=1
            ;;
    esac
    shift
done

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
    rm -rf gradle-${GRADLE_VERSION}-all.zip gradle-${GRADLE_VERSION}
fi

####################
# Configure gradle #
####################

if [ ! -f gradle.properties ]; then
    echo keyStoreFile=$HOME/.android/debug.keystore > gradle.properties
    echo storealias=androiddebugkey >> gradle.properties
    echo storepwd=android >> gradle.properties
fi
if [ ! -f local.properties ]; then
    echo sdk.dir=$ANDROID_SDK > local.properties
    echo ndk.dir=$ANDROID_NDK >> local.properties
fi

####################
# Fetch VLC source #
####################

TESTED_HASH=18e445a
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
    OPTS+=" release"
fi

./compile-libvlc.sh $OPTS

##################
# Compile the UI #
##################

./gradlew tasks
