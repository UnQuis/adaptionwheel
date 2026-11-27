#!/usr/bin/env python3
"""RED/GREEN check for tools/gen_model_bin.py against the tiny OBJ fixture.

Verifies the pinned FRRB v1 header and the first decoded corner position.
Stdlib only; writes its scratch output under /mnt/Barracuda/tmp/opencode/.
"""

import os
import struct
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONVERTER = os.path.join(ROOT, "tools", "gen_model_bin.py")
OBJ = os.path.join(ROOT, "src", "test", "resources", "models", "fixture.obj")
OUTPUT = "/mnt/Barracuda/tmp/opencode/fixture.bin"


def main():
    subprocess.run([sys.executable, CONVERTER, OBJ, OUTPUT], check=True)
    with open(OUTPUT, "rb") as handle:
        data = handle.read()

    magic, version, layer_count, corners, indices, groups, materials, byte_count = (
        struct.unpack_from("<4sIIIIIII", data, 0)
    )
    assert magic == b"FRRB", "magic: %r" % (magic,)
    assert version == 1, "version: %d" % version
    assert layer_count == 6, "layerCount: %d" % layer_count
    assert byte_count == len(data), "byteCount %d != file length %d" % (byte_count, len(data))

    position = struct.unpack_from("<3f", data, 32)
    expected = (-0.5, -0.5, 0.0)
    assert all(abs(a - b) < 1e-6 for a, b in zip(position, expected)), (position, expected)

    print(
        "PASS: magic=%s version=%d layers=%d corners=%d indices=%d "
        "groups=%d materials=%d bytes=%d first=%s"
        % (magic.decode(), version, layer_count, corners, indices, groups, materials, byte_count, position)
    )


if __name__ == "__main__":
    main()
