#!/usr/bin/env zsh
# Compile every mod GLSL shader offline, before it ever reaches a running game.
#
# glslangValidator insists on .vert/.frag suffixes, while Minecraft ships
# .vsh/.fsh, so each stage is copied into a temp dir under TMPDIR first. A shader
# that fails to link only logs once at load and leaves the CPU fallback drawing,
# which looks like "the effect silently doesn't exist"; this catches it in CI.
#
# Usage: ./tools/validate-shaders.sh
set -o pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if ! command -v glslangValidator >/dev/null 2>&1; then
  echo "glslangValidator not found. Install it (e.g. 'mesa-utils' / 'glslang-tools') and retry."
  exit 2
fi

TMP=$(mktemp -d "${TMPDIR:-/mnt/Barracuda/tmp}/aw-shaders.XXXXXX") || exit 2
trap 'rm -rf "$TMP"' EXIT

COUNT=0
FAILED=0
for src in "$REPO"/src/main/resources/assets/adaptionwheel/shaders/core/*/*.{vsh,fsh} \
           "$REPO"/src/main/resources/assets/adaptionwheel/shaders/core/*.{vsh,fsh}; do
  [ -f "$src" ] || continue
  base=$(basename "$src")
  case "$base" in
    *.vsh) ext=vert ;;
    *.fsh) ext=frag ;;
    *) continue ;;
  esac
  dst="$TMP/${base%.*}.$ext"
  cp "$src" "$dst"
  COUNT=$((COUNT + 1))
  if ! glslangValidator "$dst"; then
    echo "FAIL: $src"
    FAILED=$((FAILED + 1))
  else
    echo "OK:   ${src#$REPO/}"
  fi
done

if [ "$COUNT" -eq 0 ]; then
  echo "No shaders found under assets/adaptionwheel/shaders/core"
  exit 2
fi
echo "Checked $COUNT stages, $FAILED failed"
[ "$FAILED" -eq 0 ]
