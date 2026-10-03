#!/usr/bin/env python3
"""Convert a Blockbench "Modded Block"/"Java Block" export into a vanilla model file.

Blockbench's export is *almost* vanilla JSON, and the gap is exactly where models break:

  * `rotation` is emitted even when there is no rotation, as `{"angle": null, "axis": null,
    "origin": [...]}`. Vanilla does not read it as "no rotation": `CuboidModelElement`'s
    deserializer sees that `axis` is present, calls `getAsString` on a JSON null and throws.
    A zero angle is dropped entirely here.
  * `groups` (a flattened index list, not a hierarchy) is not a vanilla field at all. It is
    dropped; `elements` is already flat and absolute.
  * texture references are `#2`, `#3`, ... — Blockbench's internal ids. They are renamed to
    vanilla texture variables and the referenced PNGs are copied into the mod's textures.

Everything vanilla *does* validate is checked here too, so a broken model fails on the command
line instead of in the resource log: `from`/`to` must be within [-16, 32], an element needs at
least one face, every face needs a texture the model declares. (26.3 does not validate face UVs,
but out-of-texture UVs are reported as warnings because they show up as missing pixels.)

Usage:
    python3 tools/bbmodel_to_vanilla.py "Resonance altar/resonance_altar.json" \\
        --out models/block/resonance_altar.json \\
        --texture 2=resonance_altar --texture 3=resonance_altar_core
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BLOCK_TEXTURES = REPO / "src/main/resources/assets/adaptionwheel/textures/block"
MIN_EXTENT, MAX_EXTENT = -16.0, 32.0


def load_export(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if "elements" not in data:
        raise SystemExit(f"{path} has no 'elements' -- this is not a Java block/item model export")
    return data


# --- the repair -------------------------------------------------------------
#
# The export lost the rotations of four of the six shard groups, and that is exactly the
# "the lattice is everywhere but on no sides" symptom. The evidence is not a guess:
#
#   * the model is a 14x14x14 core cube (1..15) plus six *one-unit-thick* plates of shards;
#   * two of them are already where they belong -- one at z 0..1 (front), one at 15..16 (back);
#   * the other four all lie in the *same* z 7..8 slice, overlapping each other, i.e. they are
#     unrotated copies of the front plate;
#   * each of those four shares one single rotation origin across all 56 of its elements, and
#     each origin is the midpoint of an edge of the core cube: (1,8,8), (16,8,8), (8,15,8),
#     (8,0,8) -- the pivots the author rotated about.
#
# A pivot plus a bounding box pins the rotation down uniquely, and the result is verifiable:
# after the rotation below each plate sits exactly on one face of the core and spans 0..16 in
# the two remaining axes. So this restores what the source had rather than inventing a look.
# `--no-repair` turns it off.
MISSING_ROTATIONS = {
    (1.0, 8.0, 8.0): ("y", -90),
    (16.0, 8.0, 8.0): ("y", 90),
    (8.0, 15.0, 8.0): ("x", -90),
    (8.0, 0.0, 8.0): ("x", 90),
}


def rotate_box(lo: list[float], hi: list[float], pivot: tuple[float, float, float], axis: str, angle: int) -> tuple[list[float], list[float]]:
    """Bake a right-handed 90-degree rotation about a pivot into an axis-aligned box."""
    px, py, pz = pivot
    radians = math.radians(angle)
    cos_a, sin_a = math.cos(radians), math.sin(radians)

    def rotate(point):
        x, y, z = point
        if axis == "y":
            x, z = px + (x - px) * cos_a + (z - pz) * sin_a, pz - (x - px) * sin_a + (z - pz) * cos_a
        else:
            y, z = py + (y - py) * cos_a - (z - pz) * sin_a, pz + (y - py) * sin_a + (z - pz) * cos_a
        return (x, y, z)

    corners = [rotate((x, y, z)) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    return [min(c[i] for c in corners) for i in range(3)], [max(c[i] for c in corners) for i in range(3)]


def group_origins(data: dict) -> dict[tuple[int, ...], tuple[float, float, float] | None]:
    """Map each element index to the single rotation origin its whole group shares.

    A group is one pattern copied around the cube: Blockbench gives all of its elements the
    same rotation origin and then, for the export, a null angle. Where the angle is missing
    but the origin is shared, that origin is the pivot the pattern was rotated about.
    """
    origins: dict[tuple[int, ...], tuple[float, float, float] | None] = {}
    for group in data.get("groups", []):
        if not isinstance(group, dict):
            continue
        children = group.get("children", [])
        if not children:
            continue
        shared = {tuple(data["elements"][i].get("rotation", {}).get("origin") or ()) for i in children
                  if isinstance(data["elements"][i].get("rotation"), dict)}
        origins[tuple(children)] = next(iter(shared)) if len(shared) == 1 and len(next(iter(shared))) == 3 else None
    return origins


def element_rotation(element: dict, problems: list[str]) -> dict | None:
    """Vanilla-shaped rotation, or None when the element is not rotated."""
    rotation = element.get("rotation")
    if rotation is None:
        return None
    if not isinstance(rotation, dict):
        if rotation == 0:
            return None
        problems.append(f"element {element['from']} has a numeric rotation {rotation!r}")
        return None
    angle = rotation.get("angle")
    axis = rotation.get("axis")
    if angle is None and axis is None:
        return None  # Blockbench's "no rotation" marker
    if not angle:
        return None  # angle 0 with an axis is still no rotation
    if axis is None:
        problems.append(f"element {element['from']} has angle {angle} but no axis")
        return None
    return {"origin": rotation.get("origin", [0, 0, 0]), "axis": axis, "angle": angle}


def convert(data: dict, names: dict[str, str], clip: tuple[float, ...] | None, origin: tuple[int, int, int],
            flip_y: bool = False, repair: bool = True) -> tuple[dict, list[str], list[str]]:
    problems: list[str] = []
    warnings: list[str] = []
    textures = data.get("textures", {})

    normalised = {f"#{k}" if not k.startswith("#") else k: v for k, v in textures.items()}
    missing = [key for key in names if key not in normalised]
    if missing:
        raise SystemExit(f"the export declares {sorted(textures)} but not {missing}")
    textures = normalised
    # The chosen name is both the vanilla texture variable and the file the PNG is
    # installed as; `textures[key]` is the Blockbench-side filename we copy from.
    out_textures = {name: f"adaptionwheel:block/{name}" for name in names.values()}
    sources = {name: textures[key] for key, name in names.items()}

    # index -> the rotation that has to be baked back in
    repair_rotation: dict[int, tuple[tuple[float, float, float], str, int]] = {}
    if repair:
        for children, pivot in group_origins(data).items():
            if pivot is None:
                continue
            key = tuple(float(v) for v in pivot)
            if key in MISSING_ROTATIONS:
                axis, angle = MISSING_ROTATIONS[key]
                for index in children:
                    repair_rotation[index] = (key, axis, angle)

    elements = []
    dropped_by_clip = 0
    for index, element in enumerate(data["elements"]):
        box = [float(v) for v in (*element["from"], *element["to"])]
        if index in repair_rotation:
            pivot, axis, angle = repair_rotation[index]
            lo, hi = rotate_box(box[:3], box[3:], pivot, axis, angle)
            box = lo + hi
        if flip_y:  # negate and swap so from < to stays true
            box[1], box[4] = -box[4], -box[1]
        for i in range(3):
            box[i] += origin[i]
            box[i + 3] += origin[i]
        if any(not MIN_EXTENT <= v <= MAX_EXTENT for v in box):
            problems.append(f"element {element['from']} -> {element['to']} leaves the [-16, 32] range vanilla accepts")
        if clip and not all(clip[i] <= box[i] <= box[i + 3] for i in range(3)):
            dropped_by_clip += 1
            continue
        faces = element.get("faces") or {}
        if not faces:
            problems.append(f"element {element['from']} has no faces")
            continue
        out_faces = {}
        for facing, face in faces.items():
            ref = face.get("texture")
            name = names.get(ref)
            if name is None:
                problems.append(f"element {element['from']} face {facing} uses undeclared texture {ref!r}")
                continue
            out_face = {"texture": f"#{name}"}
            if face.get("uv") is not None:
                uv = [float(v) for v in face["uv"]]
                out_face["uv"] = uv
            if face.get("rotation"):
                out_face["rotation"] = int(face["rotation"])
            out_faces[facing] = out_face
        if not out_faces:
            continue
        new_element = {
            "from": [round(v, 4) for v in box[:3]],
            "to": [round(v, 4) for v in box[3:]],
        }
        rotation = element_rotation(element, problems)
        if rotation:
            rotation["origin"] = [(-float(rotation["origin"][1]) if (flip_y and i == 1) else float(rotation["origin"][i])) + origin[i]
                                  for i in range(3)]
            new_element["rotation"] = rotation
        new_element["faces"] = out_faces
        elements.append(new_element)

    if repair_rotation:
        pivots = sorted({f"{pivot} {axis}{angle:+d}" for pivot, axis, angle in repair_rotation.values()})
        warnings.append(f"restored {len(repair_rotation)} dropped rotations ({len(pivots)} plates): " + "; ".join(pivots))
    if dropped_by_clip:
        warnings.append(f"{dropped_by_clip} elements removed by --clip")
    if not elements:
        problems.append("nothing left to write")

    bounds = [min(e["from"][i] for e in elements) for i in range(3)] + [max(e["to"][i] for e in elements) for i in range(3)]
    if any(bounds[i] < 0.0 or bounds[i + 3] > 16.0 for i in range(3)):
        warnings.append(f"model spans {bounds}, i.e. outside the block cube 0..16 -- it will render over "
                        f"neighbouring blocks and in the inventory as-is")

    # vanilla's block models want a `particle` texture; MaterialBaker warns without one
    out_textures["particle"] = f"adaptionwheel:block/{next(iter(names.values()))}"
    model = {"parent": "minecraft:block/block", "textures": out_textures, "elements": elements}
    return model, sources, warnings



# Each face is (normal, corners); a corner is ("lo"|"hi", "lo"|"hi", "lo"|"hi")
# picked per axis out of the element's from/to. Listed winding-agnostic -- the preview
# sorts by depth, and the game rebuilds its own winding from the facing.
FACES = {
    "north": ((0, 0, -1), (("lo", "lo", "lo"), ("hi", "lo", "lo"), ("hi", "hi", "lo"), ("lo", "hi", "lo"))),
    "south": ((0, 0, 1), (("hi", "lo", "hi"), ("lo", "lo", "hi"), ("lo", "hi", "hi"), ("hi", "hi", "hi"))),
    "west": ((-1, 0, 0), (("lo", "lo", "hi"), ("lo", "lo", "lo"), ("lo", "hi", "lo"), ("lo", "hi", "hi"))),
    "east": ((1, 0, 0), (("hi", "lo", "lo"), ("hi", "lo", "hi"), ("hi", "hi", "hi"), ("hi", "hi", "lo"))),
    "up": ((0, 1, 0), (("lo", "hi", "hi"), ("hi", "hi", "hi"), ("hi", "hi", "lo"), ("lo", "hi", "lo"))),
    "down": ((0, -1, 0), (("lo", "lo", "lo"), ("hi", "lo", "lo"), ("hi", "lo", "hi"), ("lo", "lo", "hi"))),
}


def render_preview(model: dict, texture_paths: dict[str, Path], out: Path, size: int = 640, clip: tuple[float, ...] | None = None) -> None:
    """A crude isometric preview, so a converted model can be eyeballed without the game.

    Painter's algorithm over box faces, each face filled with the average colour of its
    UV rectangle and outlined. It is not the game's shading -- it answers the questions a
    conversion can actually break: is it mirrored, is it upside down, what is the
    silhouette, which parts stick out of the block.
    """
    from PIL import Image, ImageDraw

    images = {name: Image.open(path).convert("RGBA") for name, path in texture_paths.items()}
    quads = []
    for element in model["elements"]:
        lo, hi = element["from"], element["to"]
        if clip and not all(clip[i] <= lo[i] and clip[i + 3] >= hi[i] for i in range(3)):
            continue
        for facing, face in element["faces"].items():
            normal, corners = FACES[facing]
            name = face["texture"].lstrip("#")
            uv = face.get("uv") or [0, 0, 16, 16]
            region = images[name].crop((int(min(uv[0], uv[2])), int(min(uv[1], uv[3])),
                                        max(1, int(max(uv[0], uv[2]))), max(1, int(max(uv[1], uv[3])))))
            pixels = list(region.get_flattened_data() if hasattr(region, "get_flattened_data") else region.getdata())
            opaque = [q for q in pixels if q[3] > 0]
            colour = tuple(sum(q[i] for q in opaque) // len(opaque) for i in range(3)) if opaque else (0, 0, 0)
            shade = {"up": 1.0, "down": 0.45, "north": 0.72, "south": 0.86, "west": 0.6, "east": 0.94}[facing]
            colour = tuple(int(c * shade) for c in colour)
            points = [tuple(hi[i] if corner[i] == "hi" else lo[i] for i in range(3)) for corner in corners]
            centre = [sum(p[i] for p in points) / 4 for i in range(3)]
            quads.append((sum(centre[i] * normal[i] for i in range(3)), points, colour))

    xs = [p[0] for _, points, _ in quads for p in points]
    ys = [p[1] for _, points, _ in quads for p in points]
    zs = [p[2] for _, points, _ in quads for p in points]
    centre = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2)
    scale = size * 0.8 / (max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)) or 1)
    angle = math.radians(30)
    image = Image.new("RGBA", (size, size), (24, 26, 32, 255))
    draw = ImageDraw.Draw(image)

    def project(point):
        x, y, z = (point[i] - centre[i] for i in range(3))
        # true isometric: y is up on screen, the two horizontal axes are 30 degrees apart
        screen_x = (x - z) * math.cos(angle)
        screen_y = (x + z) * math.sin(angle) * 0.5 - y
        return (size / 2 + screen_x * scale, size / 2 + screen_y * scale)

    for _depth, points, colour in sorted(quads, key=lambda q: -q[0]):
        projected = [project(p) for p in points]
        draw.polygon(projected, fill=colour + (255,), outline=(8, 9, 12, 255))
    image.save(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", help="Blockbench Java block/item model export")
    parser.add_argument("--out", required=True, help="model file to write, relative to the repo root")
    parser.add_argument("--texture", action="append", default=[], metavar="ID=NAME",
                        help="map a Blockbench texture id (#2) to a texture name; repeatable")
    parser.add_argument("--copy-textures", action="store_true",
                        help="copy the referenced PNGs into assets/.../textures/block/<NAME>.png")
    parser.add_argument("--from-texture-dir", default=None, help="where Blockbench's PNGs live (default: next to the export)")
    parser.add_argument("--clip", default=None, metavar="x0,y0,z0,x1,y1,z1",
                        help="drop elements not fully inside this box")
    parser.add_argument("--origin", default="0,0,0", metavar="x,y,z", help="shift the model before writing")
    parser.add_argument("--preview", default=None, help="also render a crude isometric preview to this PNG")
    parser.add_argument("--no-repair", action="store_true",
                        help="keep the export's unrotated plates as they are (see MISSING_ROTATIONS)")
    parser.add_argument("--flip-y", action="store_true",
                        help="mirror vertically (Blockbench's Y grows downwards, vanilla's upwards)")
    args = parser.parse_args()

    source = Path(args.source)
    data = load_export(source)
    names = {}
    for entry in args.texture:
        key, _, name = entry.partition("=")
        names[f"#{key}" if not key.startswith("#") else key] = name
    if not names:
        # Fall back to layer0.. by usage, so the file is still usable.
        used = {}
        for element in data["elements"]:
            for face in (element.get("faces") or {}).values():
                ref = face.get("texture")
                if ref:
                    used[ref] = used.get(ref, 0) + 1
        for index, ref in enumerate(sorted(used, key=lambda r: -used[r])):
            names[ref] = f"layer{index}"
        print("note: no --texture given, named them " + ", ".join(f"{k}={v}" for k, v in names.items()))

    clip = None
    if args.clip:
        clip = [float(v) for v in args.clip.split(",")]
        if len(clip) != 6:
            raise SystemExit("--clip needs six comma-separated numbers")
    origin = [int(v) for v in args.origin.split(",")]
    if len(origin) != 3:
        raise SystemExit("--origin needs three comma-separated numbers")

    model, sources, warnings = convert(data, names, clip, tuple(origin), flip_y=args.flip_y, repair=not args.no_repair)

    out = REPO / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    try:
        shown = out.relative_to(REPO)
    except ValueError:
        shown = out
    print(f"wrote {shown}: {len(model['elements'])} elements, textures "
          + ", ".join(f"#{k}={v}" for k, v in model['textures'].items()))

    if args.preview:
        texture_paths = {}
        for name, filename in sources.items():
            candidate = BLOCK_TEXTURES / f"{name}.png"
            if not candidate.exists():
                candidate = Path(args.from_texture_dir or source.parent) / f"{filename}.png"
            texture_paths[name] = candidate
        render_preview(model, texture_paths, Path(args.preview))
        print(f"  preview: {args.preview}")

    if args.copy_textures:
        png_dir = Path(args.from_texture_dir or source.parent)
        for name, filename in sources.items():
            src = png_dir / f"{filename}.png"
            if not src.exists():
                print(f"error: {src} not found -- cannot copy the texture", file=sys.stderr)
                return 1
            dst = BLOCK_TEXTURES / f"{name}.png"
            shutil.copyfile(src, dst)
            from PIL import Image
            with Image.open(dst) as image:
                size = image.size
            print(f"  {name}.png  <- {src.name}  {size[0]}x{size[1]}")
    else:
        print(f"  referenced textures: {sources} (use --copy-textures to install them)")

    for warning in warnings:
        print(f"warning: {warning}")
    if not warnings:
        print("  fits inside the block cube")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())