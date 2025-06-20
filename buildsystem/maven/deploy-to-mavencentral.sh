#!/bin/bash

set -euo pipefail

# Constants
OUTPUT_DIR="artifacts"
MEDIALIB_VERSION=""
LIBVLC_VERSION=""
REMOTE_ACCESS_VERSION=""

# Utilities
function escape_pom() {
  echo "$1" | sed 's#/#\\/#g' | tr '\n' '@'
}

function xml_encode() {
  echo $1 | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/"/\&quot;/g; s/'"'"'/\&#39;/g'
}

function blue() {
  echo -e "\033[1;34m===================\n$1\n===================\033[0m"
}

function purple() {
  echo -e "\033[1;35m$1\033[0m"
}

function red() {
  echo -e "\033[1;31m===================\n$1\n===================\033[0m"
}

function cleanup {
  echo "Removing settings"
  rm settings.xml
}

if [ $# -eq 0 ]
  then
    red "The file path has not been specified."
    exit 1
fi

FILE=$1

#START

rm -rf $OUTPUT_DIR
mkdir -p $OUTPUT_DIR

blue "File management"
#check files
if test -f "$FILE"; then
  blue "File exists. Unzipping"
  unzip "$FILE" -d $OUTPUT_DIR
else
  red "Cannot find the $FILE file."
  exit 1
fi

blue "Files extracted"

blue "Looking for artifacts and versions"

if test -d "$OUTPUT_DIR/aars/repository/org/videolan/android/medialibrary-all"; then
  MEDIALIB_VERSION=$(ls -tUd "$OUTPUT_DIR/aars/repository/org/videolan/android/medialibrary-all/"*/ | xargs basename)
else
  red "Cannot find the medialibrary directory"
fi
if test -d "$OUTPUT_DIR/aars/repository/org/videolan/android/libvlc-all"; then
  LIBVLC_VERSION=$(ls -tUd "$OUTPUT_DIR/aars/repository/org/videolan/android/libvlc-all/"*/ | xargs basename)
else
  red "Cannot find the libvlc directory"
fi

if test -d "$OUTPUT_DIR/aars/repository/org/videolan/android/remote-access"; then
  REMOTE_ACCESS_VERSION=$(ls -tUd "$OUTPUT_DIR/aars/repository/org/videolan/android/remote-access/"*/ | xargs basename)
else
  red "Cannot find the remote access directory"
fi

purple "The following versions have been found.\nlibvlc: $LIBVLC_VERSION\nmedialibrary: $MEDIALIB_VERSION\nremote access: $REMOTE_ACCESS_VERSION"
read -sp "Continue: y/N" -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  [[ "$0" == "$BASH_SOURCE" ]] && exit 1 || return 1 # handle exits from shell or function but don't exit interactive shell
fi

blue "Ready to deploy"

read -sp 'Enter the zip password: ' pass
echo
TOKEN_PASSWORD=$(xml_encode $pass)

blue "Setup Maven credentials"
unzip -P $pass settings.xml.zip
trap cleanup EXIT


if [ -z "$LIBVLC_VERSION" ]; then
  purple "No version for libvlc. Skipping"
else
  BASE_DIR="$OUTPUT_DIR/aars/repository/org/videolan/android/libvlc-all/$LIBVLC_VERSION"
  blue "Deploying $BASE_DIR"

  mvn gpg:sign-and-deploy-file \
    --settings settings.xml \
    -DpomFile=$BASE_DIR/libvlc-all-$LIBVLC_VERSION.pom \
    -Dfile=$BASE_DIR/libvlc-all-$LIBVLC_VERSION.aar \
    -Dsources=$BASE_DIR/libvlc-all-$LIBVLC_VERSION-sources.jar \
    -Djavadoc=$BASE_DIR/libvlc-all-$LIBVLC_VERSION-javadoc.jar \
    -Durl="https://s01.oss.sonatype.org/service/local/staging/deploy/maven2" \
    -DgroupId=org.videolan.android \
    -Dgpg.keyname=49A7E6FE58DCF183F0B349DFB83763AD62ED0721 \
    -DrepositoryId=ossrh
fi

if [ -z "$MEDIALIB_VERSION" ]; then
  purple "No version for medialibrary Skipping"
else
  BASE_DIR="$OUTPUT_DIR/aars/repository/org/videolan/android/medialibrary-all/$MEDIALIB_VERSION"
  blue "Deploying $BASE_DIR"

  mvn gpg:sign-and-deploy-file \
    --settings settings.xml \
    -DpomFile=$BASE_DIR/medialibrary-all-$MEDIALIB_VERSION.pom \
    -Dfile=$BASE_DIR/medialibrary-all-$MEDIALIB_VERSION.aar \
    -Dsources=$BASE_DIR/medialibrary-all-$MEDIALIB_VERSION-sources.jar \
    -Djavadoc=$BASE_DIR/medialibrary-all-$MEDIALIB_VERSION-javadoc.jar \
    -Durl="https://s01.oss.sonatype.org/service/local/staging/deploy/maven2" \
    -DgroupId=org.videolan.android \
    -Dgpg.keyname=49A7E6FE58DCF183F0B349DFB83763AD62ED0721 \
    -DrepositoryId=ossrh
fi

if [ -z "$REMOTE_ACCESS_VERSION" ]; then
  purple "No version for remote access Skipping"
else
  BASE_DIR="$OUTPUT_DIR/aars/repository/org/videolan/android/remote-access/$REMOTE_ACCESS_VERSION"
  blue "Deploying $BASE_DIR"

  mvn gpg:sign-and-deploy-file \
    --settings settings.xml \
    -DpomFile=$BASE_DIR/remote-access-$REMOTE_ACCESS_VERSION.pom \
    -Dfile=$BASE_DIR/remote-access-$REMOTE_ACCESS_VERSION.aar \
    -Dsources=$BASE_DIR/remote-access-$REMOTE_ACCESS_VERSION-sources.jar \
    -Djavadoc=$BASE_DIR/remote-access-$REMOTE_ACCESS_VERSION-javadoc.jar \
    -Durl="https://s01.oss.sonatype.org/service/local/staging/deploy/maven2" \
    -DgroupId=org.videolan.android \
    -Dgpg.keyname=49A7E6FE58DCF183F0B349DFB83763AD62ED0721 \
    -DrepositoryId=ossrh
fi


