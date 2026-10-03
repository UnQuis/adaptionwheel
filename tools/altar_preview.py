"""Растеризует block-модель в PNG: ровно то, что видит игрок, но без игры.

Существует потому, что «в мире видно кубики других блоков» — это симптом, который
невозможно объяснить, глядя на JSON. Идёт по элементам, берёт их UV и кладёт на
текстуру, которую задаёт bound-модель — то есть повторяет ровно то подстановочное
правило, которым пользуется игра.

    python3 tools/altar_preview.py [models/block/resonance_altar_21.json] out.png
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/adaptionwheel"

# Тот же разбор, что делает bound-модель: ключ родителя -> текстура мода.
BOUND = {
    "0": "textures/block/resonance_altar_core.png",
    "1": "textures/block/resonance_altar.png",
}

SIZE = 480
SCALE = 8.0
CX, CY = SIZE / 2, SIZE / 2 + 40


def project(p):
    """Изометрия: x вправо-вниз, z влево-вниз, y вверх."""
    x, y, z = p
    return (CX + (x - z) * 0.866 * SCALE, CY + (x + z) * 0.5 * SCALE - y * SCALE)


def rotate(points, rot):
    if not rot:
        return points
    axis, angle = rot["axis"], rot["angle"]
    ox, oy, oz = rot["origin"]
    ca, sa = round(math.cos(math.radians(angle))), round(math.sin(math.radians(angle)))
    out = []
    for x, y, z in points:
        dx, dy, dz = x - ox, y - oy, z - oz
        if axis == "x":
            dy, dz = dy * ca - dz * sa, dy * sa + dz * ca
        elif axis == "y":
            dx, dz = dx * ca + dz * sa, -dx * sa + dz * ca
        else:
            dx, dy = dx * ca - dy * sa, dx * sa + dy * ca
        out.append((ox + dx, oy + dy, oz + dz))
    return out


# Порядок индексов внутри from/to: x, затем y, затем z — как в BlockElement.
CORNERS = [(x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)]
FACES = {
    "down": (0, 1, 3, 2), "up": (4, 5, 7, 6),
    "west": (0, 4, 6, 2), "east": (1, 5, 7, 3),
    "north": (0, 3, 7, 4), "south": (1, 2, 6, 5),
}
SHADE = {"up": 1.0, "down": 0.45, "north": 0.72, "south": 0.62, "east": 0.86, "west": 0.55}


def shade(img, factor):
    if factor == 1.0:
        return img
    rgb = img.convert("RGB").point(lambda v: min(255, int(v * factor)))
    out = rgb.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def paste_quad(dst, patch, quad):
    """Image.QUAD: source rectangle -> произвольный четырёхугольник."""
    xs = [p[0] for p in quad]
    ys = [p[1] for p in quad]
    x0, y0 = int(min(xs)), int(min(ys))
    w, h = max(1, int(max(xs) - x0)), max(1, int(max(ys) - y0))
    src = [0, 0, patch.width - 1, 0, patch.width - 1, patch.height - 1, 0, patch.height - 1]
    tgt = [quad[0][0] - x0, quad[0][1] - y0, quad[1][0] - x0, quad[1][1] - y0,
           quad[2][0] - x0, quad[2][1] - y0, quad[3][0] - x0, quad[3][1] - y0]
    dst.paste(patch.transform((w, h), Image.QUAD, src + tgt, Image.BILINEAR), (x0, y0))


def main():
    model_path = sys.argv[1] if len(sys.argv) > 1 else "models/block/resonance_altar_21.json"
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("/tmp/opencode/altar_render.png")
    model = json.loads((ASSETS / model_path).read_text())

    textures = {k: Image.open(ASSETS / v).convert("RGBA") for k, v in BOUND.items()}
    fallback = Image.new("RGBA", (16, 16), (255, 0, 255, 255))

    faces = []
    for el in model["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        pts = rotate([(x0 + dx * (x1 - x0), y0 + dy * (y1 - y0), z0 + dz * (z1 - z0))
                      for dx, dy, dz in CORNERS], el.get("rotation"))
        for name, idx in FACES.items():
            spec = el.get("faces", {}).get(name)
            if spec is None:
                continue
            poly = [pts[i] for i in idx]
            # Ближе к камере там, где больше x, меньше z и меньше y: рисовать от
            # дальнего к ближнему, иначе верхние грани затирают передние.
            depth = sum((p[2] - p[0] + p[1]) for p in poly) / 4.0
            faces.append((depth, [project(p) for p in poly], spec, name))
    faces.sort(key=lambda f: f[0])   # painters algorithm: сверху вниз по y

    img = Image.new("RGBA", (SIZE, SIZE), (26, 26, 30, 255))
    for _, quad, spec, name in faces:
        tex = textures.get(spec.get("texture", "#0").lstrip("#"), fallback)
        uv = spec.get("uv")
        if uv is None:
            patch = fallback
        else:
            # Правило игры: UV измеряются в 1/16 ширины спрайта, а не в пикселях
            # (FaceBakery: sprite.getU(faceUV.getU(i) / 16.0F)). Без этого превью
            # показывает не то, что увидит игрок.
            u0, v0, u1, v1 = (c * tex.width / 16.0 if i % 2 == 0 else c * tex.height / 16.0
                              for i, c in enumerate(uv))
            lo_u, hi_u = sorted((u0, u1))
            lo_v, hi_v = sorted((v0, v1))
            patch = tex.crop((int(lo_u), int(lo_v),
                              max(int(hi_u), int(lo_u) + 1), max(int(hi_v), int(lo_v) + 1)))
            if u1 < u0:
                patch = patch.transpose(Image.FLIP_LEFT_RIGHT)
            if v1 < v0:
                patch = patch.transpose(Image.FLIP_TOP_BOTTOM)
        paste_quad(img, shade(patch, SHADE.get(name, 0.8)), quad)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path)
    print("wrote", out_path, "| граней:", len(faces))


if __name__ == "__main__":
    main()
