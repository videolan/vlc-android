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
# It provides real-time progress, statistics, and smart retries on failure:
#   - missing dependencies in offline mode -> retry online, without 'clean'
#   - compilation errors                   -> fail immediately, retrying cannot fix them
#   - anything else                        -> retry after a 'clean' (build cache kept)
# Commits that only change files without build impact (docs, CI, scripts, tests) are skipped.
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
# BASE_BUILD_CMD is the plain build, used for the 'clean' retry.
# BUILD_CMD includes performance optimizations for iterative checks (offline first).
# ONLINE_BUILD_CMD is the same without --offline, used when dependencies are missing from the cache.
# CLEAN_REBUILD_CMD is used after a 'clean'. It keeps the build cache: cache entries are keyed
# by their inputs, so they are safe to reuse, and they make the rebuild much faster.
BASE_BUILD_CMD=("$ROOT_DIR/gradlew" ":application:app:assembleDebug")
OPTIMIZED_ARGS=("--offline" "--build-cache" "--configuration-cache" "--parallel")
ONLINE_ARGS=("--build-cache" "--configuration-cache" "--parallel")
BUILD_CMD=("${BASE_BUILD_CMD[@]}" "${OPTIMIZED_ARGS[@]}")
ONLINE_BUILD_CMD=("${BASE_BUILD_CMD[@]}" "${ONLINE_ARGS[@]}")
CLEAN_REBUILD_CMD=("${BASE_BUILD_CMD[@]}" "--build-cache")

# Commits without build impact.
# A commit is skipped (not built) when EVERY file it changes matches NO_BUILD_IMPACT_PATTERNS
# and none matches ALWAYS_BUILD_PATTERNS. Its result is then the same as the previous commit's,
# which was already built. Patterns are bash extended regexes on the path from the repository root.
# Run with CHECK_ALL_COMMITS=true to build every commit anyway.
NO_BUILD_IMPACT_PATTERNS=(
    '\.md$'                          # Documentation (README, issue templates...).
    '^(NEWS|COPYING|\.mailmap)$'     # Repository metadata.
    '(^|/)\.gitignore$'              # Never read by Gradle.
    '^\.gitlab-ci\.yml$' '^\.gitlab/' # CI configuration, only used by the CI.
    '^buildsystem/.*\.sh$'          # Scripts: Gradle never runs them. Other buildsystem files
                                    # (publish.gradle, remoteaccess.properties) are read by Gradle.
    # Test sources: assembleDebug doesn't compile them.
    '^(application/[^/]+|medialibrary)/(src/test|src/androidTest|test|androidTest|test-common)/'
)
# Files packaged in the APK whatever their extension: always built.
ALWAYS_BUILD_PATTERNS=('(^|/)assets/' '(^|/)res/')

# Persistence directory and files.
PERSISTENCE_DIR="$ROOT_DIR/buildsystem/.check-commits"
mkdir -p "$PERSISTENCE_DIR"

PROGRESS_FILE="$PERSISTENCE_DIR/progress"   # Stores stats between rebase iterations.
UI_TABLE_FLAG="$PERSISTENCE_DIR/ui_present"  # Acts as a semaphore to avoid re-drawing headers.
BUILD_LOG="$PERSISTENCE_DIR/build.log"       # Captured output of the current Gradle build.
RUNNER_COPY="$PERSISTENCE_DIR/runner.sh"     # Self-copy to ensure availability during rebase.
RESULTS_FILE="$PERSISTENCE_DIR/results.tsv"  # One line per checked commit, for the final report.
REPORT_FILE="$PERSISTENCE_DIR/report.md"     # Report written when the whole rebase has been checked.

# Git Rebase Todo file.
# We link the actual git rebase file into our persistence folder for consistency.
ACTUAL_TODO_FILE="$(git rev-parse --git-path rebase-merge 2>/dev/null)/git-rebase-todo"
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

# generate_bar: Stores in the variable named by $3 a bar of $1 times the character $2.
# Writes to a variable instead of stdout to avoid a subshell on every UI refresh.
generate_bar() {
    local filled=$1
    local char=$2
    local -n _bar=$3
    _bar=""
    for ((i=0; i<filled; i++)); do _bar+="$char"; done
}

# The height of our "Rich UI" table. Centralized here for cursor movement calculations.
UI_HEIGHT=24

# --- Core Build Logic ---

# run_build: Executes a build command and monitors its progress in real-time.
run_build() {
    local -n _cmd=$1
    local log_file="$2"
    local label="$3"
    local start_time
    start_time=$(date +%s)

    # Start gradle in the background with a rich console to get the progress updates.
    # We append to the log file to avoid wiping previous attempts (like 'clean' output).
    "${_cmd[@]}" --console rich >> "$log_file" 2>&1 &
    CURRENT_GRADLE_PID=$!

    # Monitor loop: polls the log file for percentage updates.
    while kill -0 $CURRENT_GRADLE_PID 2>/dev/null; do
        local now
        now=$(date +%s)
        local elapsed=$((now - start_time))

        # Robustly extract the latest status percentage from the build log.
        # Strips null bytes (common in binary-like rich output) to avoid shell warnings.
        local last_chunk
        last_chunk=$(tail -c 1024 "$log_file" | tr -d '\000')
        # Last "NN%" of the chunk: the greedy '.*' makes the regex match the last occurrence.
        local raw_pct=0
        [[ "$last_chunk" =~ .*[^0-9]([0-9]{1,3})% ]] && raw_pct=${BASH_REMATCH[1]}

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

# --- Commits Without Build Impact ---

# matches_any: Returns 0 if the path $1 matches one of the regexes of the array named $2.
matches_any() {
    local path="$1"
    local -n _patterns=$2
    local pattern
    for pattern in "${_patterns[@]}"; do
        [[ "$path" =~ $pattern ]] && return 0
    done
    return 1
}

# is_skippable_commit: Returns 0 if HEAD only changes files without build impact (see
# NO_BUILD_IMPACT_PATTERNS). Commits without changes are skippable too.
is_skippable_commit() {
    [ "$CHECK_ALL_COMMITS" == "true" ] && return 1
    # The root commit has no parent to compare with.
    git rev-parse -q --verify HEAD~1 >/dev/null || return 1
    local file
    while IFS= read -r file; do
        matches_any "$file" ALWAYS_BUILD_PATTERNS && return 1
        matches_any "$file" NO_BUILD_IMPACT_PATTERNS || return 1
    done < <(git diff-tree --no-commit-id --name-only -r HEAD~1 HEAD)
    return 0
}

# --- Failure Analysis ---

# log_size: Current size of the build log in bytes, used to analyse only the output of the last attempt.
# Uses 'wc -c' rather than 'stat', whose options differ between GNU and BSD/macOS.
log_size() {
    if [ -f "$BUILD_LOG" ]; then
        wc -c < "$BUILD_LOG" | tr -d ' '
    else
        echo 0
    fi
}

# clean_log_since: Prints the build log from a byte offset, without null bytes and ANSI escape codes.
# Uses Perl for the escape codes: BSD/macOS sed does not understand '\x1b'.
clean_log_since() {
    local offset=${1:-0}
    tail -c +$((offset + 1)) "$BUILD_LOG" | tr -d '\000' | perl -pe 's/\e\[[0-9;?]*[A-Za-z]//g'
}

# Kotlin (including KSP/kapt) and Java compilation error lines.
COMPILE_ERROR_PATTERN='(^|[[:space:]])e: (file:|/|\[ksp\])|\.(java|kt):[0-9]+: error:|Compilation error|Compilation failed'
# Dependencies missing from the Gradle cache (offline mode) or not downloadable.
DEPENDENCY_ERROR_PATTERN='No cached version|available for offline mode|Could not resolve|Could not download|Could not find [A-Za-z0-9_.-]+:'

# classify_failure: Prints the failure type of the attempt that started at the given log offset:
#   "compile"    : real source errors, retrying cannot fix them.
#   "dependency" : missing artifacts in offline mode, an online retry fixes them.
#   "other"      : anything else (stale outputs, cache issues...), a 'clean' may fix them.
# Compilation errors located only in generated sources (build/generated/) are "other": they
# usually come from stale files generated by another commit (e.g. KSP/Hilt output left in
# build/ when going back to a commit without Hilt), and a 'clean' fixes them.
classify_failure() {
    local attempt_log error_files
    attempt_log=$(clean_log_since "$1")
    if echo "$attempt_log" | grep -qE "$COMPILE_ERROR_PATTERN"; then
        error_files=$(echo "$attempt_log" | grep -E "$COMPILE_ERROR_PATTERN" | grep -oE '/[^ :]+\.(java|kt)')
        if [ -n "$error_files" ] && ! echo "$error_files" | grep -qv '/build/generated/'; then
            echo "other"
        else
            echo "compile"
        fi
    elif echo "$attempt_log" | grep -qE "$DEPENDENCY_ERROR_PATTERN"; then
        echo "dependency"
    else
        echo "other"
    fi
}

# print_errors: Shows the first relevant error lines of the attempt that started at the given log offset.
print_errors() {
    local errors
    errors=$(clean_log_since "$1" \
        | grep -E -A3 "$COMPILE_ERROR_PATTERN|What went wrong" \
        | grep -vE '^--$' | awk '!seen[$0]++' | head -n 25)
    if [ -n "$errors" ]; then
        echo ""
        echo "First errors (full log: $BUILD_LOG):"
        echo "$errors"
    fi
}

# --- State Management ---

# load_progress: Reads rebase statistics from the disk.
load_progress() {
    if [ -f "$PROGRESS_FILE" ]; then
        read -r CURRENT TOTAL SUCCESS_COUNT TOTAL_SUCCESS_DURATION LONGEST SHORTEST BASE_REF SKIPPED_COUNT _ < "$PROGRESS_FILE"
    fi

    # Apply robust defaults if any field is missing or invalid.
    CURRENT=${CURRENT:-0}
    TOTAL=${TOTAL:-0}
    SUCCESS_COUNT=${SUCCESS_COUNT:-0}
    TOTAL_SUCCESS_DURATION=${TOTAL_SUCCESS_DURATION:-0}
    LONGEST=${LONGEST:-0}
    SHORTEST=${SHORTEST:-999999}
    BASE_REF=${BASE_REF:-"N/A"}
    SKIPPED_COUNT=${SKIPPED_COUNT:-0}
}

# save_progress: Writes current stats to disk so they survive across 'git rebase' iterations.
save_progress() {
    echo "$CURRENT $TOTAL $SUCCESS_COUNT $TOTAL_SUCCESS_DURATION $LONGEST $SHORTEST $BASE_REF $SKIPPED_COUNT" > "$PROGRESS_FILE"
}

# record_success: Updates the statistics with a successful build, renders the UI and exits.
record_success() {
    local label="$1"
    local duration="$2"
    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    TOTAL_SUCCESS_DURATION=$((TOTAL_SUCCESS_DURATION + duration))
    [ "$duration" -gt "$LONGEST" ] && LONGEST=$duration
    [ "$duration" -lt "$SHORTEST" ] && SHORTEST=$duration
    save_progress
    record_result "$label"

    print_ui "$label" "100%" "$duration"
    show_cursor
    exit 0
}

# record_result: Appends the result of the current commit to the results file, for the final report.
# Tab-separated: short hash, status, time spent on the commit (all attempts, in seconds), subject.
record_result() {
    local status="$1"
    local spent=$(( $(date +%s) - VERIFY_START ))
    printf '%s\t%s\t%s\t%s\n' "$(git rev-parse --short HEAD)" "$status" "$spent" \
        "$(git log -1 --format=%s)" >> "$RESULTS_FILE"
}

# write_report: Writes the Markdown report of the whole check from the results file.
write_report() {
    local hash status spent subject
    local built=0 online=0 after_clean=0 skipped=0 failures=0
    while IFS=$'\t' read -r hash status spent subject; do
        case "$status" in
            "SUCCESS (online)") built=$((built + 1)); online=$((online + 1)) ;;
            "SUCCESS (after clean)") built=$((built + 1)); after_clean=$((after_clean + 1)) ;;
            SUCCESS*) built=$((built + 1)) ;;
            SKIPPED*) skipped=$((skipped + 1)) ;;
            FAILED*) failures=$((failures + 1)) ;;
        esac
    done < "$RESULTS_FILE"

    # Table rows: '|' in commit subjects would break the Markdown table, it is escaped.
    # The escape is stored in a variable: a literal '\|' replacement would lose its backslash.
    local escaped_pipe='\|'
    print_rows() {
        while IFS=$'\t' read -r hash status spent subject; do
            local time_text="-"
            [[ "$status" != SKIPPED* ]] && time_text=$(format_time "$spent")
            echo "| \`$hash\` | $status | $time_text | ${subject//|/$escaped_pipe} |"
        done
    }

    {
        echo "# Build check report"
        echo ""
        echo "- Date: $(date '+%Y-%m-%d %H:%M')"
        echo "- Branch: \`$(git rev-parse --abbrev-ref HEAD)\`, rebased onto \`$BASE_REF\`"
        echo "- Build: \`${BUILD_CMD[*]/#"$ROOT_DIR"\//}\`"
        echo ""
        echo "## Summary"
        echo ""
        echo "| | |"
        echo "|---|---|"
        echo "| Commits | $TOTAL |"
        echo "| Built | $built (online retry: $online, after clean: $after_clean) |"
        echo "| Skipped (no build impact) | $skipped |"
        echo "| Failures fixed during the check | $failures |"
        echo "| Total build time | $(format_time "$TOTAL_SUCCESS_DURATION") |"
        if [ "$SUCCESS_COUNT" -gt 0 ]; then
            echo "| Average / Min / Max | $(format_time $((TOTAL_SUCCESS_DURATION / SUCCESS_COUNT))) / $(format_time "$SHORTEST") / $(format_time "$LONGEST") |"
        fi
        if [ "$failures" -gt 0 ]; then
            echo ""
            echo "## Failures"
            echo ""
            echo "The hash is the one before the fix."
            echo ""
            echo "| Commit | Status | Time | Subject |"
            echo "|---|---|---|---|"
            awk -F '\t' '$2 ~ /^FAILED/' "$RESULTS_FILE" | print_rows
        fi
        echo ""
        echo "## Slowest builds"
        echo ""
        echo "| Commit | Status | Time | Subject |"
        echo "|---|---|---|---|"
        awk -F '\t' '$2 ~ /^SUCCESS/' "$RESULTS_FILE" | sort -t $'\t' -k3,3nr | head -n 5 | print_rows
        echo ""
        echo "## All commits"
        echo ""
        echo "| Commit | Status | Time | Subject |"
        echo "|---|---|---|---|"
        print_rows < "$RESULTS_FILE"
    } > "$REPORT_FILE"
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

# read_term_height: Caches the terminal height, so that the UI refresh doesn't run 'tput' every time.
read_term_height() {
    TERM_HEIGHT=$(tput lines 2>/dev/null || echo 24)
}

# handle_resize: Triggers a UI redraw if the user resizes their terminal window.
handle_resize() {
    rm -f "$UI_TABLE_FLAG"
    read_term_height
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
    echo "                When all commits are checked, writes a Markdown report to:"
    echo "                $REPORT_FILE"
    echo ""
    echo "  verify [--no-increment] : Runs the build check: ${BUILD_CMD[*]}"
    echo "                            On failure: retries online if dependencies are missing,"
    echo "                            stops on compilation errors, otherwise retries after a 'clean'."
    echo "                            Skips commits without build impact (docs, CI, scripts, tests),"
    echo "                            unless CHECK_ALL_COMMITS=true."
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
    # Leading zero hours and minutes are omitted (e.g. "05m 03s"), without spawning 'sed'.
    if [ "$s" -ge 3600 ]; then
        printf "%02dh %02dm %02ds" $((s/3600)) $((s%3600/60)) $((s%60))
    elif [ "$s" -ge 60 ]; then
        printf "%02dm %02ds" $((s/60)) $((s%60))
    else
        printf "%02ds" "$s"
    fi
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

# prepare_retry_ui: Removes the failing UI table, prints a warning and reserves space for the next table.
prepare_retry_ui() {
    local message="$1"
    clear_ui
    UI_INITIALIZED="false"

    echo -e "\n⚠️  $message"
    hide_cursor

    # Reserve space for the new retry table to ensure correct anchoring.
    for i in $(seq 1 "$UI_HEIGHT"); do echo ""; done
    printf "\033[%dF" "$UI_HEIGHT"
}

# print_ui: Renders the entire status table.
# Redraws the UI by moving the cursor up and overwriting.
# It runs every 0.5s during a build, so it avoids external commands: the commit line, the branch
# and the terminal height are cached, and dates and strings use bash builtins.
print_ui() {
    local status="$1"   # Status text (e.g. Building, SUCCESS, FAILED)
    local pct="$2"      # Progress percentage string (e.g. 45%)
    local elapsed="$3"  # Seconds since the start of the current build
    local color=$GREEN
    local EL="\033[K"   # ANSI "Erase to End of Line" code.
    local UI_BUFFER=""

    # The commit and branch don't change while this process runs: read them only once.
    [ -z "$UI_COMMIT_LINE" ] && UI_COMMIT_LINE=$(git log -1 --oneline)
    [ -z "$UI_BRANCH" ] && UI_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    [ -z "$TERM_HEIGHT" ] && read_term_height

    # Check terminal height for adaptive layout.
    if [ "$TERM_HEIGHT" -lt 22 ]; then
        # Minimalist "Compact Mode" for small terminal windows.
        if [ "$UI_INITIALIZED" == "true" ] || [ -f "$UI_TABLE_FLAG" ]; then
            printf "\033[2F"
        fi
        UI_INITIALIZED="true"
        [ -f "$UI_TABLE_FLAG" ] || : > "$UI_TABLE_FLAG"
        printf "| %-10s | %-5s | %-30s |${EL}\n" "$status" "$pct" "${UI_COMMIT_LINE:0:30}"
        printf "| Progress: %d/%d | Time: %s | Remaining: %s |${EL}\n" "$CURRENT" "$TOTAL" "$(format_time $elapsed)" "$(format_time $((REMAINING_TIME - elapsed)))"
        return
    fi

    # Pick color based on status
    if [[ "$status" == *"Cleaning"* ]] || [[ "$status" == *"Online"* ]]; then
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
        # printf '%(...)T' is a bash builtin (bash 4.2+), it works the same on GNU and BSD/macOS.
        local start_time
        printf -v start_time '%(%s)T' -1
        [ -n "$elapsed" ] && start_time=$((start_time - elapsed))
        printf -v est_end_text '%(%Hh%Mm%Ss)T' $((start_time + REMAINING_TIME))
    fi

    local bar_width=44
    local filled_commits=0
    local commit_pct=0
    if [ "$TOTAL" -gt 0 ]; then
        # Use the built and skipped commits for the "Done" percentage to accurately reflect completed work.
        local done_count=$((SUCCESS_COUNT + SKIPPED_COUNT))
        filled_commits=$(( (done_count * bar_width) / TOTAL ))
        commit_pct=$(( (done_count * 100) / TOTAL ))
    fi

    local commit_bar empty_bar
    generate_bar $filled_commits "█" commit_bar
    generate_bar $((bar_width - filled_commits)) "░" empty_bar

    local build_pct_num=0
    [[ "$pct" =~ ^[0-9]+%$ ]] && build_pct_num=${pct%\%}
    local filled_build=$(( (build_pct_num * bar_width) / 100 ))
    local build_bar empty_build
    generate_bar $filled_build "█" build_bar
    generate_bar $((bar_width - filled_build)) "░" empty_build

    # Buffer construction for the "Rich UI" table.
    local rebase_header_line
    # Exact padding calculation to ensure the header aligns perfectly with the dashed borders.
    printf -v rebase_header_line "| REBASE: %-22s -> %-23s |${EL}\n" "${UI_BRANCH:0:22}" "${BASE_REF:0:23}"

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
    printf -v _line "| Commit   | %-46s |${EL}\n" "${UI_COMMIT_LINE:0:46}"; UI_BUFFER+="$_line"
    printf -v _line "| Status   | ${color}%-46s${NC} |${EL}\n" "$status ($pct)"; UI_BUFFER+="$_line"
    printf -v _line "|          | [${DARK_GREEN}%s${NC}${DARK_GREEN}%s${NC}] |${EL}\n" "$build_bar" "$empty_build"; UI_BUFFER+="$_line"
    printf -v _line "| Time     | ${color}%-46s${NC} |${EL}\n" "$(format_time $elapsed)"; UI_BUFFER+="$_line"

    UI_BUFFER+="+----------+----------+-------------------------------------+${EL}\n"
    UI_BUFFER+="| STATISTICS          | VALUES                              |${EL}\n"
    UI_BUFFER+="+---------------------+-------------------------------------+${EL}\n"
    printf -v _line "| Total Time          | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $TOTAL_SUCCESS_DURATION)"; UI_BUFFER+="$_line"
    printf -v _line "| Min / Max (skipped) | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $SHORTEST) / $(format_time $LONGEST) ($SKIPPED_COUNT skipped)"; UI_BUFFER+="$_line"
    printf -v _line "| Avg (Success)       | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $AVG)"; UI_BUFFER+="$_line"
    printf -v _line "| Remaining           | ${GREEN}%-35s${NC} |${EL}\n" "$(format_time $dynamic_remaining)"; UI_BUFFER+="$_line"
    printf -v _line "| Estimated End       | ${GREEN}%-35s${NC} |${EL}\n" "$est_end_text"; UI_BUFFER+="$_line"
    UI_BUFFER+="+---------------------+-------------------------------------+${EL}\n"

    # Atomic write: Move cursor up UI_HEIGHT lines and overwrite the existing table.
    if [ "$UI_INITIALIZED" == "true" ] || [ -f "$UI_TABLE_FLAG" ]; then
        printf "\033[%dF" "$UI_HEIGHT"
    fi
    UI_INITIALIZED="true"
    [ -f "$UI_TABLE_FLAG" ] || : > "$UI_TABLE_FLAG"
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
        # Self-copy to persistence directory to ensure the script remains available
        # even if the rebase checkouts a commit where this script doesn't exist.
        cp "$0" "$RUNNER_COPY"
        chmod +x "$RUNNER_COPY"

        # Check if a rebase is already in progress to allow resumption.
        # We only check for rebase-merge as 'git rebase -x' always uses this backend.
        if [ -d "$(git rev-parse --git-path rebase-merge)" ]; then
            echo "Resuming existing rebase..."
            # verify --no-increment checks current state without counting it as a new commit checked.
            if "$RUNNER_COPY" verify --no-increment; then
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
                    "$RUNNER_COPY" auto "$CORRECTED_COMMIT"
                else
                    git rebase --continue
                fi
            else
                exit 1
            fi
        else
            # Start a brand new rebase with automated verification.
            echo "Starting automated build check from $BASE..."
            # Count only the commits the rebase will actually replay: it drops merge commits and
            # commits whose changes are already in $BASE (cherry-picked or applied upstream).
            TOTAL=$(git rev-list --count --no-merges --cherry-pick --right-only "$BASE...HEAD")
            CURRENT=0
            SUCCESS_COUNT=0
            TOTAL_SUCCESS_DURATION=0
            LONGEST=0
            SHORTEST=999999
            BASE_REF="$BASE"
            SKIPPED_COUNT=0
            save_progress
            : > "$RESULTS_FILE"
            echo "The rebase will stop automatically if a build fails. ($TOTAL commits to check)"
            # Use git rebase -x (exec) to automatically run the 'verify' mode on every commit.
            # We use the copy in PERSISTENCE_DIR to ensure it stays available during the rebase.
            git rebase -q "$BASE" -x "REBASE_VERIFY=true \"$RUNNER_COPY\" verify"
        fi

        # Cleanup persistence files if the rebase is finally complete.
        if [ ! -d "$(git rev-parse --git-path rebase-merge)" ]; then
            # Print a final session summary before cleaning up.
            load_progress
            echo -e "\n\n🚀 REBASE VERIFICATION COMPLETE!"
            echo -e "+---------------------+-------------------------------------+"
            printf "| Total Commits       | %-35d |\n" "$TOTAL"
            printf "| Successes           | %-35d |\n" "$SUCCESS_COUNT"
            printf "| Skipped (no impact) | %-35d |\n" "$SKIPPED_COUNT"
            printf "| Total Time          | %-35s |\n" "$(format_time $TOTAL_SUCCESS_DURATION)"
            printf "| Min / Max Duration  | %s / %s |\n" "$(format_time $SHORTEST)" "$(format_time $LONGEST)"
            if [ "$SUCCESS_COUNT" -gt 0 ]; then
                SUMMARY_AVG=$((TOTAL_SUCCESS_DURATION / SUCCESS_COUNT))
                printf "| Average Duration    | %-35s |\n" "$(format_time $SUMMARY_AVG)"
            fi
            echo -e "+---------------------+-------------------------------------+"

            if [ -s "$RESULTS_FILE" ]; then
                write_report
                echo ""
                echo "📄 Report: $REPORT_FILE"
            fi

            rm -f "$PROGRESS_FILE" "$RESULTS_FILE"
            rm -f "$UI_TABLE_FLAG"
        fi
        ;;
    verify)
        # Entry point for the 'git rebase -x' loop.
        # This script is re-executed as a fresh process for each commit being verified.
        # Statistics and UI state are persisted in the .check-commits directory.
        hide_cursor
        load_progress
        VERIFY_START=$(date +%s)

        # Clear the build log for this iteration.
        : > "$BUILD_LOG"

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


        # Step 0: Commits that only change files without build impact are not built.
        # Their durations are kept out of the statistics, so that they don't skew the estimates.
        if is_skippable_commit; then
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            save_progress
            record_result "SKIPPED"
            print_ui "SKIPPED (no build impact)" "100%" 0
            show_cursor
            exit 0
        fi

        # Step 1: Initial optimized (offline) build attempt.
        ATTEMPT_OFFSET=$(log_size)
        START=$(date +%s)
        run_build BUILD_CMD "$BUILD_LOG" "Building"
        EXIT_CODE=$?
        DURATION=$(( $(date +%s) - START ))

        [ $EXIT_CODE -eq 0 ] && record_success "SUCCESS" "$DURATION"

        print_ui "FAILED" "---" "$DURATION"
        show_cursor
        FAILURE_KIND=$(classify_failure "$ATTEMPT_OFFSET")

        # Step 2: Dependencies missing from the offline cache -> retry online, without 'clean'.
        # Incremental outputs are kept, so this costs little more than downloading the artifacts.
        if [ "$FAILURE_KIND" == "dependency" ]; then
            prepare_retry_ui "Dependencies missing from the offline cache. Retrying online (no 'clean')..."

            ATTEMPT_OFFSET=$(log_size)
            START=$(date +%s)
            run_build ONLINE_BUILD_CMD "$BUILD_LOG" "Online build"
            EXIT_CODE=$?
            DURATION=$(( $(date +%s) - START ))

            [ $EXIT_CODE -eq 0 ] && record_success "SUCCESS (online)" "$DURATION"

            print_ui "FAILED (online)" "---" "$DURATION"
            show_cursor
            FAILURE_KIND=$(classify_failure "$ATTEMPT_OFFSET")
        fi

        # Step 3: Anything that is not a compilation error -> retry after a 'clean'.
        # Compilation errors are skipped: a 'clean' rebuild would only fail again, after a full build.
        if [ "$FAILURE_KIND" != "compile" ]; then
            prepare_retry_ui "Build failed. Retrying after a 'clean' (build cache kept)..."

            echo "Cleaning..." >> "$BUILD_LOG"
            "${BASE_BUILD_CMD[0]}" clean --console rich >> "$BUILD_LOG" 2>&1

            ATTEMPT_OFFSET=$(log_size)
            START=$(date +%s)
            run_build CLEAN_REBUILD_CMD "$BUILD_LOG" "Cleaning & Rebuilding"
            EXIT_CODE=$?
            DURATION=$(( $(date +%s) - START ))

            [ $EXIT_CODE -eq 0 ] && record_success "SUCCESS (after clean)" "$DURATION"

            print_ui "FAILED (after clean)" "---" "$DURATION"
            show_cursor
            echo -e "\n❌ BUILD FAILED (even after clean)"
            record_result "FAILED (after clean)"
        else
            echo -e "\n❌ COMPILATION FAILED (no retry: a 'clean' would not fix it)"
            record_result "FAILED (compile)"
        fi

        # Hard failure: stop the rebase and prompt the user for fixes.
        print_errors "$ATTEMPT_OFFSET"
        echo ""
        echo "Fix the error, then run:"
        echo "  git add ."
        echo "  git commit --amend --no-edit"
        echo "  \"$0\" auto"
        exit 1
        ;;
    *)
        usage
        ;;
esac
