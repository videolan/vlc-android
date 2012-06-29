#! /bin/sh

OLD_NAME=org.videolan.vlc
NEW_NAME=org.videolan.vlc.$1

OLD_PATH=$(echo $OLD_NAME |sed 's/\./\//g')
NEW_PATH=$(echo $NEW_NAME |sed 's/\./\//g')

mv vlc-android/src/${OLD_PATH} vlc-android/src/tmp
mkdir -p vlc-android/src/${OLD_PATH}
mv vlc-android/src/tmp vlc-android/src/${NEW_PATH}

find vlc-android \( -name "*.xml" -o -name "*.java" -o -name "*.cfg" -o -name "*.aidl" \) -print0 | xargs -0 sed -ri "s/${OLD_NAME}/${NEW_NAME}/g"
find vlc-android \( -name "*.c" \) -print0 | xargs -0 sed -ri "s,${OLD_PATH},${NEW_PATH},g"
sed -ri "s,${OLD_PATH},${NEW_PATH},g" Makefile

find vlc-android/jni/* -print0 | xargs -0  sed -ri "s,$(echo $OLD_NAME |sed 's/\./_/g'),$(echo $NEW_NAME |sed 's/\./_/g'),g"
