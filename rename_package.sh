#! /bin/sh

OLD_NAME=org.videolan.vlc
NEW_NAME=org.videolan.vlc.beta_v7neon

OLD_PATH=org/videolan/vlc/
NEW_PATH=org/videolan/vlc_beta_v7neon/

mv vlc-android/src/${OLD_PATH} vlc-android/src/${NEW_PATH}
find vlc-android \( -name "*.xml" -o -name "*.java" -o -name "*.cfg" -o -name "*.aidl" \) -print0 | xargs -0 sed -ri "s/${OLD_PATH}/${NEW_PATH}/g'

