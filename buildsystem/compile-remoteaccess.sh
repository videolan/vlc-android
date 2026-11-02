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

fail()
{
    diagnostic "$1"
    exit 1
}

USE_MAIN=0
RESET=0

while [ $# -gt 0 ]; do
    case $1 in
        help|--help|-h)
            echo "Use --init to only clone/pull without building"
            echo "Use --main or -m to build using the main branch"
            echo "Use --reset or -r to reset code from git"
            exit 0
            ;;
        --init)
            INIT_ONLY=1
            ;;
        --main|-m)
            USE_MAIN=1
            ;;
        --reset|-r)
            RESET=1
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

REMOTE_ACCESS_TESTED_HASH=b72415d65efc922a2fd2c0539b61aeb0ea17cd66
REMOTE_ACCESS_REPOSITORY=https://code.videolan.org/videolan/remoteaccess

: ${VLC_REMOTE_ACCESS_PATH:="$(pwd -P)/application/remote-access-client/remoteaccess"}
diagnostic "VLC_REMOTE_ACCESS_PATH is $VLC_REMOTE_ACCESS_PATH"

if [ "$USE_MAIN" = 1 ]; then
    branch="main"
else
    branch="0.x.x"
fi

CURRENT_DIR="$(pwd -P)"

if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ] || [ ! -d "$VLC_REMOTE_ACCESS_PATH/.git" ]; then
    diagnostic "Remote access sources: not found, cloning branch $branch"
    if [ ! -d "$VLC_REMOTE_ACCESS_PATH" ]; then
        git clone --single-branch --branch ${branch} "${REMOTE_ACCESS_REPOSITORY}" "$VLC_REMOTE_ACCESS_PATH"
        cd "$VLC_REMOTE_ACCESS_PATH"
    else # folder exist with only the artifacts
        cd "$VLC_REMOTE_ACCESS_PATH"
        git init
        git remote add origin "${REMOTE_ACCESS_REPOSITORY}"
        git fetch origin ${branch}:refs/remotes/origin/${branch}
        git checkout -B ${branch} origin/${branch}
    fi
    if [ "$USE_MAIN" != 1 ]; then
        git reset --hard ${REMOTE_ACCESS_TESTED_HASH} || fail "Remote access sources: REMOTE_ACCESS_TESTED_HASH ${REMOTE_ACCESS_TESTED_HASH} not found"
    fi
    cd "$CURRENT_DIR"
elif [ "$RESET" = 1 ]; then
    diagnostic "Remote access sources: resetting $VLC_REMOTE_ACCESS_PATH"
    cd "$VLC_REMOTE_ACCESS_PATH"
    git fetch origin ${branch}:refs/remotes/origin/${branch}
    if [ "$USE_MAIN" = 1 ]; then
        git checkout -B ${branch} origin/${branch}
    else
        git checkout -B ${branch} ${REMOTE_ACCESS_TESTED_HASH} || fail "Remote access sources: REMOTE_ACCESS_TESTED_HASH ${REMOTE_ACCESS_TESTED_HASH} not found"
    fi
    cd "$CURRENT_DIR"
else
    diagnostic "Remote access sources: found in $VLC_REMOTE_ACCESS_PATH"
fi

if [ "$INIT_ONLY" != 1 ]; then
    diagnostic "Building the Remote Access project"
    cd "$VLC_REMOTE_ACCESS_PATH"

    if [ "$USE_MAIN" = 1 ]; then
        # Check current Node.js major version
        NODE_VERSION=$(node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/')

        # If Node 22 is not active, attempt to switch automatically using standard version managers
        if [ "$NODE_VERSION" != "22" ]; then
            # Attempt to activate Node 22 using NVM if available
            if [ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]; then
                . "${NVM_DIR:-$HOME/.nvm}/nvm.sh" 2>/dev/null || true
                nvm use 22 >/dev/null 2>&1 || true
            # Attempt to activate Node 22 using fnm if available
            elif command -v fnm >/dev/null 2>&1; then
                eval "$(fnm env 2>/dev/null)" || true
                fnm use 22 >/dev/null 2>&1 || true
            # Attempt to activate Node 22 using n if available
            elif command -v n >/dev/null 2>&1; then
                n 22 >/dev/null 2>&1 || true
            # Attempt to activate Node 22 using asdf if available
            elif command -v asdf >/dev/null 2>&1; then
                asdf shell nodejs 22 >/dev/null 2>&1 || true
            fi

            # Re-check Node.js major version
            NODE_VERSION=$(node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/')

            # Only display an error message and fail if Node 22 is still not available
            if [ "$NODE_VERSION" != "22" ]; then
                fail "Node 22 is required to build the remote access main branch (found Node v${NODE_VERSION:-unknown}). Please install or switch to Node 22."
            fi
        fi
    fi

    npm install
    npm run build-android
    cd "$CURRENT_DIR"
fi
