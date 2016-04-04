#!/bin/sh

SCRIPT_PATH=$(dirname $0)
TMP_PATH="$SCRIPT_PATH"/.gdb

FLAVOUR=vanillaARMv7
NDK_GDB_ARGS="--force"

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -f to set the flavour. Default is vanillaARMv7."
            echo "Use --apk-file <file.apk> [--dbg-file <file.so.dbg>] to debug an apk via a file containing the debugging info"
            exit 0
            ;;
        -f)
            FLAVOUR=$2
            shift
            ;;
        -s)
            NDK_GDB_ARGS="$NDK_GDB_ARGS --nowait --start"
            ;;
        --apk-file)
            APK_PATH=$2
            shift
            ;;
        --dbg-file)
            DBGFILE_PATH=$2
            shift
            ;;
    esac
    shift
done

rm -rf "$TMP_PATH"
mkdir -p "$TMP_PATH"

if [ ! -z "$APK_PATH" ]; then
    if [ ! -f "$APK_PATH" ];then
        echo "invalid --apk"
        exit 1
    fi

    aapt=$(ls -1 --sort=time $ANDROID_SDK/build-tools/*/aapt|head -n 1)
    if [ -z "$aapt" ];then
        echo "aapt not found in \$ANDROID_SDK"
        exit 1
    fi

    arch=$($aapt l -a "$APK_PATH"|grep "libvlcjni.so"|cut -d"/" -f 2)

    if [ -z "$DBGFILE_PATH" ];then
        version=$($aapt l -a "$APK_PATH"|grep versionName|cut -d\" -f 2)
        dbgfile_path="$SCRIPT_PATH/.dbg/$arch/$version/libvlcjni.so.dbg"
    else
        dbgfile_path="$DBGFILE_PATH"
    fi
    if [ ! -f "$dbgfile_path" ];then
        echo "invalid --dbg-file"
        exit 1
    fi

    echo ""
    echo "\"list *0x<pc_address>\" to know where the specified apk crashed"
    echo ""

    ./compile-libvlc.sh -a $arch --gdb "$dbgfile_path"
    exit 0
fi

ANDROID_MANIFEST="$SCRIPT_PATH"/vlc-android/build/intermediates/manifests/full/$FLAVOUR/debug/AndroidManifest.xml

if [ ! -f "$ANDROID_MANIFEST" ]; then
    echo "invalid flavour, did you try building first for this flavour ?"
    exit 1
fi

mkdir -p "$TMP_PATH"/jni

cp -r "$SCRIPT_PATH"/libvlc/jni/libs "$TMP_PATH"
cp -r "$SCRIPT_PATH"/libvlc/jni/obj "$TMP_PATH"
ln -s "$TMP_PATH"/obj "$TMP_PATH"/jni

cp "$SCRIPT_PATH"/libvlc/jni/Android.mk "$TMP_PATH"/jni
echo "APP_ABI := all" > "$TMP_PATH"/jni/Application.mk

cp "$ANDROID_MANIFEST" "$TMP_PATH"

(cd "$TMP_PATH" && bash $ANDROID_NDK/ndk-gdb $NDK_GDB_ARGS)
