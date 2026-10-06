#version 150

uniform sampler2D Sampler0;

uniform vec2 ImpactSize;
// x strength, y chromatic aberration, z flash, w edge gain.
uniform vec4 ImpactParams;
// x panel age 0..1. y reserved for future use. zw impact centre, in screen uv.
uniform vec4 ImpactProgress;
// x shockwave gain, y speed-line gain, z screentone crawl rate, w ink darkness.
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
    // The copy pass runs this very shader with strength 0, purely to move the frame into the
    // scratch target before anything here reads its neighbours. Has to come first.
    if (ImpactParams.x <= 0.0005) {
        fragColor = vec4(scene(texCoord), 1.0);
        return;
    }

    vec2 uv = texCoord;
    vec2 texel = 1.5 / ImpactSize;
    float aspect = ImpactSize.x / ImpactSize.y;
    vec2 centre = ImpactProgress.zw;

    // Chromatic aberration growing with the square of the radius from the impact centre, so the
    // middle of the frame holds still and only the edges tear.
    vec2 radial = (uv - centre) * vec2(aspect, 1.0);
    float r2 = dot(radial, radial);
    float split = ImpactParams.y * r2 * 0.08;
    vec3 col = vec3(scene(uv + radial * split).r, scene(uv).g, scene(uv - radial * split).b);
    float l = lum(col);

    // Ink outline off a 1.5px central difference - this is what stops the two-tone split below
    // reading as a threshold filter: a drawn impact frame inks its silhouettes.
    float gx = lum(scene(uv + vec2(texel.x, 0.0))) - lum(scene(uv - vec2(texel.x, 0.0)));
    float gy = lum(scene(uv + vec2(0.0, texel.y))) - lum(scene(uv - vec2(0.0, texel.y)));
    float ink = smoothstep(0.05, 0.16, length(vec2(gx, gy))) * ImpactParams.w;

    // Eight-tap ring average. Thresholding against each pixel's own neighbourhood instead of a
    // fixed brightness is what lets the same two tones read over a noon sky and a black cave
    // alike, and survive the white flash sitting underneath this whole pass.
    float avg = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * (TAU / 8.0);
        avg += lum(scene(uv + vec2(cos(a) / aspect, sin(a)) * 0.045));
    }
    avg *= 0.125;

    float two = step(avg + 0.004, l);
    float core = step(avg + 0.18, l);

    // Screentone: a 45-degree dot grid, the dots growing as the tone darkens, crawling outward on
    // the panel's own clock so the frame is never a still image.
    float crawl = ImpactProgress.x * ImpactBurst.z;
    vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv * ImpactSize) / 9.0
              - vec2(crawl * 2.0, crawl * 1.2);
    // Keyed off absolute brightness, not off local contrast: a flat region (sky, a wall in
    // shadow) matches its own ring average exactly, so a contrast-driven tone has no reading
    // there at all and every flat pixel lands in dirty midtone dots instead of clean paper.
    float tone = 1.0 - smoothstep(0.36, 0.96, l);
    float dots = step(length(fract(tp) - 0.5), sqrt(clamp(tone, 0.0, 1.0)) * 0.46);
    float mid = smoothstep(0.28, 0.44, tone) * (1.0 - smoothstep(0.62, 0.80, tone));

    vec3 frame = ImpactPaper.rgb * two;
    frame = mix(frame, ImpactInk.rgb * dots, mid * 0.8);
    frame = mix(frame, ImpactInk.rgb * 0.25, ink * ImpactBurst.w);
    frame = mix(frame, vec3(1.0), core);

    // The drawn burst: one hard ring leaving the impact centre, sized to reach the edges as its
    // own short window ends, plus radial speed lines. Both decay on their own fast clock
    // (ImpactProgress.x against a ~0.28 window, about 125ms) independently of the panel's own
    // slower fade, so the burst reads as a single instant rather than fading with the panel.
    float radius = length(radial);
    float angle = atan(radial.y, radial.x);

    float bp = clamp(ImpactProgress.x / 0.28, 0.0, 1.0);
    float burst = pow(1.0 - bp, 0.6);

    float ringR = 0.7 * pow(bp, 0.6);
    float ringW = 0.024 + 0.018 * ImpactProgress.x;
    float ring = smoothstep(ringW, ringW * 0.3, abs(radius - ringR)) * burst * ImpactBurst.x;

    float spokes = 32.0;
    float wedgeId = floor(angle / TAU * spokes);
    float pick = step(0.5, hash11(wedgeId));
    float wedge = abs(fract(angle / TAU * spokes) - 0.5);
    float ray = 1.0 - smoothstep(0.05, 0.095, wedge);
    float taper = 0.25 + 0.75 * clamp(radius / 0.6, 0.0, 1.0);
    float band = smoothstep(0.04, 0.28, radius) * (1.0 - smoothstep(0.6, 1.05, radius));
    float lines = pick * ray * taper * band * burst * ImpactBurst.y;

    vec3 harsh = clamp(frame * 0.12, vec3(0.02), vec3(0.35));
    float hard = clamp(smoothstep(0.05, 0.45, ring) + smoothstep(0.05, 0.5, lines), 0.0, 1.0) * 0.8;
    frame = mix(frame, harsh, hard);

    // Void vignette: the edges of the screen are visibly being consumed, closing in as the
    // panel's own life runs out - this is what makes it read as a dimension failing rather than
    // as a colour grade laid over an otherwise untouched frame.
    float edgeRadius = length((uv - 0.5) * vec2(aspect, 1.0));
    float vignette = smoothstep(0.55, 1.05 - ImpactProgress.x * 0.35, edgeRadius);
    frame = mix(frame, ImpactInk.rgb, vignette * 0.85);

    vec3 result = mix(col, frame, clamp(ImpactParams.x, 0.0, 1.0));
    result += ImpactPaper.rgb * ImpactParams.z;

    fragColor = vec4(result, 1.0);
}