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

# Release tags are shared with build.gradle (remoteAccessVersion)
REMOTE_ACCESS_PROPERTIES="$(cd "$(dirname "$0")" && pwd -P)/remoteaccess.properties"
[ -f "$REMOTE_ACCESS_PROPERTIES" ] || fail "Remote access: $REMOTE_ACCESS_PROPERTIES not found"
read_remote_access_version()
{
    sed -n "s/^$1=//p" "$REMOTE_ACCESS_PROPERTIES" | tr -d '[:space:]'
}
REMOTE_ACCESS_TAG_VLC3="$(read_remote_access_version remoteAccessVersionVlc3)"
REMOTE_ACCESS_TAG_VLC4="$(read_remote_access_version remoteAccessVersionVlc4)"
[ -n "$REMOTE_ACCESS_TAG_VLC3" ] || fail "Remote access: remoteAccessVersionVlc3 missing in $REMOTE_ACCESS_PROPERTIES"
[ -n "$REMOTE_ACCESS_TAG_VLC4" ] || fail "Remote access: remoteAccessVersionVlc4 missing in $REMOTE_ACCESS_PROPERTIES"
REMOTE_ACCESS_REPOSITORY=https://code.videolan.org/videolan/remoteaccess

: ${VLC_REMOTE_ACCESS_PATH:="$(pwd -P)/application/remote-access-client/remoteaccess"}
diagnostic "VLC_REMOTE_ACCESS_PATH is $VLC_REMOTE_ACCESS_PATH"

if [ "$USE_MAIN" = 1 ]; then
    branch="main"
    TARGET_TAG="${REMOTE_ACCESS_TAG_VLC4}"
else
    branch="0.x.x"
    TARGET_TAG="${REMOTE_ACCESS_TAG_VLC3}"
fi

CURRENT_DIR="$(pwd -P)"

# Fetch and check out the pinned release tag in the current directory, extra arguments are passed to git checkout
# Only this tag is fetched: fetching all tags would also download the other release line's history
checkout_pinned_tag()
{
    git fetch --no-tags origin "+refs/tags/${TARGET_TAG}:refs/tags/${TARGET_TAG}" \
        || fail "Remote access sources: unable to fetch tag ${TARGET_TAG}"
    git rev-parse -q --verify "refs/tags/${TARGET_TAG}^{commit}" >/dev/null \
        || fail "Remote access sources: tag ${TARGET_TAG} not found"
    diagnostic "Checking out Remote Access tag ${TARGET_TAG}"
    git checkout "$@" "refs/tags/${TARGET_TAG}"
}

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
    checkout_pinned_tag
    cd "$CURRENT_DIR"
elif [ "$RESET" = 1 ]; then
    diagnostic "Remote access sources: resetting $VLC_REMOTE_ACCESS_PATH"
    cd "$VLC_REMOTE_ACCESS_PATH"
    git fetch origin ${branch}:refs/remotes/origin/${branch}
    checkout_pinned_tag -f
    cd "$CURRENT_DIR"
else
    diagnostic "Remote access sources: found in $VLC_REMOTE_ACCESS_PATH"
fi

if [ "$INIT_ONLY" != 1 ]; then
    diagnostic "Building the Remote Access project"
    cd "$VLC_REMOTE_ACCESS_PATH"

    NODE_VERSION=$(node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/')

    if [ "$USE_MAIN" = 1 ]; then
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
    else
        # Also fails when node is missing or its version can't be parsed
        case "$NODE_VERSION" in
            ''|*[!0-9]*) NODE_VERSION_OK=0 ;;
            *) [ "$NODE_VERSION" -ge 20 ] && NODE_VERSION_OK=1 || NODE_VERSION_OK=0 ;;
        esac
        if [ "$NODE_VERSION_OK" != 1 ]; then
            fail "Node 20 or higher is required to build remote access (found Node v${NODE_VERSION:-unknown}). Please install or switch to Node 20+."
        fi
    fi

    npm install
    npm run build-android
    cd "$CURRENT_DIR"
fi
