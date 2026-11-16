#!/usr/bin/env zsh
# Verify the built jar's name and manifest agree with gradle.properties.
#
# The jar name is the only thing that tells a player which game a build is for:
# `version = "${mod_version}+mc${minecraft_version}"` in build.gradle and the
# same property expanded into neoforge.mods.toml. The manifest repeats it for
# tools that read a jar without unpacking it. A mismatch means two builds for
# incompatible Minecraft versions can look identical on disk.
#
# Usage: ./tools/check-jar.sh
set -o pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO" || exit 1

get() { grep -E "^$1=" gradle.properties | head -1 | cut -d= -f2-; }
MOD_VERSION=$(get mod_version)
MC_VERSION=$(get minecraft_version)
EXPECTED="adaptionwheel-${MOD_VERSION}+mc${MC_VERSION}.jar"

if [ -z "$MOD_VERSION" ] || [ -z "$MC_VERSION" ]; then
  echo "FAIL: cannot read mod_version/minecraft_version from gradle.properties"
  exit 1
fi

FAILED=0
JARS=(build/libs/*.jar)
if [ ! -f "${JARS[1]}" ]; then
  echo "FAIL: no jar in build/libs (run ./gradlew build first)"
  exit 1
fi

FOUND=0
for jar in "${JARS[@]}"; do
  [ -f "$jar" ] || continue
  FOUND=1
  name=$(basename "$jar")
  echo "Jar: $name"
  if [ "$name" = "$EXPECTED" ]; then
    echo "  name OK"
  else
    echo "  FAIL: expected $EXPECTED"
    FAILED=1
  fi
  MANIFEST=$(unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null | tr -d '\r')
  for pair in "Minecraft-Version:$MC_VERSION" "Implementation-Version:${MOD_VERSION}+mc${MC_VERSION}" "FMLLoader-Type:NEOFORGE"; do
    key=${pair%%:*}
    want=${pair#*:}
    got=$(echo "$MANIFEST" | grep -E "^$key:" | head -1 | cut -d: -f2- | sed 's/^ //')
    if [ "$got" = "$want" ]; then
      echo "  $key OK ($got)"
    else
      echo "  FAIL: $key is '$got', expected '$want'"
      FAILED=1
    fi
  done
done

[ "$FOUND" -eq 1 ] || exit 1
if [ "$FAILED" -eq 0 ]; then
  echo "RESULT: PASS"
else
  echo "RESULT: FAIL"
fi
exit "$FAILED"
