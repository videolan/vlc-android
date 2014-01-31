#! /bin/sh

# Usage rename_package.sh <new_name> <ABI>
#  new_name should be a string
#  ABI should be an integer, between 0 and 4

OLD_NAME=org.videolan.vlc
NEW_NAME=org.videolan.vlc.$1

echo $NEW_NAME $2

sed -i.orig -e "s/versionCode\(.*\)0\"/versionCode\1$2\"/" vlc-android/AndroidManifest.xml

OLD_PATH=$(echo $OLD_NAME |sed 's/\./\//g')
NEW_PATH=$(echo $NEW_NAME |sed 's/\./\//g')

mv vlc-android/src/${OLD_PATH} vlc-android/src/tmp
mkdir -p vlc-android/src/${OLD_PATH}
mv vlc-android/src/tmp vlc-android/src/${NEW_PATH}

find vlc-android \( -name "*.xml" -o -name "*.java" -o -name "*.cfg" -o -name "*.aidl" \) -print0 | xargs -0 sed -i.orig "s/${OLD_NAME}/${NEW_NAME}/g"
find vlc-android \( -name "*.c" \) -print0 | xargs -0 sed -i.orig "s,${OLD_PATH},${NEW_PATH},g"
sed -i.orig "s,${OLD_PATH},${NEW_PATH},g" Makefile

find vlc-android/jni -type f -print0 | xargs -0  sed -i.orig "s,$(echo $OLD_NAME |sed 's/\./_/g'),$(echo $NEW_NAME |sed 's/\./_/g'),g"
