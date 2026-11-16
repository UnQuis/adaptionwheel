# Tools

Offline checks and headless test drivers for the mod. Run from anywhere; each
script resolves the repo root from its own location. Python tools need only the
standard library unless stated otherwise.

## Testing the game

| Tool | What it does |
| --- | --- |
| `./tools/run-gametests.sh [attempts] [seconds]` | Runs `runGameTestServer` with a wall-clock cap, a 40 MiB log cap, full process-tree cleanup and bounded retries. Use this instead of a bare `./gradlew runGameTestServer` — the test grid's random spawn position can leave a batch where chunks never tick, and the server then spins forever, writing ~130 KB/s into the log. A healthy run is ~192 tests in ~20 s. |
| `./tools/smoke-server.sh [startup-seconds]` | Boots a headless dev server, waits for `Done (`, queries it over RCON (`list`, `seed`, `time get`, `save-all`), sends `stop`, and restores `runs/server/server.properties` on every exit path. Gradle does not forward stdin, so piped commands never reach the server. |
| `python3 tools/rcon.py <command> [command...]` | Minimal Source-RCON client used by the smoke test. Reads host/port/password from `runs/server/server.properties` (or `RCON_PASSWORD`). Handy for driving a server already started with `./gradlew runServer`. |

Logs and scratch output go under `${TMPDIR}/adaptionwheel-gametests` — on this
machine `TMPDIR=/mnt/Barracuda/tmp`. Do not redirect verbose logs to `/tmp`.

## Static checks

| Tool | What it does |
| --- | --- |
| `python3 tools/check-config.py [server.toml]` | Counts `push`/`pop` **per builder variable** in `AdaptionConfig.java` (an unbalanced pair silently rewrites config paths) and compares the pushed sections with a generated `adaptionwheel-server.toml`. Also flags the `.toml.bak` pile-up caused by a stale config looping the tracker. |
| `./tools/validate-shaders.sh` | Compiles every `.vsh`/`.fsh` with `glslangValidator` via temporary `.vert`/`.frag` copies. Needs `glslang-tools` installed. A shader that fails to link only logs once at game load and silently falls back. |
| `./tools/check-jar.sh` | Checks the built jar's name and manifest against `mod_version`/`minecraft_version` in `gradle.properties`. Run after `./gradlew build`. |
| `python3 tools/check-lang.py [--strict]` | Finds duplicate display names in `en_us.json` (two rows with the same label means one of them cannot be switched off from the panel) and missing keys between `en_us` and `ru_ru`. |

## Branch-port audits

The mod is maintained on two incompatible branches, `1.21.1` and `26.3`. A
method can be present on both branches and still never be called, or have the
pre-fix body; name diffs alone miss both. These three tools compare different
layers and their output still has to be judged (API renames are expected):

| Tool | What it compares |
| --- | --- |
| `python3 tools/branch_parity.py` | Method names declared per shared Java file. |
| `python3 tools/branch_audit.py` | Call sites (a method with N references here and 0 there), config keys/defaults, lang values, and full block-tag JSON. |
| `python3 tools/branch_bodies.py` | Statement text of methods, with comments and formatting stripped, to catch a copied method whose body is still the old version. |

All three read the *working tree* of the branch you are standing on, so an
uncommitted fix is seen instead of being reported as still missing. Override the
repo with `AW_REPO=/path/to/repo`.

## Worldgen

`python3 tools/temple_variation.py check [--jar PATH]` verifies the Adaptation
Temple's NBT template against its processor list (and optionally against a real
block registry). `template` and `rules` modes regenerate those files. Needs
`nbtlib`: `python3 -m pip install --user nbtlib`. Without it the tool prints an
install hint instead of a traceback.
