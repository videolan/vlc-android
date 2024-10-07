#! /bin/sh
#
# *************************************************************************
#  compile-remoteaccess.sh
# **************************************************************************
# Copyright © 2024 VLC authors and VideoLAN
# Author: Nicolas POMEPUY
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
# ***************************************************************************
#
#
#

set -e

#############
# FUNCTIONS #
#############

diagnostic()
{
    echo "$@" 1>&2;
}

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use -c to build the remote access project"
            exit 0
            ;;
        --init)
            INIT_ONLY=1
            ;;
        *)
            diagnostic "$0: Invalid option '$1'."
            diagnostic "$0: Try --help for more information."
            exit 1
            ;;
    esac
    shift
done

##############################
# Retrieve the remote access #
##############################
  diagnostic "Setting up the Remote Access project"

  REMOTE_ACCESS_TESTED_HASH=99d3c877c9b1e573806d22213a718a7a07fe9846
  REMOTE_ACCESS_REPOSITORY=https://code.videolan.org/videolan/remoteaccess

  : ${VLC_REMOTE_ACCESS_PATH:="$(pwd -P)/remoteaccess"}

  if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ] || [ ! -d "$VLC_REMOTE_ACCESS_PATH/.git" ]; then
      diagnostic "Remote access sources: not found, cloning"
      branch="main"
      if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ]; then
          git clone --single-branch --branch ${branch} "${REMOTE_ACCESS_REPOSITORY}"
          cd remoteaccess
      else # folder exist with only the artifacts
          cd remoteaccess
          git init
          git remote add origin "${REMOTE_ACCESS_REPOSITORY}"
          git pull origin ${branch}
      fi
      git reset --hard ${REMOTE_ACCESS_TESTED_HASH} || fail "Remote access sources: REMOTE_ACCESS_TESTED_HASH ${REMOTE_ACCESS_TESTED_HASH} not found"
      cd ..
  fi

  if [ "$INIT_ONLY" != 1 ]; then
        diagnostic "Building the Remote Access project"
    cd "$VLC_REMOTE_ACCESS_PATH"

    npm install
    npm run build-android
    cd ..
  fi
