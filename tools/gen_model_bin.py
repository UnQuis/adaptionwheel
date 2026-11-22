#!/usr/bin/env python3
"""Convert a Wavefront OBJ into an FRRB v1 model binary (stdlib only).

Usage: python3 tools/gen_model_bin.py OBJECT OUTPUT

Byte layout (all little-endian):
  header   <4sIIIIIII  magic 'FRRB', version 1, layerCount 6, cornerCount,
                        indexCount, groupCount, materialCount, byteCount
  layer 0  cornerCount x 14 four-byte slots: pos3, packed RGBA (float bits),
                        data4 (material id in x, rest 0), aux4 (normal xyz, meta w), uv2
  layer 1  indexCount x <I>     sequential 0..cornerCount-1
  layer 2  groupCount x (<H> length + UTF-8 bytes)
  layer 3  materialCount x (<H> length + UTF-8 bytes)
  layer 4  indexCount/3 x <II>  face -> group / material
  layer 5  <Iffffff>             faceCount, centre xyz, radius, height, reserved

The packed colour's four bytes are the little-endian uint32 written in a float
slot (equivalently struct.unpack('<f', struct.pack('<I', rgba))), so a reader
must recover them with raw float bits, not float arithmetic. The material id is
deliberately NOT in those bytes: it lives in data4.x at full float precision so
0.5 stays exact.

Geometry is centred, wound and shaded exactly as ru.adaptionwheel.client.ObjMesh.parse
does. Every arithmetic step is rounded to float32 to mirror Java's `float` maths.
"""

import math
import os
import struct
import sys
from array import array

HEADER = struct.Struct("<4sIIIIIII")
# The packed colour is written as raw uint32 bytes, not as a Python float: white
# packs to 0xFFFFFFFF (a NaN float) and a double->float round trip may canonicalise it.
CORNER = struct.Struct("<3fI4f4f2f")
METADATA = struct.Struct("<Iffffff")


def f32(value):
    """Round a Python double to the nearest IEEE-754 binary32, as Java `float` does."""
    return struct.unpack("<f", struct.pack("<f", value))[0]


def resolve(token, count):
    """OBJ indices are 1-based; a negative index counts back from the current list."""
    try:
        index = int(token)
    except ValueError:
        return -1
    resolved = index - 1 if index > 0 else count + index
    return resolved if 0 <= resolved < count else -1


def face_normal(a, b, c):
    """Outward flat normal of (a, b, c), in the same winding ObjMesh uses."""
    ux, uy, uz = f32(b[0] - a[0]), f32(b[1] - a[1]), f32(b[2] - a[2])
    vx, vy, vz = f32(c[0] - a[0]), f32(c[1] - a[1]), f32(c[2] - a[2])
    nx = f32(f32(uy * vz) - f32(uz * vy))
    ny = f32(f32(uz * vx) - f32(ux * vz))
    nz = f32(f32(ux * vy) - f32(uy * vx))
    squared = f32(f32(f32(nx * nx) + f32(ny * ny)) + f32(nz * nz))
    length = f32(math.sqrt(squared))
    if length < 1.0e-6:
        return (0.0, 1.0, 0.0)
    return (f32(nx / length), f32(ny / length), f32(nz / length))


def parse_obj(path):
    positions, colours, uvs, normals = [], [], [], []
    triangles = []
    faces = 0

    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            tokens = line.strip().split()
            if not tokens or tokens[0].startswith("#"):
                continue
            key = tokens[0]
            if key == "v" and len(tokens) >= 4:
                positions.append(tuple(f32(float(t)) for t in tokens[1:4]))
                colours.append(
                    tuple(f32(float(t)) for t in tokens[4:7])
                    if len(tokens) >= 7
                    else (1.0, 1.0, 1.0)
                )
            elif key == "vt" and len(tokens) >= 3:
                uvs.append((f32(float(tokens[1])), f32(float(tokens[2]))))
            elif key == "vn" and len(tokens) >= 4:
                normals.append(tuple(f32(float(t)) for t in tokens[1:4]))
            elif key == "f" and len(tokens) - 1 >= 3:
                p, uv, n = [], [], []
                for token in tokens[1:]:
                    parts = token.split("/")
                    pi = resolve(parts[0], len(positions))
                    ui = resolve(parts[1], len(uvs)) if len(parts) > 1 and parts[1] else -1
                    ni = resolve(parts[2], len(normals)) if len(parts) > 2 and parts[2] else -1
                    if pi < 0:
                        break
                    p.append(pi)
                    uv.append(ui)
                    n.append(ni)
                else:
                    for i in range(1, len(p) - 1):
                        triangles.append((p[0], uv[0], n[0], p[i], uv[i], n[i], p[i + 1], uv[i + 1], n[i + 1]))
                    faces += 1

    if not positions or not triangles:
        raise ValueError("no triangles in %s (%d vertices, %d faces)" % (path, len(positions), faces))

    mins = [min(p[i] for p in positions) for i in range(3)]
    maxs = [max(p[i] for p in positions) for i in range(3)]
    centre = tuple(f32(f32(mins[i] + maxs[i]) * 0.5) for i in range(3))

    radius = 0.0
    for p in positions:
        dx, dz = f32(p[0] - centre[0]), f32(p[2] - centre[2])
        radius = max(radius, f32(math.hypot(dx, dz)))
    if radius < 1.0e-4:
        raise ValueError("the mesh has no horizontal extent")

    height = f32(maxs[1] - mins[1])
    return positions, colours, uvs, normals, triangles, faces, centre, radius, height


def pack_rgba(colour):
    """RGBA bytes -> the uint32 whose little-endian bytes are the packed float."""
    channels = [max(0, min(255, int(round(float(c) * 255.0)))) for c in colour[:3]] + [255]
    return sum(value << (8 * i) for i, value in enumerate(channels))


def corner_records(positions, colours, uvs, normals, triangles, centre):
    records = bytearray()
    for triangle in triangles:
        p0, u0, n0, p1, u1, n1, p2, u2, n2 = triangle
        smooth = n0 >= 0 or n1 >= 0 or n2 >= 0
        flat = None if smooth else face_normal(positions[p0], positions[p1], positions[p2])
        for pi, ui, ni in ((p0, u0, n0), (p1, u1, n1), (p2, u2, n2)):
            position = positions[pi]
            colour = colours[pi]
            normal = (
                normals[ni] if ni >= 0
                else (flat if flat is not None else face_normal(positions[p0], positions[p1], positions[p2]))
            )
            uv = uvs[ui] if ui >= 0 else (0.0, 0.0)
            record = (
                f32(position[0] - centre[0]),
                f32(position[1] - centre[1]),
                f32(position[2] - centre[2]),
                pack_rgba(colour),
                colour[0], 0.0, 0.0, 0.0,  # data4.x = material id, full precision
                normal[0], normal[1], normal[2], 0.0,  # aux4.xyz = normal, w = meta
                uv[0], uv[1],
            )
            records += CORNER.pack(*record)
    return records


def convert(obj_path, output_path):
    positions, colours, uvs, normals, triangles, faces, centre, radius, height = parse_obj(obj_path)
    corners = len(triangles) * 3

    layer0 = corner_records(positions, colours, uvs, normals, triangles, centre)

    indices = array("I", range(corners))
    if sys.byteorder != "little":
        indices.byteswap()
    layer1 = indices.tobytes()

    # shortcut: the three shipped OBJs declare no `g`/`usemtl`, so layers 2 and 3
    # are empty and every face maps to (0, 0). Extend if an OBJ with groups ships.
    layer2 = b""
    layer3 = b""
    layer4 = struct.pack("<II", 0, 0) * len(triangles)
    layer5 = METADATA.pack(faces, centre[0], centre[1], centre[2], radius, height, 0.0)

    layers = layer0 + layer1 + layer2 + layer3 + layer4 + layer5
    header = HEADER.pack(b"FRRB", 1, 6, corners, corners, 0, 0, HEADER.size + len(layers))
    with open(output_path, "wb") as handle:
        handle.write(header + layers)

    print(
        "%s: %d in -> %d out bytes, %d faces, %d triangles, %d corners"
        % (os.path.basename(obj_path), os.path.getsize(obj_path), len(header + layers), faces, len(triangles), corners)
    )


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: python3 tools/gen_model_bin.py OBJECT OUTPUT")
    convert(sys.argv[1], sys.argv[2])


if __name__ == "__main__":
    main()
