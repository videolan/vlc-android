#!/bin/sh

SCRIPT_PATH=$(dirname $0)
TMP_PATH="$SCRIPT_PATH"/.gdb

FLAVOUR=vanillaARMv7
NDK_GDB_ARGS="--force"

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -f to set the flavour. Default is vanillaARMv7."
            exit 0
            ;;
        -f)
            FLAVOUR=$2
            shift
            ;;
        -s)
            NDK_GDB_ARGS="$NDK_GDB_ARGS --nowait --start"
            ;;
    esac
    shift
done

rm -rf "$TMP_PATH"
mkdir -p "$TMP_PATH"

ANDROID_MANIFEST="$SCRIPT_PATH"/vlc-android/build/intermediates/manifests/full/$FLAVOUR/debug/AndroidManifest.xml

if [ ! -f "$ANDROID_MANIFEST" ]; then
    echo "invalid flavour, did you try building first for this flavour ?"
    exit 1
fi

mkdir -p "$TMP_PATH"/jni

cp -r "$SCRIPT_PATH"/libvlc/jni/libs "$TMP_PATH"
cp -r "$SCRIPT_PATH"/libvlc/jni/obj "$TMP_PATH"
cp -r "$SCRIPT_PATH"/medialibrary/jni/libs "$TMP_PATH"
cp -r "$SCRIPT_PATH"/medialibrary/jni/obj "$TMP_PATH"

ln -s "$TMP_PATH"/obj "$TMP_PATH"/jni

cp "$SCRIPT_PATH"/libvlc/jni/Android.mk "$TMP_PATH"/jni
echo "APP_ABI := all" > "$TMP_PATH"/jni/Application.mk

cp "$ANDROID_MANIFEST" "$TMP_PATH"

(cd "$TMP_PATH" && bash $ANDROID_NDK/ndk-gdb $NDK_GDB_ARGS)
