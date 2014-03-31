#! /bin/sh

# Usage rename_package.sh <new_name> <ABI>
#  new_name should be a string
#  ABI should be an integer, between 0 and 4

OLD_NAME=org.videolan.vlc
NEW_NAME=org.videolan.vlc.$1

echo $NEW_NAME $2

OLD_PATH=$(echo $OLD_NAME |sed 's/\./\//g')
NEW_PATH=$(echo $NEW_NAME |sed 's/\./\//g')

sed -i.orig "s,${OLD_PATH},${NEW_PATH},g" Makefile

cd vlc-android/

sed -i.orig -e "s/versionCode\(.*\)0\"/versionCode\1$2\"/" AndroidManifest.xml
sed -i.orig -e "s/android:debuggable=\"true\"/android:debuggable=\"false\"/" AndroidManifest.xml

mv src/${OLD_PATH} src/tmp
mkdir -p src/${OLD_PATH}
mv src/tmp src/${NEW_PATH}

find . \( -name "*.xml" -o -name "*.java" -o -name "*.cfg" -o -name "*.aidl" \) -print0 | xargs -0 sed -i.orig "s/${OLD_NAME}/${NEW_NAME}/g"
find . \( -name "*.c" \) -print0 | xargs -0 sed -i.orig "s,${OLD_PATH},${NEW_PATH},g"
find jni -type f -print0 | xargs -0  sed -i.orig "s,$(echo $OLD_NAME |sed 's/\./_/g'),$(echo $NEW_NAME |sed 's/\./_/g'),g"
find . -name \*.orig -exec rm -f {} \;
