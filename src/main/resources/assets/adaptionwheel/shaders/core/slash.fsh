#version 150

// Colouring only. The blade's shape is a swept volume built on the CPU
// (SlashBladeMesh), and the fragment stage is left with nothing to decide but what colour a
// point is -- so there is no SlashShape uniform here at all, and the edges that used to be
// feathered by a smoothstep are now real geometry.

uniform vec4 SlashTint;  // цвет "ядра" (тушь), coreness -> 1
uniform vec4 SlashGlow;  // цвет ауры, coreness -> 0
uniform float GameTime;

in vec2 texCoord; // x = позиция вдоль клинка (0 = рукоять, 1 = остриё), y = coreness, запечённый на CPU
out vec4 fragColor;

void main() {
    float u = texCoord.x;
    float coreness = clamp(texCoord.y, 0.0, 1.0);

    vec3 color = mix(SlashGlow.rgb, SlashTint.rgb, coreness);
    float alpha = mix(SlashGlow.a, SlashTint.a, coreness);

    // Скринтон на переходной полосе между ядром и аурой - тот же приём, что в impact-панели:
    // точечный паттерн вместо градиента, чтобы клинок читался как тушь, а не аэрография.
    float band = 1.0 - abs(coreness * 2.0 - 1.0);
    vec2 dotSpace = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (texCoord * vec2(40.0, 10.0));
    float dots = step(length(fract(dotSpace) - 0.5), mix(0.1, 0.5, band));
    color = mix(color, SlashTint.rgb, dots * band * 0.7);

    // Штрихи движения вдоль клинка, бегущие по GameTime.
    float hatchLane = fract(u * 9.0 - GameTime * 4.0);
    float hatch = step(0.86, hatchLane) * coreness;
    color = mix(color, SlashTint.rgb, hatch * 0.6);

    if (alpha < 0.01) {
        discard;
    }
    fragColor = vec4(color, alpha);
}