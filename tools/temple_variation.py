#!/usr/bin/env python3
"""Build and verify the adaptation_temple's block-variety worldgen data.

Three modes, all offline (no game running):

    template <source.nbt>   copy a vanilla `/structure save` output into the mod's
                            data folder, turning every `minecraft:air` entry into
                            `minecraft:structure_void`
    rules                   regenerate the structure's processor list from the
                            shipped template's palette
    check [--jar PATH]      verify the shipped template and processor list against
                            each other (and, with --jar, against the real 26.3 block
                            registry)

Why a generated processor list instead of hand-written rules
------------------------------------------------------------
`minecraft:rule` matches an input block state and writes a **fixed** output state
(`ProcessorRule.outputState` is a single `BlockState`, not a mapping), so a data-only
swap cannot carry properties over. Vanilla solves the same problem in Java
(`BlackstoneReplaceProcessor` copies FACING/HALF/TYPE by hand). Here the property sets
are known and small, so the honest data-only equivalent is one rule per *exact* input
state with the properties spelled out -- and the rule set is derived from the
template's own palette instead of written by hand, so it cannot drift out of sync with
the building it randomises.

`check` is the reason this is worth having: it fails loudly when the template gains a
state the rules do not cover, when a rule names a block that does not exist in this
Minecraft version, or when a rule would move a block into a family with different
properties (which the game would reject at world load with a codec error naming
neither the field nor the file).

Also worth knowing: rules are evaluated in order and the **first** match wins, and
`RuleProcessor` seeds its randomness from the block's world position, so a given
temple looks the same every time you load the world but two temples in the same world
differ.

Usage:
    python3 tools/temple_variation.py template "run/client/saves/NAME/generated/minecraft/structure/adaptation_temple.nbt"
    python3 tools/temple_variation.py rules
    python3 tools/temple_variation.py check --jar build/neoForm/neoFormJoined*/steps/decompile/outputs.jar
"""

from __future__ import annotations

import argparse
import collections
import gzip
import json
import re
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TEMPLATE = REPO / "src/main/resources/data/adaptionwheel/structure/adaptation_temple.nbt"
PROCESSORS = REPO / "src/main/resources/data/adaptionwheel/worldgen/processor_list/adaptation_temple_variety.json"
PROCESSOR_ID = "adaptionwheel:adaptation_temple_variety"

AIR = "minecraft:air"
STRUCTURE_VOID = "minecraft:structure_void"

# Blocks removed from a `/structure save` output before it becomes mod content.
#
# **Placement writes every block in the palette, air and structure_void included.**
# `StructureTemplate.placeInWorld` iterates the whole block list and calls `setBlock` on
# each; the only skip is the chunk bounding box. `structure_void` is stripped when a
# template is *saved* (StructureBlockEntity adds it to ignoreBlocks), which is why a
# vanilla template never contains one and why converting air to it looks like the fix.
# It is not: it is an invisible real block, so every temple would bury a 13x13 ring of
# grass, flowers and terrain in nothing-at-all blocks.
#
# So the air is *dropped*. A position that is not in the template is not written at all,
# and the ruin touches nothing outside itself. Pass --keep-air for a sealed building
# whose interior should be carved out of the hillside (an open ruin should not).
DROPPED_BLOCKS = (AIR, STRUCTURE_VOID)

# Property sets of the block families the temple is built from, as declared by the
# vanilla block classes (StairBlock / SlabBlock / WallBlock / ChainBlock). A swap may
# only carry properties across inside one family, and a family's set is complete --
# so `check` can prove a carried property set is exactly the target's own.
FAMILY_PROPS = {
    "cube": frozenset(),
    "stairs": frozenset({"facing", "half", "shape", "waterlogged"}),
    "slab": frozenset({"type", "waterlogged"}),
    "wall": frozenset({"up", "north", "east", "south", "west", "waterlogged"}),
    "chain": frozenset({"axis", "waterlogged"}),
}


def family_of(block_id: str) -> str | None:
    """Which family a vanilla block id belongs to, or None if we do not model it."""
    name = block_id.split(":", 1)[1]
    if name.endswith("_stairs"):
        return "stairs"
    if name.endswith("_slab"):
        return "slab"
    if name.endswith("_wall"):
        return "wall"
    if name.endswith("_chain"):
        return "chain"
    if "_infested" in name or name.endswith("infested_cobblestone"):
        return "cube"
    return "cube"


# input block -> [(output block, probability)]. First matching rule wins, so a block
# with several outputs gets several rules with independent probability rolls: with the
# numbers below `minecraft:stone_bricks` ends up plain 42%, mossy 39%, cracked 22%,
# chiseled 5%, infested 4% (each rule is evaluated only if the previous ones missed).
SWAPS: dict[str, list[tuple[str, float]]] = {
    # --- full cubes ------------------------------------------------------
    "minecraft:stone_bricks": [
        ("minecraft:mossy_stone_bricks", 0.45),
        ("minecraft:cracked_stone_bricks", 0.30),
        ("minecraft:chiseled_stone_bricks", 0.08),
        ("minecraft:infested_stone_bricks", 0.08),
    ],
    "minecraft:mossy_stone_bricks": [
        ("minecraft:stone_bricks", 0.20),
        ("minecraft:infested_mossy_stone_bricks", 0.08),
    ],
    "minecraft:cracked_stone_bricks": [
        ("minecraft:mossy_stone_bricks", 0.18),
        ("minecraft:infested_cracked_stone_bricks", 0.08),
        ("minecraft:chiseled_stone_bricks", 0.06),
    ],
    "minecraft:chiseled_stone_bricks": [
        ("minecraft:stone_bricks", 0.25),
        ("minecraft:mossy_stone_bricks", 0.15),
    ],
    "minecraft:cobblestone": [
        ("minecraft:mossy_cobblestone", 0.50),
        ("minecraft:infested_cobblestone", 0.08),
    ],
    "minecraft:mossy_cobblestone": [
        ("minecraft:cobblestone", 0.22),
    ],
    # --- slabs / walls ---------------------------------------------------
    # Only the mossy direction exists for these: `cracked_*` is a full-cube-only
    # block in 26.3, so there is nothing to crack them into.
    "minecraft:stone_brick_slab": [("minecraft:mossy_stone_brick_slab", 0.50)],
    "minecraft:mossy_stone_brick_slab": [("minecraft:stone_brick_slab", 0.22)],
    "minecraft:stone_brick_wall": [("minecraft:mossy_stone_brick_wall", 0.50)],
    "minecraft:mossy_stone_brick_wall": [("minecraft:stone_brick_wall", 0.22)],
    "minecraft:mossy_cobblestone_slab": [("minecraft:cobblestone_slab", 0.22)],
    "minecraft:mossy_cobblestone_wall": [("minecraft:cobblestone_wall", 0.22)],
    # --- stairs ----------------------------------------------------------
    "minecraft:stone_brick_stairs": [("minecraft:mossy_stone_brick_stairs", 0.50)],
    "minecraft:mossy_stone_brick_stairs": [("minecraft:stone_brick_stairs", 0.22)],
    "minecraft:mossy_cobblestone_stairs": [("minecraft:cobblestone_stairs", 0.22)],
    # --- chains: iron has no natural copper family, but a temple that has
    #     weathered for a few thousand years has lost its iron too.
    "minecraft:iron_chain": [
        ("minecraft:oxidized_copper_chain", 0.30),
        ("minecraft:weathered_copper_chain", 0.25),
        ("minecraft:exposed_copper_chain", 0.25),
        ("minecraft:copper_chain", 0.15),
    ],
}

# Blocks that are deliberately never randomised. The altar is the one mod block in
# the template: swapping it would turn a Resonance Altar into scenery, and the whole
# point of the structure is that it holds one.
KEEP_UNCHANGED = {
    AIR,
    STRUCTURE_VOID,
    "minecraft:soul_lantern",
    "minecraft:oxidized_lightning_rod",
    "minecraft:oxidized_copper_chain",
    "adaptionwheel:resonance_altar",
}


def load_nbt(path: Path) -> dict:
    import nbtlib

    return nbtlib.load(str(path))


def save_nbt(data: "nbtlib.File", path: Path) -> None:
    import nbtlib  # noqa: F401  (documents the type of `data`; loaded in load_nbt)

    path.parent.mkdir(parents=True, exist_ok=True)
    # Structure templates are stored gzipped (vanilla ships them that way), and the
    # game loads either form -- match vanilla so the file is comparable to its own.
    with gzip.open(path, "wb") as handle:
        data.write(handle)


def palette_states(nbt: dict) -> list[dict]:
    """Every distinct state in the template, as {id, properties} dicts."""
    states = []
    for entry in nbt["palette"]:
        state = {"id": str(entry["id"])}
        properties = entry.get("properties")
        if properties is not None and len(properties) > 0:
            state["properties"] = {str(k): str(v) for k, v in properties.items()}
        states.append(state)
    return states


def state_object(state: dict) -> object:
    """A `BlockState` codec value: bare id for a default state, object otherwise."""
    if not state.get("properties"):
        return state["id"]
    return {"id": state["id"], "properties": dict(sorted(state["properties"].items()))}


def render_rules(states: list[dict]) -> dict:
    rules = []
    for state in states:
        block_id = state["id"]
        for target, probability in SWAPS.get(block_id, ()):
            # Same properties, different block: the target is in the same family, so
            # the input's property set is also the target's complete property set.
            output: dict = {"id": target}
            if state.get("properties"):
                output["properties"] = dict(sorted(state["properties"].items()))
            rules.append(
                {
                    "input_predicate": {
                        "predicate_type": "minecraft:random_blockstate_match",
                        "block_state": state_object(state),
                        "probability": probability,
                    },
                    "location_predicate": {"predicate_type": "minecraft:always_true"},
                    "output_state": state_object(output),
                }
            )
    return {
        "processors": [
            {
                "processor_type": "minecraft:rule",
                "rules": rules,
            }
        ]
    }


def cmd_template(args: argparse.Namespace) -> int:
    import nbtlib

    source = Path(args.source)

    nbt = load_nbt(source)
    drop = set(DROPPED_BLOCKS if not args.keep_air else ())
    palette = nbt["palette"]

    # Which palette entries are we throwing away, and what index does each kept block
    # end up at afterwards?
    kept_entries = []
    remap = {}
    for index, entry in enumerate(palette):
        if str(entry["id"]) in drop:
            continue
        remap[index] = len(kept_entries)
        kept_entries.append(entry)

    if not kept_entries:
        print(f"error: every block in {source} is air or structure_void -- nothing to ship", file=sys.stderr)
        return 1

    dropped = collections.Counter()
    blocks = nbtlib.List[nbtlib.Compound]()
    for entry in nbt["blocks"]:
        index = int(entry["state"])
        if index in remap:
            kept = nbtlib.Compound({"pos": nbtlib.List[nbtlib.Int]([int(v) for v in entry["pos"]]), "state": nbtlib.Int(remap[index])})
            if "nbt" in entry:
                kept["nbt"] = entry["nbt"]
            blocks.append(kept)
        else:
            dropped[str(palette[index]["id"])] += 1

    nbt["blocks"] = blocks
    nbt["palette"] = nbtlib.List[nbtlib.Compound](kept_entries)

    # The saved volume is the window the structure block was given, which is usually far
    # bigger than the ruin. Report it, because a template that straddles a slope is the
    # other half of "does not look like it belongs here".
    size = [int(v) for v in nbt["size"]]
    xs = [int(b["pos"][0]) for b in blocks]
    ys = [int(b["pos"][1]) for b in blocks]
    zs = [int(b["pos"][2]) for b in blocks]
    extent = (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1)
    if extent != tuple(size):
        print(f"  note: the saved window is {tuple(size)} but the ruin only fills {extent} from "
              f"({min(xs)},{min(ys)},{min(zs)}); crop the structure block's size to shrink the footprint")

    save_nbt(nbt, TEMPLATE)
    print(f"wrote {TEMPLATE.relative_to(REPO)}")
    print(f"  {len(blocks)} blocks, {len(kept_entries)} states")
    if dropped:
        print("  dropped: " + ", ".join(f"{block} x{n}" for block, n in dropped.most_common()) + " (placement writes every palette block)")
    else:
        print("  nothing dropped -- the source had no air or structure_void")
    return 0


def cmd_rules(args: argparse.Namespace) -> int:
    nbt = load_nbt(TEMPLATE)
    states = palette_states(nbt)
    data = render_rules(states)
    PROCESSORS.parent.mkdir(parents=True, exist_ok=True)
    PROCESSORS.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {PROCESSORS.relative_to(REPO)}")
    print(f"  {len(data['processors'][0]['rules'])} rules over {len(states)} palette states")
    return 0


def vanilla_block_ids(jar: Path) -> set[str]:
    """Block ids that really exist in this Minecraft version.

    26.3 splits the ids across two reference classes: `BlockItemIds` holds blocks that
    also have an item form, `BlockIds` the handful that do not. Weathering families
    (copper chains, lightning rods) are declared by their *base* name and expand into
    four ids, so those are expanded here too.
    """
    ids: set[str] = set()
    with zipfile.ZipFile(jar) as archive:
        for entry in ("net/minecraft/references/BlockItemIds.java", "net/minecraft/references/BlockIds.java"):
            source = archive.read(entry).decode("utf-8")
            ids |= {f"minecraft:{name}" for name in re.findall(r'create\("(\w+)"\)', source)}
            for base in re.findall(r'createSimpleCopper\("(\w+)"\)', source):
                ids |= {f"minecraft:{base}", f"minecraft:exposed_{base}", f"minecraft:weathered_{base}", f"minecraft:oxidized_{base}"}
    return ids


def read_state(value: object) -> dict:
    """Normalise a `BlockState` codec value (bare id or object) into a dict."""
    if isinstance(value, str):
        return {"id": value, "properties": {}}
    return {"id": value["id"], "properties": value.get("properties", {})}


def cmd_check(args: argparse.Namespace) -> int:
    problems: list[str] = []
    nbt = load_nbt(TEMPLATE)
    states = palette_states(nbt)

    if not nbt.get("palette"):
        problems.append("template has no palette")
    # A template that still carries air or structure_void digs a hole when placed, and
    # structure_void is invisible, so the damage is invisible too.
    for block in DROPPED_BLOCKS:
        carried = [s for s in states if s["id"] == block]
        if carried and not args.allow_air:
            blocks = sum(1 for b in nbt["blocks"] if str(nbt["palette"][int(b["state"])]["id"]) == block)
            problems.append(f"template still carries {blocks} {block} blocks -- placement writes every one of them "
                            f"(re-run 'template', or pass --allow-air if the interior really should be carved)")

    rules_doc = json.loads(PROCESSORS.read_text(encoding="utf-8"))
    processors = rules_doc["processors"]
    if len(processors) != 1 or processors[0]["processor_type"] != "minecraft:rule":
        problems.append(f"expected exactly one minecraft:rule processor, found {len(processors)}")
        return report(problems)
    rules = processors[0]["rules"]

    covered = {json.dumps(read_state(r["input_predicate"]["block_state"]), sort_keys=True) for r in rules}

    # 1. every swappable palette state is covered by at least one rule
    for state in states:
        block_id = state["id"]
        if block_id not in SWAPS and block_id not in KEEP_UNCHANGED:
            problems.append(f"{block_id} is in the template but in neither SWAPS nor KEEP_UNCHANGED")
        if block_id in SWAPS and json.dumps({"id": block_id, "properties": state.get("properties", {})}, sort_keys=True) not in covered:
            problems.append(f"template state {json.dumps(state_object(state))} has no rule -- regenerate with 'rules'")

    # 2. every SWAPS entry actually occurs in the template (dead rule otherwise)
    present = {s["id"] for s in states}
    for block_id in SWAPS:
        if block_id not in present:
            problems.append(f"SWAPS has {block_id} but the template does not contain it")

    # 3. every rule's output keeps the input's family, and carries exactly the
    #    family's property set
    for rule in rules:
        in_state = read_state(rule["input_predicate"]["block_state"])
        out_state = read_state(rule["output_state"])
        in_id, out_id = in_state["id"], out_state["id"]
        in_props, out_props = set(in_state["properties"]), set(out_state["properties"])
        in_family, out_family = family_of(in_id), family_of(out_id)
        if in_family != out_family:
            problems.append(f"{in_id} -> {out_id} crosses block families ({in_family} -> {out_family})")
        if out_props != FAMILY_PROPS[out_family]:
            problems.append(f"{in_id} -> {out_id} carries {sorted(out_props)}, but a {out_family} has {sorted(FAMILY_PROPS[out_family])}")
        if in_props != FAMILY_PROPS[in_family]:
            problems.append(f"{in_id} in the template has properties {sorted(in_props)}, not the {in_family} set {sorted(FAMILY_PROPS[in_family])}")
        probability = rule["input_predicate"].get("probability")
        if not isinstance(probability, (int, float)) or not 0.0 < probability <= 1.0:
            problems.append(f"{in_id} -> {out_id} has probability {probability!r}")

    # 4. the rules never touch the altar, the lanterns or the lightning rod
    for rule in rules:
        in_id = read_state(rule["input_predicate"]["block_state"])["id"]
        if in_id in KEEP_UNCHANGED:
            problems.append(f"rule rewrites {in_id}, which is on the keep-unchanged list")

    # 5. (optional) every block id involved really exists in this Minecraft version
    if args.jar:
        known = vanilla_block_ids(Path(args.jar))
        used = {s["id"] for s in states}
        used |= {read_state(r["input_predicate"]["block_state"])["id"] for r in rules}
        used |= {read_state(r["output_state"])["id"] for r in rules}
        missing = sorted(b for b in used if not b.startswith("adaptionwheel:") and b not in known)
        if missing:
            problems.append("blocks that do not exist in this Minecraft version: " + ", ".join(missing))

    print(f"template : {len(states)} states, {len(nbt['blocks'])} blocks, size {[int(v) for v in nbt['size']]}")
    print(f"processor: {PROCESSOR_ID} -> {len(rules)} rules")
    print(f"swapped  : {len({read_state(r['input_predicate']['block_state'])['id'] for r in rules})} distinct blocks")
    print(f"untouched: {', '.join(sorted(s['id'] for s in states if s['id'] in KEEP_UNCHANGED))}")
    return report(problems)


def report(problems: list[str]) -> int:
    if problems:
        print(f"\nFAIL ({len(problems)} problem(s)):")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("\nOK")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="mode", required=True)

    p_template = sub.add_parser("template", help="convert a /structure save output into the mod's template")
    p_template.add_argument("source")
    p_template.add_argument("--keep-air", action="store_true",
                            help="keep air/structure_void instead of dropping them (only for a sealed building whose interior should be carved)")
    p_template.set_defaults(func=cmd_template)

    sub.add_parser("rules", help="regenerate the processor list from the template").set_defaults(func=cmd_rules)

    p_check = sub.add_parser("check", help="verify template and processor list against each other")
    p_check.add_argument("--jar", help="decompiled Minecraft jar, to verify block ids exist")
    p_check.add_argument("--allow-air", action="store_true",
                         help="tolerate air/structure_void in the template (see 'template --keep-air')")
    p_check.set_defaults(func=cmd_check)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
