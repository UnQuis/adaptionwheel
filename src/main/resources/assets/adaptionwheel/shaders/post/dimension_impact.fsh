#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform DimensionImpactUniforms {
    vec2 ImpactSize;
    vec4 ImpactParams;
    vec4 ImpactProgress;
    vec4 ImpactBurst;
    vec4 ImpactInk;
    vec4 ImpactPaper;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318531;

float lum(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 scene(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
}

float hash11(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    if (ImpactParams.x <= 0.0005) {
        fragColor = vec4(scene(texCoord), 1.0);
        return;
    }

    vec2 uv = texCoord;
    vec2 texel = 1.5 / ImpactSize;
    float aspect = ImpactSize.x / ImpactSize.y;
    vec2 centre = ImpactProgress.zw;

    // Modes 0 and 1 are the shipped manga pair: black ink on white paper, then its exact inverse.
    float inverseMode = mod(floor(ImpactProgress.y + 0.5), 2.0);
    vec3 inkColor = mix(ImpactInk.rgb, ImpactPaper.rgb, inverseMode);
    vec3 paperColor = mix(ImpactPaper.rgb, ImpactInk.rgb, inverseMode);

    vec2 radial = (uv - centre) * vec2(aspect, 1.0);
    float r2 = dot(radial, radial);
    float split = ImpactParams.y * r2 * 0.08;
    vec3 col = vec3(scene(uv + radial * split).r, scene(uv).g, scene(uv - radial * split).b);
    float l = lum(col);

    float gx = lum(scene(uv + vec2(texel.x, 0.0))) - lum(scene(uv - vec2(texel.x, 0.0)));
    float gy = lum(scene(uv + vec2(0.0, texel.y))) - lum(scene(uv - vec2(0.0, texel.y)));
    float ink = smoothstep(0.05, 0.16, length(vec2(gx, gy))) * ImpactParams.w;

    float avg = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * (TAU / 8.0);
        avg += lum(scene(uv + vec2(cos(a) / aspect, sin(a)) * 0.045));
    }
    avg *= 0.125;

    float two = step(avg + 0.004, l);
    float core = step(avg + 0.18, l);

    float crawl = ImpactProgress.x * ImpactBurst.z;
    vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv * ImpactSize) / 9.0
              - vec2(crawl * 2.0, crawl * 1.2);
    float tone = 1.0 - smoothstep(0.36, 0.96, l);
    float dots = step(length(fract(tp) - 0.5), sqrt(clamp(tone, 0.0, 1.0)) * 0.46);
    float mid = smoothstep(0.28, 0.44, tone) * (1.0 - smoothstep(0.62, 0.80, tone));

    vec3 frame = paperColor * two;
    frame = mix(frame, inkColor * dots, mid * 0.8);
    frame = mix(frame, inkColor * 0.25, ink * ImpactBurst.w);
    frame = mix(frame, paperColor, core);

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

    float edgeRadius = length((uv - 0.5) * vec2(aspect, 1.0));
    float vignette = smoothstep(0.55, 1.05 - ImpactProgress.x * 0.35, edgeRadius);
    frame = mix(frame, inkColor, vignette * 0.85);

    vec3 result = mix(col, frame, clamp(ImpactParams.x, 0.0, 1.0));

    // A value outside [-1, 1] is the opening white detonation. Otherwise the sign alternates the
    // flash between the current paper and ink colours, matching the frame's inverse cut.
    float encodedFlash = ImpactParams.z;
    bool detonation = abs(encodedFlash) > 1.0;
    float flashStrength = detonation
            ? clamp(abs(encodedFlash) - 1.0, 0.0, 1.0)
            : clamp(abs(encodedFlash), 0.0, 1.0);
    vec3 flashColor = detonation ? vec3(1.0)
            : (encodedFlash < 0.0 ? inkColor : paperColor);
    result = mix(result, flashColor, flashStrength);

    fragColor = vec4(result, 1.0);
}
