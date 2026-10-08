#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// texCoord.x runs along the blade 0..1, texCoord.y is coreness: 0 at the edge, 1 at the core.
// Both come from SlashBladeMesh, which already resolved the shape and the glow->tint mix per
// vertex -- that work used to be done here, but there is no client in which to iterate on a shader,
// so the mesh keeps it and this shader only adds the parts that genuinely need per-pixel work.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float u = texCoord.x;
    float coreness = clamp(texCoord.y, 0.0, 1.0);
    float alpha = vertexColor.a;

    if (alpha < 0.01) {
        discard;
    }

    // Globals.GameTime is the fraction of an in-game day, so one unit is 1200 seconds.
    float t = GameTime * 1200.0;

    vec3 color = vertexColor.rgb;

    // Screentone across the band between core and edge. It lives in the blade's own UV rather than
    // in screen space, so it stays glued to the object from every angle instead of swimming across
    // it as the camera turns -- which is the whole reason to spend a fragment shader on this.
    float band = 1.0 - abs(coreness * 2.0 - 1.0);
    vec2 dotSpace = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (texCoord * vec2(48.0, 8.0));
    float dots = step(length(fract(dotSpace) - 0.5), mix(0.12, 0.48, band));
    color *= 1.0 - dots * band * 0.45;

    // Hot strokes running tip-ward along the core, so a still frame still reads as motion.
    float hatch = step(0.86, fract(u * 9.0 - t * 4.0)) * coreness;
    color = mix(color, vec3(1.0), hatch * 0.55);

    fragColor = vec4(color, alpha);
}