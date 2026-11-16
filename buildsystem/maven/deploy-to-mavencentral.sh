#!/bin/bash

set -euo pipefail

# Constants
OUTPUT_DIR="artifacts"
MEDIALIB_VERSION=""
LIBVLC_VERSION=""

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
  rm pwd.txt
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

purple "The following versions have been found.\nlibvlc: $LIBVLC_VERSION\nmedialibrary: $MEDIALIB_VERSION"
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

file="pwd.txt"
PWD=$(cat "$file")

function deploy_artifact() {
  local artifact_name=$1
  local version=$2
  local base_dir=$3

  blue "Preparing and signing $artifact_name $version in $base_dir"

  # Sign all release files (.aar, .pom, .jar, .module) and generate md5/sha1 checksums
  for f in "$base_dir"/*; do
    [ -f "$f" ] || continue
    case "$f" in
      *.asc|*.md5|*.sha1|*.sha256|*.sha512) ;;
      *)
        if [ ! -f "${f}.asc" ]; then
          gpg --batch --yes -u 49A7E6FE58DCF183F0B349DFB83763AD62ED0721 --detach-sign --armor "$f" || true
        fi
        md5sum "$f" | awk '{print $1}' > "${f}.md5"
        sha1sum "$f" | awk '{print $1}' > "${f}.sha1"
        if [ -f "${f}.asc" ]; then
          md5sum "${f}.asc" | awk '{print $1}' > "${f}.asc.md5"
          sha1sum "${f}.asc" | awk '{print $1}' > "${f}.asc.sha1"
        fi
        ;;
    esac
  done

  # Create bundle.zip preserving full Maven directory structure from artifacts/aars/repository
  local bundle_zip="/tmp/bundle-${artifact_name}-${version}.zip"
  rm -f "$bundle_zip"
  (cd "$OUTPUT_DIR/aars/repository" && zip -r "$bundle_zip" "org/videolan/android/$artifact_name/$version")

  blue "Uploading bundle to Sonatype Central Publisher Portal (POST /api/v1/publisher/upload)..."
  local upload_res
  upload_res=$(curl -s -X POST \
    -H "Authorization: Bearer $PWD" \
    -F "bundle=@$bundle_zip" \
    "https://central.sonatype.com/api/v1/publisher/upload?name=${artifact_name}-${version}&publishingType=USER_MANAGED")

  echo "Central Publisher Portal response: $upload_res"

  rm -f "$bundle_zip"

  if [[ "$upload_res" =~ ^[0-9a-fA-F]{8}- || "$upload_res" == *"deploymentId"* ]]; then
    purple "Successfully submitted $artifact_name $version to Central Publisher Portal!"
  else
    red "Failed to upload $artifact_name $version to Central Publisher Portal. Response: $upload_res"
    exit 1
  fi
}

if [ -z "$LIBVLC_VERSION" ]; then
  purple "No version for libvlc. Skipping"
else
  BASE_DIR="$OUTPUT_DIR/aars/repository/org/videolan/android/libvlc-all/$LIBVLC_VERSION"
  deploy_artifact "libvlc-all" "$LIBVLC_VERSION" "$BASE_DIR"
fi

if [ -z "$MEDIALIB_VERSION" ]; then
  purple "No version for medialibrary Skipping"
else
  BASE_DIR="$OUTPUT_DIR/aars/repository/org/videolan/android/medialibrary-all/$MEDIALIB_VERSION"
  deploy_artifact "medialibrary-all" "$MEDIALIB_VERSION" "$BASE_DIR"
fi
