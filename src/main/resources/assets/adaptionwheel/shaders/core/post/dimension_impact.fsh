#version 150

uniform sampler2D Sampler0;

uniform vec2 ImpactSize;
// x strength, y chromatic aberration, z flash, w edge gain.
uniform vec4 ImpactParams;
// x = panel age 0..1. The original drove its post effects off a uProgress parameter rather than
// off opacity alone, which is the difference between a wipe and a dissolve.
uniform vec4 ImpactProgress;
// x = shockwave gain, y = radial speed-line gain, z = screentone crawl rate, w = ink darkness.
uniform vec4 ImpactBurst;
uniform vec4 ImpactInk;
uniform vec4 ImpactPaper;

in vec2 texCoord;
out vec4 fragColor;

const float TAU = 6.28318531;

float lum(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
}

float hash11(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    // Pass A runs this very program with strength 0, purely to copy the frame somewhere we are
    // not drawing into. The early-out therefore has to come before any neighbourhood work.
    if (ImpactParams.x <= 0.0005) {
        fragColor = vec4(scene(texCoord), 1.0);
        return;
    }

    vec2 uv = texCoord;
    vec2 texel = 1.0 / ImpactSize;
    float aspect = ImpactSize.x / ImpactSize.y;
    vec2 centred = uv - 0.5;

    // Aspect-corrected radial coordinates. The pattern layer is drawn in this space so the
    // shockwave stays circular and the speed lines stay evenly spaced on any window shape.
    vec2 radial = centred * vec2(aspect, 1.0);
    float radius = length(radial);
    float angle = atan(radial.y, radial.x);

    // Chromatic aberration whose split grows with the square of the radius, so the middle of the
    // frame holds still and only the edges tear.
    float r2 = dot(radial, radial);
    float split = ImpactParams.y * r2 * 0.06;
    vec3 col = vec3(scene(uv + centred * split).r,
                    scene(uv).g,
                    scene(uv - centred * split).b);
    float l = lum(col);

    // Edge mask taken off the split image's own gradient, so the ink follows the distortion
    // rather than describing the undistorted scene.
    float gx = lum(scene(uv + vec2(texel.x, 0.0))) - lum(scene(uv - vec2(texel.x, 0.0)));
    float gy = lum(scene(uv + vec2(0.0, texel.y))) - lum(scene(uv - vec2(0.0, texel.y)));
    float ink = smoothstep(0.05, 0.05 + 0.11 * ImpactParams.w, length(vec2(gx, gy)));

    // The eight-tap ring average is the whole trick. Thresholding two tones against each pixel's
    // own neighbourhood instead of against a fixed level is what lets one frame read over a noon
    // sky and over a black cave alike, and survive the white flash sitting underneath it — a
    // fixed threshold either clips one of those to a flat silhouette or does nothing on the other.
    float avg = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * (TAU / 8.0);
        avg += lum(scene(uv + vec2(cos(a) / aspect, sin(a)) * 0.05));
    }
    avg *= 0.125;

    float lit = step(avg + 0.02, l);
    float core = step(avg + 0.24, l);

    // Manga screentone: a dot grid turned 45 degrees, the dots growing as the tone darkens.
    // The grid crawls outward on the panel's own clock so the frame is never a still image.
    float crawl = ImpactProgress.x * ImpactBurst.z;
    vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv / texel) / 5.0 - vec2(crawl, crawl * 0.6);
    float tone = clamp((l - avg) * 5.0 + 0.5, 0.0, 1.0);
    float dots = step(length(fract(tp) - 0.5), sqrt(1.0 - tone) * 0.62);
    float mid = smoothstep(0.15, 0.35, tone) * (1.0 - smoothstep(0.65, 0.85, tone));

    vec3 frame = ImpactPaper.rgb * lit;
    frame = mix(frame, ImpactInk.rgb * dots, mid * 0.92);
    frame = mix(frame, ImpactInk.rgb * 0.25, ink * ImpactBurst.w);
    frame = mix(frame, vec3(1.0), core);

    // ---------------------------------------------------------------------------------------
    // The harsh layer. All of it is driven by panel age rather than by opacity, so it reads as a
    // drawn burst that happens and is over, rather than as the whole image gently dimming.
    // ---------------------------------------------------------------------------------------

    // A burst only exists in the opening ~28% of the panel's life (about 125 ms). The decay is a
    // fractional power rather than a smoothstep: smoothstep collapsed the ring's strength faster
    // than the ring could cross the frame, so it was gone before it reached the edges.
    float bp = clamp(ImpactProgress.x / 0.28, 0.0, 1.0);
    float burst = pow(1.0 - bp, 0.6);

    // Shockwave: one hard ring leaving the centre, sized so it reaches the top and bottom edge
    // right as the burst ends. Its width stays near-constant in screen terms so it reads as a
    // drawn line rather than smearing as it grows.
    float ringR = 0.66 * pow(bp, 0.65);
    float ringW = 0.075 + 0.05 * ImpactProgress.x;
    float ring = smoothstep(ringW, ringW * 0.3, abs(radius - ringR)) * burst * ImpactBurst.x;

    // Radial speed lines (the manga shuuchuusen): 32 angle-quantised wedges, ~45% of them picked,
    // each thinned to a ray that widens outward, banded in radius so the corners stay clean.
    // 96 spokes was the first guess and every ray came out under a pixel wide, i.e. invisible.
    float spokes = 32.0;
    float wedgeId = floor(angle / TAU * spokes);
    float pick = step(0.55, hash11(wedgeId));
    float wedge = abs(fract(angle / TAU * spokes) - 0.5);
    float ray = 1.0 - smoothstep(0.10, 0.16, wedge);
    float taper = 0.25 + 0.75 * clamp(radius / 0.55, 0.0, 1.0);
    float band = smoothstep(0.05, 0.30, radius) * (1.0 - smoothstep(0.55, 1.0, radius));
    float lines = pick * ray * taper * band * burst * ImpactBurst.y;

    // Both burst terms are pure ink: they sit on top of the two-tone frame as drawn lines.
    frame = mix(frame, ImpactInk.rgb, clamp(ring + lines * 0.85, 0.0, 1.0));

    vec3 result = mix(col, frame, ImpactParams.x);
    result += ImpactPaper.rgb * ImpactParams.z;

    fragColor = vec4(result, 1.0);
}