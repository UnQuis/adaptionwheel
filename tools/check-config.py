#!/usr/bin/env python3
"""Guard the two silent config failures this mod has already shipped.

1. UNBALANCED PUSH/POP. ModConfigSpec.Builder.pop() on an empty stack is a silent
   no-op, and an extra push keeps the current section open. Every key defined
   afterwards is written one level too deep and read back from that same wrong
   path, so the build, boot and tests all pass while existing config files get
   their sections reset. Count push/pop PER BUILDER VARIABLE: this file has both
   `s` (server) and `c` (client), and one counter across both reads as a false
   positive.

2. SECTION DRIFT. Compare the sections the code pushes with the sections in a
   generated adaptionwheel-server.toml. A missing pop shows up here as a
   `[mutations.contactDefense]`-style section that should have been top level.

Usage: python3 tools/check-config.py [path/to/adaptionwheel-server.toml]
Exit 0 = clean, 1 = drift found.
"""
import glob
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(REPO, "src", "main", "java", "ru", "adaptionwheel", "config", "AdaptionConfig.java")
CANDIDATES = [
    os.path.join(REPO, "runs", "server", "config", "adaptionwheel-server.toml"),
    os.path.join(REPO, "runs", "gameTestServer", "config", "adaptionwheel-server.toml"),
]


def main(argv):
    with open(SOURCE, encoding="utf-8") as handle:
        lines = handle.read().splitlines()

    builders = []
    for line in lines:
        for match in re.finditer(r"ModConfigSpec\.Builder\s+(\w+)\s*=", line):
            builders.append(match.group(1))
    if not builders:
        print("FAIL: no ModConfigSpec.Builder variables found in " + SOURCE)
        return 1

    stacks = {name: [] for name in builders}
    sections = {name: [] for name in builders}
    counts = {name: [0, 0] for name in builders}  # push, pop

    for lineno, line in enumerate(lines, 1):
        used = [name for name in builders if re.search(r"\b" + re.escape(name) + r"\s*\.", line)]
        if not used:
            continue
        for match in re.finditer(r'\.push\(\s*"([^"]+)"\s*\)|\.pop\(\s*\)', line):
            for name in used:
                if match.group(1) is not None:
                    stacks[name].append(match.group(1))
                    sections[name].append((lineno, ".".join(stacks[name])))
                    counts[name][0] += 1
                else:
                    counts[name][1] += 1
                    if stacks[name]:
                        stacks[name].pop()

    bad = False
    print("Builders: " + ", ".join(builders))
    for name in builders:
        pushes, pops = counts[name]
        depth = len(stacks[name])
        state = "OK" if pushes == pops and depth == 0 else "FAIL"
        if state == "FAIL":
            bad = True
        print(f"  {name}: push={pushes} pop={pops} final_depth={depth}  {state}")

    toml = argv[0] if argv else next((p for p in CANDIDATES if os.path.isfile(p)), None)
    if not toml or not os.path.isfile(toml):
        print("\nNo generated server TOML found - skipped section comparison.")
        print("Run the server once (or ./tools/run-gametests.sh) to generate it.")
        return 1 if bad else 0

    expected = {path for _line, path in sections["s"]}
    actual = set()
    with open(toml, encoding="utf-8") as handle:
        for line in handle:
            match = re.match(r"^\s*\[([^\]]+)\]", line)
            if match:
                actual.add(match.group(1).strip())

    print(f"\nGenerated TOML: {toml}")
    print(f"  expected sections: {len(expected)}   found: {len(actual)}")
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing:
        bad = True
        print("  MISSING: " + ", ".join(missing))
    if extra:
        bad = True
        print("  UNEXPECTED: " + ", ".join(extra))
    if not missing and not extra:
        print("  sections match")

    backups = glob.glob(os.path.join(os.path.dirname(toml), "adaptionwheel-*.toml.bak"))
    if backups:
        bad = True
        print("  CONFIG TRACKER LOOP: " + str(len(backups)) + " .toml.bak files - delete stale config")

    print("\nRESULT: " + ("FAIL" if bad else "PASS"))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
