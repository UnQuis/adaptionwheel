#version 150

// The blade, drawn procedurally: nothing here samples a texture.
//
// The blade line is a parabola, `y = -bow * (1 - x^2)`, which passes through the middle of the
// quad, sits `bow` below it at the belly and rises back to the midline at both tips. A circular
// arc was the obvious alternative and it is the wrong shape here: because the arc has to leave
// the quad somewhere, a circle whose radius equals its centre offset only grazes the quad's
// middle and the blade dies out roughly a third of the way to each edge. The parabola keeps the
// blade across the full width, and `bow` is then a directly meaningful number of half-heights
// instead of an indirect circle radius.
//
// Everything else is a function of the perpendicular distance `d` to that one line, which is what
// lets the taper sharpen the tips and the halo follow the taper rather than staying a blob.

in vec2 vLocal;

// x = bow (half-heights the belly dips), y = belly half-thickness, z = core half-thickness,
// w = tip sharpness.
uniform vec4 SlashShape;
// rgb = blade tint, a = overall alpha.
uniform vec4 SlashTint;
// rgb = halo colour, a = halo strength.
uniform vec4 SlashGlow;
// Seconds since the world started; filled in by ShaderInstance.apply().
uniform float GameTime;

out vec4 fragColor;

void main() {
    // -1..1 with x running along the blade and y across it.
    vec2 p = vLocal * 2.0 - 1.0;

    float bow = SlashShape.x;
    float thickness = SlashShape.y;
    float coreWidth = SlashShape.z;
    float taper = SlashShape.w;

    float bladeY = -bow * (1.0 - p.x * p.x);
    // Dividing by the slope's length turns the vertical gap into a perpendicular one, so the
    // blade keeps an even thickness instead of thinning where it runs steeply.
    float slope = 2.0 * bow * p.x;
    float d = abs(p.y - bladeY) / sqrt(1.0 + slope * slope);

    // Local half-width multiplier: full at the belly, zero at both tips.
    float t = pow(max(0.0, 1.0 - p.x * p.x), taper);

    // Derivative-based edge: the blade stays one pixel wide on screen no matter how far away the
    // slash is, instead of shimmering into aliasing at range. That was the whole reason for
    // moving off the sprite.
    float aa = max(fwidth(d), 1e-4);
    float ad = abs(d);

    float body = 1.0 - smoothstep(thickness * t - aa, thickness * t + aa, ad);
    float core = 1.0 - smoothstep(coreWidth * t - aa, coreWidth * t + aa, ad);

    // Halo measured outward from the blade's own edge, so it follows the taper.
    float halo = exp(-max(ad - thickness * t, 0.0) * 9.0) * t;

    // A highlight running the length of the blade, drifting outward over time. This is the only
    // term that animates on its own; the caller already drives growth and fade.
    float shimmer = 0.86 + 0.14 * sin(vLocal.x * 20.0 - GameTime * 5.0);

    // Manga slashes are a *replacement* of pixels, not an addition: a dark inked stroke with a
    // white-hot cutting edge. The sprite this replaced was dark for exactly this reason -- it had
    // to read against a blown-out noon sky. An additive glow cannot do that: on a near-white sky it
    // simply saturates, and the slash vanishes. So the body is a very dark tint (keeping the
    // per-entity colour identity -- cyan for the cursed slash, violet for the rift), the core burns
    // to white so it still reads in a dark cave, and the halo carries the saturated colour.
    vec3 ink = SlashTint.rgb * 0.16;
    vec3 colour = ink * body * shimmer;
    colour = mix(colour, vec3(1.0), core);
    colour = mix(colour, SlashGlow.rgb, clamp(halo * 0.75, 0.0, 1.0) * (1.0 - core));

    float alpha = clamp(body * 0.92 + core + halo * 0.45, 0.0, 1.0) * SlashTint.a;
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(colour, alpha);
}