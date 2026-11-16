#!/usr/bin/env python3
"""Audit the mod's language files for ambiguous labels and missing translations.

Two things this catches that no build check does:

  * Duplicate concept display names. The panel used to show two rows both called
    "Starvation" (one immunity, one resistance), so switching off "Starvation"
    did nothing - the player had switched off the wrong mechanism. Only the
    `adaptionwheel.concept.*` keys are scanned: an item sharing a name with its
    advancement title, or "ON"/"OFF" appearing in two UI contexts, is normal.
  * Keys present in one language but not the other.

Usage: python3 tools/check-lang.py [--strict]
  --strict also fails on missing translation keys.
"""
import collections
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(REPO, "src", "main", "resources", "assets", "adaptionwheel", "lang")


def load(name):
    path = os.path.join(LANG, name + ".json")
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main(argv):
    strict = "--strict" in argv
    en = load("en_us")
    ru = load("ru_ru")

    by_value = collections.defaultdict(list)
    for key, value in en.items():
        if key.startswith("adaptionwheel.concept.") and isinstance(value, str):
            by_value[value].append(key)
    duplicates = {value: keys for value, keys in by_value.items() if len(keys) > 1}

    print(f"en_us: {len(en)} keys   ru_ru: {len(ru)} keys")
    if duplicates:
        print("\nDUPLICATE CONCEPT DISPLAY NAMES in en_us:")
        for value, keys in sorted(duplicates.items()):
            print(f'  "{value}"')
            for key in keys:
                print(f"    {key}")
    else:
        print("\nNo duplicate concept display names in en_us")

    only_en = sorted(set(en) - set(ru))
    only_ru = sorted(set(ru) - set(en))
    if only_en:
        print(f"\nMissing in ru_ru ({len(only_en)}):")
        for key in only_en[:40]:
            print("  " + key)
        if len(only_en) > 40:
            print(f"  ... and {len(only_en) - 40} more")
    if only_ru:
        print(f"\nMissing in en_us ({len(only_ru)}):")
        for key in only_ru[:40]:
            print("  " + key)

    failed = bool(duplicates) or (strict and (only_en or only_ru))
    print("\nRESULT: " + ("FAIL" if failed else "PASS"))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
