#!/usr/bin/env zsh
# Headless dev-server smoke test: boot runServer, ask it real questions over RCON, stop it.
#
# Gradle does not forward stdin to the server, so piped commands are lost. This
# script temporarily enables RCON in runs/server/server.properties, waits for
# "Done (", runs a few harmless queries through tools/rcon.py, then sends
# "stop". The properties file is backed up and restored on every exit path.
#
# Usage: ./tools/smoke-server.sh [startup-timeout-seconds]
# Env:   RCON_PORT, LOGDIR
set -o pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
LOGDIR=${LOGDIR:-${TMPDIR:-/mnt/Barracuda/tmp}/adaptionwheel-gametests}
STARTUP_TIMEOUT=${1:-180}
RCON_PORT=${RCON_PORT:-25575}
RCON_PASSWORD=${RCON_PASSWORD:-adaptionwheel-smoke}
LOG="$LOGDIR/server-smoke.log"
PROPS="$REPO/runs/server/server.properties"
BACKUP="$LOGDIR/server.properties.smoke-backup"

mkdir -p "$LOGDIR" "$REPO/runs/server"

kill_tree() {
  local pid=$1 child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill -TERM "$pid" 2>/dev/null || true
}

restore_props() {
  if [ -f "$BACKUP" ]; then
    mv -f "$BACKUP" "$PROPS"
  fi
}
trap restore_props EXIT INT TERM

# Back up whatever the user had, then write a minimal properties file with RCON on.
if [ -f "$PROPS" ]; then
  cp -f "$PROPS" "$BACKUP"
fi
python3 - "$PROPS" "$RCON_PORT" "$RCON_PASSWORD" <<'PY'
import sys
path, port, password = sys.argv[1], sys.argv[2], sys.argv[3]
lines = []
try:
    with open(path, encoding="utf-8", errors="replace") as handle:
        lines = [line.rstrip("\n") for line in handle]
except OSError:
    pass
wanted = {"enable-rcon": "true", "rcon.port": port, "rcon.password": password}
seen = set()
out = []
for line in lines:
    key = line.split("=", 1)[0].strip() if "=" in line else None
    if key in wanted:
        out.append(f"{key}={wanted[key]}")
        seen.add(key)
    else:
        out.append(line)
for key, value in wanted.items():
    if key not in seen:
        out.append(f"{key}={value}")
with open(path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(out) + "\n")
PY

cd "$REPO" || exit 1
rm -f "$LOG"
echo "== booting dev server (max ${STARTUP_TIMEOUT}s), log: $LOG"
./gradlew runServer --no-daemon > "$LOG" 2>&1 &
PID=$!
START=$SECONDS
READY=0

while kill -0 "$PID" 2>/dev/null; do
  if grep -aq 'Done (' "$LOG" 2>/dev/null; then READY=1; break; fi
  if (( SECONDS - START >= STARTUP_TIMEOUT )); then
    echo "FAIL: server did not finish loading in ${STARTUP_TIMEOUT}s"
    kill_tree "$PID"; sleep 2; kill -KILL "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null
    exit 1
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "FAIL: server process exited before 'Done ('"
  tail -30 "$LOG"
  wait "$PID" 2>/dev/null
  exit 1
fi
echo "== server up: $(grep -a 'Done (' "$LOG" | tail -1 | cut -c1-140)"

STATUS=0
python3 tools/rcon.py --port "$RCON_PORT" --password "$RCON_PASSWORD" list seed 'time query daytime' || STATUS=1
python3 tools/rcon.py --port "$RCON_PORT" --password "$RCON_PASSWORD" save-all || STATUS=1
python3 tools/rcon.py --port "$RCON_PORT" --password "$RCON_PASSWORD" --no-wait stop || STATUS=1

# Give the server a bounded chance to shut down cleanly.
STOP_START=$SECONDS
while kill -0 "$PID" 2>/dev/null && (( SECONDS - STOP_START < 60 )); do sleep 2; done
if kill -0 "$PID" 2>/dev/null; then
  echo "WARN: server still alive after stop; killing process tree"
  kill_tree "$PID"; sleep 2; kill -KILL "$PID" 2>/dev/null || true
  STATUS=1
fi
wait "$PID" 2>/dev/null

echo "== errors in log: $(grep -ac '\[ERROR\]\|\[Server thread/ERROR\]' "$LOG" 2>/dev/null)"
if [ "$STATUS" -eq 0 ]; then
  echo "RESULT: PASS"
else
  echo "RESULT: FAIL (see $LOG)"
fi
exit "$STATUS"
