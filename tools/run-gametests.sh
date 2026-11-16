#!/usr/bin/env zsh
# Run the NeoForge gametest suite with guards against the known hang/flake.
#
# Why this exists
# ---------------
# GameTestServer spawns the test grid at an unseeded random position. When a
# chunk never reaches entity-ticking, GameTestInfo.tick never starts that batch
# and never reaches its timeout branch: the run sits at "N tests not started or
# passed" forever while the server spins at ~10k tps and the progress bar fills
# the log at ~130 KB/s. A hung run once produced a 312 MB latest.log.
#
# This runner therefore caps BOTH wall-clock time and log size, kills the whole
# process tree (a bare `timeout` leaves the Gradle daemon and the forked game
# server alive, still writing to the log), and retries a bounded number of
# times. A clean run takes ~20 s and produces a ~110 KB log.
#
# Usage: ./tools/run-gametests.sh [attempts] [seconds-per-attempt]
# Env:   LOGDIR, MAX_BYTES (default 40 MiB)
set -o pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
LOGDIR=${LOGDIR:-${TMPDIR:-/mnt/Barracuda/tmp}/adaptionwheel-gametests}
ATTEMPTS=${1:-4}
MAX_SECONDS=${2:-150}
MAX_BYTES=${MAX_BYTES:-$((40 * 1024 * 1024))}

mkdir -p "$LOGDIR"
cd "$REPO" || exit 1

kill_tree() {
  local pid=$1 child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child"
  done
  kill -TERM "$pid" 2>/dev/null || true
}

for i in $(seq 1 "$ATTEMPTS"); do
  LOG="$LOGDIR/gametest_$i.log"
  rm -f "$LOG"
  # Stale config loops the config tracker forever; a persisted world can stall a
  # whole batch. Both are disposable test artifacts, not the user's runs/server.
  rm -rf runs/gameTestServer/world runs/gameTestServer/config runs/gameTestServer/logs

  echo "== attempt $i/$ATTEMPTS (max ${MAX_SECONDS}s) -> $LOG"
  ./gradlew runGameTestServer --no-daemon > "$LOG" 2>&1 &
  PID=$!
  START=$SECONDS
  REASON=""

  while kill -0 "$PID" 2>/dev/null; do
    if (( SECONDS - START >= MAX_SECONDS )); then REASON="timeout"; break; fi
    if [ -f "$LOG" ]; then
      SIZE=$(stat -c%s "$LOG" 2>/dev/null || echo 0)
      if (( SIZE > MAX_BYTES )); then REASON="log-size"; break; fi
    fi
    sleep 2
  done

  if [ -n "$REASON" ]; then
    echo "   aborted: $REASON (pid $PID); killing process tree"
    kill_tree "$PID"
    sleep 2
    for p in $(pgrep -P "$PID" 2>/dev/null); do kill -KILL "$p" 2>/dev/null || true; done
    kill -KILL "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null
  else
    wait "$PID" 2>/dev/null
    echo "   exited with $?"
  fi

  SIZE=$(stat -c%s "$LOG" 2>/dev/null || echo 0)
  echo "   log size: $SIZE bytes"
  if grep -aq "required tests passed" "$LOG" 2>/dev/null; then
    grep -aE "tests are now running|required tests passed" "$LOG" | tail -3
    echo "RESULT: PASS on attempt $i"
    exit 0
  fi
  if grep -aq "tests failed" "$LOG" 2>/dev/null; then
    echo "RESULT: FAIL (real test failures) on attempt $i"
    grep -aE "tests failed|didn.t succeed|failed" "$LOG" | tail -15
    exit 1
  fi
  echo "   no result line; last progress:"
  grep -aE "Running test batch|\+{5,}" "$LOG" 2>/dev/null | tail -2 | cut -c1-120
done

echo "RESULT: FAIL - all $ATTEMPTS attempts hit the hang with no pass"
exit 1
