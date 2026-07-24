#!/bin/bash

#
# *************************************************************************
#  check-all-commits.sh
# **************************************************************************
# Copyright © 2026 VLC authors and VideoLAN
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

# check-all-commits.sh
# --------------------
# A robust helper script designed to verify builds during large rebases.
# It provides real-time progress, statistics, and automatic retries with 'clean' on failure.
# It can automatically resume rebases and checkpoint your work.

# --- Configuration & Environment ---

# Detect the project root directory regardless of where the script is called from.
# This ensures that gradlew and persistence files are always found in the same relative locations.
ROOT_DIR=$(git rev-parse --show-toplevel)
if [ -z "$ROOT_DIR" ]; then
    echo "Error: Not in a git repository. This script must be run from within the VLC project."
    exit 1
fi

# Build commands.
# BASE_BUILD_CMD is used for standard builds and retries after a 'clean'.
# BUILD_CMD includes performance optimizations for iterative checks.
BASE_BUILD_CMD="$ROOT_DIR/gradlew :application:app:assembleDebug"
OPTIMIZED_ARGS="--offline --build-cache --configuration-cache --parallel"
BUILD_CMD="$BASE_BUILD_CMD $OPTIMIZED_ARGS"

# Persistence directory and files.
PERSISTENCE_DIR="$ROOT_DIR/buildsystem/.check-commits"
mkdir -p "$PERSISTENCE_DIR"

PROGRESS_FILE="$PERSISTENCE_DIR/progress"   # Stores stats between rebase iterations.
UI_TABLE_FLAG="$PERSISTENCE_DIR/ui_present"  # Acts as a semaphore to avoid re-drawing headers.
BUILD_LOG="$PERSISTENCE_DIR/build.log"       # Captured output of the current Gradle build.

# Git Rebase Todo file.
# We link the actual git rebase file into our persistence folder for consistency.
ACTUAL_TODO_FILE="$(git rev-parse --git-path rebase-merge 2>/dev/null)/git-rebase-todo"
[ ! -f "$ACTUAL_TODO_FILE" ] && ACTUAL_TODO_FILE="$(git rev-parse --git-path rebase-apply 2>/dev/null)/todo"
TODO_FILE="$PERSISTENCE_DIR/rebase-todo"
if [ -f "$ACTUAL_TODO_FILE" ]; then
    ln -sf "$ACTUAL_TODO_FILE" "$TODO_FILE"
fi

# UI Colors (ANSI Escape Codes)
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
RED='\033[1;31m'
DARK_GREEN='\033[38;5;30m'
NC='\033[0m' # No Color

# UI Utilities
hide_cursor() { echo -ne "\033[?25l"; }
show_cursor() { echo -ne "\033[?25h"; }

# The height of our "Rich UI" table. Centralized here for cursor movement calculations.
UI_HEIGHT=24

# --- State Management ---

# load_progress: Reads rebase statistics from the disk.
# Handles three generations of file formats for seamless upgrades.
load_progress() {
    if [ -f "$PROGRESS_FILE" ]; then
        local line=$(cat "$PROGRESS_FILE")
        local count=$(echo "$line" | wc -w)

        if [ "$count" -eq 5 ]; then
            # Legacy format (pre-refactor)
            read -r CURRENT TOTAL TOTAL_SUCCESS_DURATION LONGEST SHORTEST < "$PROGRESS_FILE"
            SUCCESS_COUNT=$((CURRENT > 0 ? CURRENT - 1 : 0))
            BASE_REF="N/A"
        elif [ "$count" -eq 6 ]; then
            # Intermediate format
            read -r CURRENT TOTAL SUCCESS_COUNT TOTAL_SUCCESS_DURATION LONGEST SHORTEST < "$PROGRESS_FILE"
            BASE_REF="N/A"
        else
            # Modern format with full context
            read -r CURRENT TOTAL SUCCESS_COUNT TOTAL_SUCCESS_DURATION LONGEST SHORTEST BASE_REF _ < "$PROGRESS_FILE"
        fi

        # Apply robust defaults if any field is missing or invalid.
        CURRENT=${CURRENT:-0}
        TOTAL=${TOTAL:-0}
        SUCCESS_COUNT=${SUCCESS_COUNT:-0}
        TOTAL_SUCCESS_DURATION=${TOTAL_SUCCESS_DURATION:-0}
        LONGEST=${LONGEST:-0}
        SHORTEST=${SHORTEST:-999999}
        BASE_REF=${BASE_REF:-"N/A"}
    else
        # Initialize fresh state
        CURRENT=0
        TOTAL=0
        SUCCESS_COUNT=0
        TOTAL_SUCCESS_DURATION=0
        LONGEST=0
        SHORTEST=999999
        BASE_REF="N/A"
    fi
}

# save_progress: Writes current stats to disk so they survive across 'git rebase' iterations.
save_progress() {
    echo "$CURRENT $TOTAL $SUCCESS_COUNT $TOTAL_SUCCESS_DURATION $LONGEST $SHORTEST $BASE_REF" > "$PROGRESS_FILE"
}

# --- Process & Signal Handling ---

CURRENT_GRADLE_PID=""

# kill_descendants: Recursively finds and kills all child processes of a given PID.
# Essential for stopping background Gradle builds and their daemon forks.
kill_descendants() {
    local _pid=$1
    if command -v pgrep >/dev/null 2>&1; then
        for _child in $(pgrep -P "$_pid"); do
            kill_descendants "$_child"
        done
    fi
    kill -9 "$_pid" 2>/dev/null
}

# cleanup: Restores terminal state and kills any lingering builds on script exit.
cleanup() {
    show_cursor
    # We only remove the UI flag if we are NOT in a rebase loop (detected via REBASE_VERIFY).
    # This prevents the UI from resetting between every single commit.
    if [ "$REBASE_VERIFY" != "true" ]; then
        rm -f "$UI_TABLE_FLAG"
    fi
    if [ -n "$CURRENT_GRADLE_PID" ]; then
        kill_descendants "$CURRENT_GRADLE_PID"
        CURRENT_GRADLE_PID=""
    fi
}
trap cleanup EXIT INT TERM

# handle_resize: Triggers a UI redraw if the user resizes their terminal window.
handle_resize() {
    rm -f "$UI_TABLE_FLAG"
}
trap handle_resize WINCH

# --- UI Layout & Rendering ---

usage() {
    echo "Usage: $0 {edit|auto|verify} [base]"
    echo ""
    echo "Modes:"
    echo "  edit [base] : Starts interactive rebase from [base] (default: master) and marks all as 'edit'."
    echo "                Use this if you want to manually inspect every single commit."
    echo ""
    echo "  auto [base] : Starts a rebase that runs 'verify' on every commit automatically."
    echo "                Stops ONLY if a build fails. Recommended for long branches."
    echo "                If a rebase is already in progress, it will resume it."
    echo ""
    echo "  verify [--no-increment] : Runs the build check: $BUILD_CMD"
    echo "                            Returns 0 on success, 1 on failure."
    exit 1
}

# format_time: Converts seconds into a human-readable H/M/S string.
format_time() {
    local s=$1
    if [ -z "$s" ] || [ "$s" -eq 999999 ] || [ "$s" -le 0 ]; then
        echo "N/A"
        return
    fi
    printf "%02dh %02dm %02ds" $((s/3600)) $((s%3600/60)) $((s%60)) | sed 's/^00h //' | sed 's/^00m //'
}

# clear_ui: Hard-clears the UI area. Used at the start of a rebase or on failure.
clear_ui() {
    if [ -f "$UI_TABLE_FLAG" ]; then
        printf "\033[%dF" "$UI_HEIGHT"  # Move cursor up to the top of the table.
        for i in $(seq 1 "$UI_HEIGHT"); do echo -e "\033[K"; done # Clear each line.
        printf "\033[%dF" "$UI_HEIGHT"  # Move cursor back up.
        rm -f "$UI_TABLE_FLAG"
    fi
}

# print_ui: Renders the entire status table.
# Uses a buffer to perform an atomic write, which eliminates redraw flickering.
print_ui() {
    local status="$1"   # Status text (e.g. Building, SUCCESS, FAILED)
    local pct="$2"      # Progress percentage string (e.g. 45%)
    local elapsed="$3"  # Seconds since the start of the current build
    local color=$GREEN
    local EL="\033[K"   # ANSI "Erase to End of Line" code.
    local UI_BUFFER=""

    # Check terminal height for adaptive layout.
    local term_height=$(tput lines 2>/dev/null || echo 24)
    if [ "$term_height" -lt 22 ]; then
        # Minimalist "Compact Mode" for small terminal windows.
        if [ "$UI_INITIALIZED" == "true" ] || [ -f "$UI_TABLE_FLAG" ]; then
            printf "\033[2F"
        fi
        UI_INITIALIZED="true"
        touch "$UI_TABLE_FLAG"
        printf "| %-10s | %-5s | %-30s |${EL}\n" "$status" "$pct" "$(git log -1 --oneline | cut -c 1-30)"
        printf "| Progress: %d/%d | Time: %s | Remaining: %s |${EL}\n" "$CURRENT" "$TOTAL" "$(format_time $elapsed)" "$(format_time $((REMAINING_TIME - elapsed)))"
        return
    fi

    # Pick color based on status
    if [[ "$status" == *"Cleaning"* ]]; then
        color=$YELLOW
    elif [[ "$status" == *"FAILED"* ]]; then
        color=$RED
    fi

    # Calculate real-time statistics
    local est_end_text="N/A"
    local dynamic_remaining=$REMAINING_TIME
    if [ -n "$REMAINING_TIME" ] && [ "$REMAINING_TIME" -gt 0 ]; then
        # Ticking "Remaining Time": Total Estimate - Elapsed in current build.
        dynamic_remaining=$((REMAINING_TIME - elapsed))
        [ "$dynamic_remaining" -lt 0 ] && dynamic_remaining=0

        # Stable "Estimated End Time": Build Start Time + Total Remaining.
        local start_time=$(date +%s)
        [ -n "$elapsed" ] && start_time=$((start_time - elapsed))
        est_end_text=$(perl -e 'use POSIX qw(strftime); print strftime("%Hh%Mm%Ss", localtime($ARGV[0] + $ARGV[1]))' "$start_time" "$REMAINING_TIME")
    fi

    # Progress Bar Helpers
    generate_bar() {
        local filled=$1
        local char=$2
        local res=""
        for ((i=0; i<filled; i++)); do res+="$char"; done
        echo -n "$res"
    }

    local bar_width=44
    local filled_commits=0
    local commit_pct=0
    if [ "$TOTAL" -gt 0 ]; then
        # Use SUCCESS_COUNT for the "Done" percentage to accurately reflect completed work.
        filled_commits=$(( (SUCCESS_COUNT * bar_width) / TOTAL ))
        commit_pct=$(( (SUCCESS_COUNT * 100) / TOTAL ))
    fi
    local commit_bar=$(generate_bar $filled_commits "█")
    local empty_bar_width=$((bar_width - filled_commits))
    local empty_bar=$(generate_bar $empty_bar_width "░")

    local build_pct_num=0
    [[ "$pct" =~ ^[0-9]+%$ ]] && build_pct_num=$(echo "$pct" | tr -d '%')
    local filled_build=$(( (build_pct_num * bar_width) / 100 ))
    local build_bar=$(generate_bar $filled_build "█")
    local empty_build_width=$((bar_width - filled_build))
    local empty_build=$(generate_bar $empty_build_width "░")

    # Buffer construction for the "Rich UI" table.
    local current_branch=$(git rev-parse --abbrev-ref HEAD)
    local rebase_header_line
    # Exact padding calculation to ensure the header aligns perfectly with the dashed borders.
    printf -v rebase_header_line "| REBASE: %-22s -> %-23s |${EL}\n" "$(echo "$current_branch" | cut -c 1-22)" "$(echo "$BASE_REF" | cut -c 1-23)"

    UI_BUFFER+="+-----------------------------------------------------------+${EL}\n"
    UI_BUFFER+="$rebase_header_line"
    UI_BUFFER+="+-----------------------------------------------------------+${EL}\n"
    UI_BUFFER+="| PROGRESS                                                  |${EL}\n"
    UI_BUFFER+="+----------+------------------------------------------------+${EL}\n"
    printf -v _line "| Progress | %-46s |${EL}\n" "[$CURRENT/$TOTAL]"; UI_BUFFER+="$_line"
    printf -v _line "|          | [${GREEN}%s${NC}${DARK_GREEN}%s${NC}] |${EL}\n" "$commit_bar" "$empty_bar"; UI_BUFFER+="$_line"
    printf -v _line "| Done     | %-46s |${EL}\n" "${commit_pct}%"; UI_BUFFER+="$_line"

    UI_BUFFER+="+----------+------------------------------------------------+${EL}\n"
    UI_BUFFER+="| CURRENT BUILD                                             |${EL}\n"
    UI_BUFFER+="+----------+------------------------------------------------+${EL}\n"
    printf -v _line "| Commit   | %-46s |${EL}\n" "$(git log -1 --oneline | cut -c 1-46)"; UI_BUFFER+="$_line"
    printf -v _line "| Status   | ${color}%-46s${NC} |${EL}\n" "$status ($pct)"; UI_BUFFER+="$_line"
    printf -v _line "|          | [${DARK_GREEN}%s${NC}${DARK_GREEN}%s${NC}] |${EL}\n" "$build_bar" "$empty_build"; UI_BUFFER+="$_line"
    printf -v _line "| Time     | ${color}%-46s${NC} |${EL}\n" "$(format_time $elapsed)"; UI_BUFFER+="$_line"

    UI_BUFFER+="+----------+----------+-------------------------------------+${EL}\n"
    UI_BUFFER+="| STATISTICS          | VALUES                              |${EL}\n"
    UI_BUFFER+="+---------------------+-------------------------------------+${EL}\n"
    printf -v _line "| Total Time          | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $TOTAL_SUCCESS_DURATION)"; UI_BUFFER+="$_line"
    printf -v _line "| Min / Max           | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $SHORTEST) / $(format_time $LONGEST)"; UI_BUFFER+="$_line"
    printf -v _line "| Avg (Success)       | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $AVG)"; UI_BUFFER+="$_line"
    printf -v _line "| Remaining           | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $dynamic_remaining)"; UI_BUFFER+="$_line"
    printf -v _line "| Estimated End       | ${GREEN}%-35s${NC} |${EL}\n" "$est_end_text"; UI_BUFFER+="$_line"
    UI_BUFFER+="+---------------------+-------------------------------------+${EL}\n"

    # Atomic write: Move cursor up UI_HEIGHT lines and overwrite the existing table.
    if [ "$UI_INITIALIZED" == "true" ] || [ -f "$UI_TABLE_FLAG" ]; then
        printf "\033[%dF" "$UI_HEIGHT"
    fi
    UI_INITIALIZED="true"
    touch "$UI_TABLE_FLAG"
    printf "%b" "$UI_BUFFER"
}

# --- Main Entry Point Logic ---

if [ "$#" -lt 1 ]; then
    usage
fi

MODE=$1
BASE=${2:-master}

# Clean up build log at the start of a new rebase session.
if [[ "$MODE" == "auto" || "$MODE" == "edit" ]]; then
    rm -f "$BUILD_LOG"
fi

case $MODE in
    edit)
        echo "Starting interactive rebase from $BASE..."
        # Portable 'pick' to 'edit' transformation using Perl.
        GIT_SEQUENCE_EDITOR="perl -i -pe 's/^pick/edit/g'" git rebase -i "$BASE"
        ;;
    auto)
        # Check if a rebase is already in progress to allow resumption.
        if [ -d "$(git rev-parse --git-path rebase-merge)" ] || [ -d "$(git rev-parse --git-path rebase-apply)" ]; then
            echo "Resuming existing rebase..."
            # verify --no-increment checks current state without counting it as a new commit checked.
            if "$0" verify --no-increment; then
                echo ""
                echo "Build successful. Do you want to apply changes to the branch and restart verification from here?"
                read -p "Press [y] to checkpoint and restart, any other key to just continue: " -n 1 -r
                echo ""
                if [[ "$REPLY" =~ ^[Yy]$ ]]; then
                    # Checkpoint logic: save current HEAD, remove 'exec' lines from rebase-todo, and restart verification.
                    CORRECTED_COMMIT=$(git rev-parse HEAD)

                    if [ -f "$TODO_FILE" ]; then
                        perl -i -pe 's/^exec.*//' "$TODO_FILE"
                    fi

                    echo "Saving changes..."
                    git rebase --continue

                    echo "Fix applied. Restarting verification loop..."
                    "$0" auto "$CORRECTED_COMMIT"
                else
                    git rebase --continue
                fi
            else
                exit 1
            fi
        else
            # Start a brand new rebase with automated verification.
            echo "Starting automated build check from $BASE..."
            TOTAL=$(git rev-list --count "$BASE..HEAD")
            CURRENT=0
            SUCCESS_COUNT=0
            TOTAL_SUCCESS_DURATION=0
            LONGEST=0
            SHORTEST=999999
            BASE_REF="$BASE"
            save_progress
            echo "The rebase will stop automatically if a build fails. ($TOTAL commits to check)"
            # Use git rebase -x (exec) to automatically run the 'verify' mode on every commit.
            SCRIPT_PATH=$(realpath "$0")
            git rebase -q "$BASE" -x "REBASE_VERIFY=true \"$SCRIPT_PATH\" verify"
        fi

        # Cleanup persistence files if the rebase is finally complete.
        if [ ! -d "$(git rev-parse --git-path rebase-merge)" ] && [ ! -d "$(git rev-parse --git-path rebase-apply)" ]; then
            # Print a final session summary before cleaning up.
            load_progress
            echo -e "\n\n🚀 REBASE VERIFICATION COMPLETE!"
            echo -e "+---------------------+-------------------------------------+"
            printf "| Total Commits       | %-35d |\n" "$TOTAL"
            printf "| Successes           | %-35d |\n" "$SUCCESS_COUNT"
            printf "| Total Time          | %-35s |\n" "$(format_time $TOTAL_SUCCESS_DURATION)"
            printf "| Min / Max Duration  | %s / %s |\n" "$(format_time $SHORTEST)" "$(format_time $LONGEST)"
            if [ "$SUCCESS_COUNT" -gt 0 ]; then
                SUMMARY_AVG=$((TOTAL_SUCCESS_DURATION / SUCCESS_COUNT))
                printf "| Average Duration    | %-35s |\n" "$(format_time $SUMMARY_AVG)"
            fi
            echo -e "+---------------------+-------------------------------------+"

            rm -f "$PROGRESS_FILE"
            rm -f "$UI_TABLE_FLAG"
        fi
        ;;
    verify)
        # Entry point for the 'git rebase -x' loop.
        hide_cursor
        load_progress

        # Reserve space for the table if this is the very first iteration.
        if [ ! -f "$UI_TABLE_FLAG" ]; then
            for i in $(seq 1 "$UI_HEIGHT"); do echo ""; done
            printf "\033[%dF" "$UI_HEIGHT"
        fi

        if [ "$2" != "--no-increment" ]; then
            CURRENT=$((CURRENT + 1))
            save_progress
        fi

        # Calculate initial statistics for estimation.
        # Uses success-only data (excluding the longest build as an outlier for smoother prediction).
        if [ "$SUCCESS_COUNT" -gt 2 ]; then
            AVG=$(((TOTAL_SUCCESS_DURATION - LONGEST) / (SUCCESS_COUNT - 1)))
            REMAINING_COUNT=$((TOTAL - CURRENT + 1))
            REMAINING_TIME=$((AVG * REMAINING_COUNT))
        elif [ "$SUCCESS_COUNT" -gt 0 ]; then
            AVG=$((TOTAL_SUCCESS_DURATION / SUCCESS_COUNT))
            REMAINING_COUNT=$((TOTAL - CURRENT + 1))
            REMAINING_TIME=$((AVG * REMAINING_COUNT))
        else
            AVG=0
            REMAINING_TIME=0
        fi

        # run_build: Executes a build command and monitors its progress in real-time.
        run_build() {
            local cmd="$1"
            local log_file="$2"
            local label="$3"
            local start_time=$(date +%s)

            # Start gradle in the background with a rich console to get the progress updates.
            $cmd --console rich > "$log_file" 2>&1 &
            CURRENT_GRADLE_PID=$!

            # Monitor loop: polls the log file for percentage updates.
            while kill -0 $CURRENT_GRADLE_PID 2>/dev/null; do
                local now=$(date +%s)
                local elapsed=$((now - start_time))

                # Robustly extract the latest status percentage from the build log.
                # Strips null bytes (common in binary-like rich output) to avoid shell warnings.
                local last_chunk=$(tail -c 1024 "$log_file" | tr -d '\000')
                local raw_pct=$(echo "$last_chunk" | grep -o "[0-9]\{1,3\}%" | tail -n 1 | tr -d '%')
                [ -z "$raw_pct" ] && raw_pct=0

                # Phase Scaling: Gradle reports 0-100% for each phase.
                # We map this to a continuous 0-100% build progress for the UI.
                local scaled_pct=$raw_pct
                local phase="$label"
                if [[ "$last_chunk" == *"CONFIGURING"* ]]; then
                    scaled_pct=$((raw_pct / 2)) # Configuring is mapped to 0-50%
                    phase="Configuring"
                elif [[ "$last_chunk" == *"EXECUTING"* ]]; then
                    scaled_pct=$((50 + (raw_pct / 2))) # Executing is mapped to 50-100%
                    phase="Executing"
                fi

                print_ui "$phase" "${scaled_pct}%" "$elapsed"
                sleep 0.5
            done
            wait $CURRENT_GRADLE_PID
            local status=$?
            CURRENT_GRADLE_PID=""
            return $status
        }

        # Step 1: Initial optimized build attempt.
        START=$(date +%s)
        run_build "$BUILD_CMD" "$BUILD_LOG" "Building"
        EXIT_CODE=$?
        END=$(date +%s)
        DURATION=$((END - START))

        if [ $EXIT_CODE -eq 0 ]; then
            # Success: Update statistics and move to the next commit.
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
            TOTAL_SUCCESS_DURATION=$((TOTAL_SUCCESS_DURATION + DURATION))
            [ "$DURATION" -gt "$LONGEST" ] && LONGEST=$DURATION
            [ "$DURATION" -lt "$SHORTEST" ] && SHORTEST=$DURATION
            save_progress

            print_ui "SUCCESS" "100%" "$DURATION"
            show_cursor
            exit 0
        else
            # Failure: Retry after a 'clean' build (common requirement in large rebases).
            print_ui "FAILED" "---" "$DURATION"
            show_cursor

            # Cleanly remove the failing UI table and reset state before printing warning.
            clear_ui
            UI_INITIALIZED="false"

            echo -e "\n⚠️  Build failed. Retrying with 'clean' and no optimized arguments..."
            hide_cursor

            # Reserve space for the new retry table to ensure correct anchoring.
            for i in $(seq 1 "$UI_HEIGHT"); do echo ""; done
            printf "\033[%dF" "$UI_HEIGHT"

            echo "Cleaning..." >> "$BUILD_LOG"
            "$ROOT_DIR/gradlew" clean --console rich >> "$BUILD_LOG" 2>&1

            START_CLEAN=$(date +%s)
            run_build "$BASE_BUILD_CMD" "$BUILD_LOG" "Cleaning & Rebuilding"
            EXIT_CODE=$?
            END_CLEAN=$(date +%s)
            DURATION_CLEAN=$((END_CLEAN - START_CLEAN))

            if [ $EXIT_CODE -eq 0 ]; then
                # Success after clean: Update statistics.
                SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
                TOTAL_SUCCESS_DURATION=$((TOTAL_SUCCESS_DURATION + DURATION + DURATION_CLEAN))
                [ "$DURATION_CLEAN" -gt "$LONGEST" ] && LONGEST=$DURATION_CLEAN
                [ "$DURATION_CLEAN" -lt "$SHORTEST" ] && SHORTEST=$DURATION_CLEAN
                save_progress

                print_ui "SUCCESS (after clean)" "100%" "$DURATION_CLEAN"
                show_cursor
                exit 0
            else
                # Hard failure: stop the rebase and prompt the user for fixes.
                print_ui "FAILED (after clean)" "---" "$DURATION_CLEAN"
                show_cursor
                echo -e "\n❌ BUILD FAILED (even after clean)"
                echo ""
                echo "Fix the error, then run:"
                echo "  git add ."
                echo "  git commit --amend --no-edit"
                echo "  \"$0\" auto"
                exit 1
            fi
        fi
        ;;
    *)
        usage
        ;;
esac
