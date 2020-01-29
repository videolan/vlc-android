#!/bin/sh

SCRIPT_PATH=$(dirname $0)
TMP_PATH="$SCRIPT_PATH"/.gdb
NDK_GDB_ARGS="--force"

while [ $# -gt 0 ]; do
    case $1 in
        -s)
            NDK_GDB_ARGS="$NDK_GDB_ARGS --nowait --start"
            ;;
    esac
    shift
done

rm -rf "$TMP_PATH"
mkdir -p "$TMP_PATH"

APP_BUILD="$SCRIPT_PATH/../application/app/build/intermediates"
ANDROID_MANIFEST="$APP_BUILD/merged_manifests/dev/AndroidManifest.xml"
ABI=`ls "$APP_BUILD/stripped_native_libs/dev/out/lib" --sort=time | head -n 1`

if [ ! -f "$ANDROID_MANIFEST" -o "$ABI" = "" ]; then
    echo "Invalid manifest/ABI, did you try building first ?"
    exit 1
fi

mkdir -p "$TMP_PATH"/jni
touch "$TMP_PATH"/jni/Android.mk
echo "APP_ABI := $ABI" > "$TMP_PATH"/jni/Application.mk

DEST=obj/local/$ABI
mkdir -p "$TMP_PATH/$DEST"

cp -r "$SCRIPT_PATH"/../libvlc/jni/$DEST/*.so "$TMP_PATH/$DEST"
cp -r "$SCRIPT_PATH"/../vlc/build-android-*linux-android*/ndk/$DEST/*.so "$TMP_PATH/$DEST"
cp -r "$SCRIPT_PATH"/../medialibrary/jni/$DEST/*.so "$TMP_PATH/$DEST"


cp "$ANDROID_MANIFEST" "$TMP_PATH"

(cd "$TMP_PATH" && bash $ANDROID_NDK/ndk-gdb $NDK_GDB_ARGS)
