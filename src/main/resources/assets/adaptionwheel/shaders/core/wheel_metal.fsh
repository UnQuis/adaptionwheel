#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif
#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif
#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec3 worldNormal;
layout(location = 8) in vec3 relPos;
layout(location = 9) in vec3 worldPos;
layout(location = 10) in float skyLight;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const float PI = 3.14159265;

// A fixed world-space key light. The 26.3 pipeline has no uniform for the real sun angle --
// Globals.GameTime is world AGE, not time of day, so it goes wrong after /time set or a sleep --
// while the old view-space Light0/Light1 is a headlamp: the highlight follows the camera instead
// of the world, which is what makes a metal look painted. One fixed direction keeps the glint
// attached to the world and lets it sweep across the surface as the wheel turns.
const vec3 KEY_LIGHT = vec3(-0.449, 0.808, 0.382);

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, vec3(0.0), vec3(1.0)); }

// --- Noise/corrosion, anchored to world position so it does not crawl as the player walks ---
float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.11369, 0.13787));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise(p);
        p = p * 2.03 + vec3(17.0, 9.0, 13.0);
        a *= 0.5;
    }
    return v;
}

// --- BRDF helpers ---

vec3 fresnelSchlick(vec3 f0, float cosTheta) {
    return f0 + (1.0 - f0) * pow(1.0 - saturate(cosTheta), 5.0);
}

float distributionGGX(float NoH, float a) {
    float a2 = a * a;
    float denom = (NoH * NoH) * (a2 - 1.0) + 1.0;
    return a2 / (PI * denom * denom + 1e-6);
}

float geometrySmithSchlickGGX(float NoV, float NoL, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    float gv = NoV / (NoV * (1.0 - k) + k);
    float gl = NoL / (NoL * (1.0 - k) + k);
    return gv * gl;
}

vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return saturate((x * (a * x + b)) / (x * (c * x + d) + e));
}

/** A line every 1/n of the way along `v`, `w` wide. Used for the reflected studio grid. */
float gridLine(float v, float n, float w) {
    float f = fract(v * n);
    return smoothstep(0.5 - w, 0.5, abs(f - 0.5));
}

/**
 * The sky, seen along a reflected ray. `R` is in WORLD space, so the horizon stays on the world
 * horizon instead of following the camera pitch, and `day` (daylight, not torchlight) gates the
 * sun, the bright band and the studio strips: underground and at night the metal reflects the
 * dark sky and nothing else.
 */
vec3 environment(vec3 R, float rough, vec3 sky, vec3 ground, float day, float skyAccess) {
    float up = R.y;
    // Daylight drives the reflected sky; at night only a faint moon glint remains (added below),
    // so the environment must not keep a bright floor that would read as fullbright.
    float vis = mix(0.02, 1.0, day);

    vec3 zenith = sky * 0.62;
    vec3 horizon = mix(sky * 1.25, vec3(1.00, 0.96, 0.88), 0.28);
    vec3 skyPart = mix(horizon, zenith, smoothstep(-0.02, 0.72, up)) * vis;

    vec3 gnd = mix(ground, ground * 0.35, smoothstep(0.0, -0.55, up));
    vec3 col = mix(gnd, skyPart, smoothstep(-0.045, 0.055, up));

    // Sun disc, bloom and halo, in the same direction as the direct key light.
    float sd = max(dot(R, KEY_LIGHT), 0.0);
    col += vec3(1.00, 0.95, 0.86) * (pow(sd, 3000.0) * 40.0 + pow(sd, 90.0) * 0.55 + pow(sd, 8.0) * 0.08) * (day * day);

    // The moon: a dim blue disc in the same direction. Without it a night sky reflects nothing
    // and the wheel goes completely black; with it the metal catches one hard, cold glint.
    float night = (1.0 - day) * skyAccess;
    col += vec3(0.62, 0.72, 1.00) * (pow(sd, 3000.0) * 6.0 + pow(sd, 90.0) * 0.12) * night;

    // A soft bright band just above the horizon: the thing that makes a gradient read as a sky.
    float band = smoothstep(0.015, 0.10, up) * (1.0 - smoothstep(0.10, 0.40, up));
    col += vec3(1.00, 0.98, 0.94) * band * 0.50 * day;

    // Two vertical strips, so the reflection has structure the wheel can sweep across.
    float az = atan(R.x, R.z);
    float strips = pow(max(0.0, cos(az * 2.0 + 1.2)), 24.0) * smoothstep(0.0, 0.30, up);
    col += vec3(0.90, 0.95, 1.00) * strips * 0.28 * day;

    // Reflected studio grid: meridians and parallels of a dim white cage around the wheel.
    float cage = gridLine(az * (1.0 / (2.0 * PI)), 8.0, 0.012) * 0.65
               + gridLine(up, 6.0, 0.010) * 0.40;
    col += vec3(0.95, 0.97, 1.00) * cage * 0.28 * day * (1.0 - 0.7 * rough);

    // Rough surfaces average the environment out instead of mirroring it.
    vec3 average = mix(horizon * vis, ground, 0.5);
    return mix(col, average, clamp(rough * 0.75, 0.0, 0.85));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    // World-space normal; the CPU applied the wheel's own pose before submitting it.
    vec3 N = normalize(worldNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    // Eye direction: positions are camera-relative, so the camera is at the origin.
    vec3 V = normalize(-relPos);

    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    // Authored OBJ vertex colors carry material tint (gold ring/spheres, bronze beams).
    vec3 albedo = color.rgb * vertexColor.rgb * ColorModulator.rgb;

    // Corrosion anchored to world space: stable while the player walks, and it slides across the
    // surface as the wheel turns, exactly as a real corroded plate would.
    vec3 local = worldPos * 14.0;
    float pit = fbm(local);
    float grit = fbm(local * 3.7 + vec3(11.0, 5.0, 7.0));
    vec3 grad = vec3(
        fbm(local + vec3(0.06, 0.0, 0.0)) - fbm(local - vec3(0.06, 0.0, 0.0)),
        fbm(local + vec3(0.0, 0.06, 0.0)) - fbm(local - vec3(0.0, 0.06, 0.0)),
        fbm(local + vec3(0.0, 0.0, 0.06)) - fbm(local - vec3(0.0, 0.0, 0.06)));
    N = normalize(N + grad * 0.35);

    float NoV = max(dot(N, V), 1e-3);
    vec3 R = reflect(-V, N);

    float baseRough = 0.28;
    float rough = clamp(baseRough + (smoothstep(0.40, 0.78, pit) - 0.12) * 0.45, 0.08, 0.95);
    float a = rough * rough;

    // Darken pits (cheap occlusion/corrosion) but preserve material readability.
    albedo *= 1.0 - 0.28 * smoothstep(0.55, 0.85, pit) - 0.08 * smoothstep(0.60, 0.90, grit);

    #ifndef EMISSIVE
    vec3 lm = saturate(lightMapColor.rgb);
    #else
    vec3 lm = vec3(1.0);
    #endif
    float lmLum = clamp(dot(lm, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);

    // Daylight, as opposed to torchlight: the lightmap cannot tell them apart on its own, but the
    // packed sky level can. A cave under an open sky that is dark is night, not day; a bright
    // torch underground is not the sun.
    float skyAccess = clamp(skyLight, 0.0, 1.0);
    float day = smoothstep(0.22, 0.80, lmLum) * skyAccess;

    vec3 skyCol = mix(vec3(0.012, 0.018, 0.040),
                      mix(vec3(0.42, 0.52, 0.72), lm, 0.35),
                      day);
    skyCol *= mix(0.10, 1.0, skyAccess);
    vec3 groundCol = mix(vec3(0.006, 0.005, 0.004), vec3(0.15, 0.13, 0.10), lmLum);

    vec3 f0 = albedo; // metal reflectance
    float fres = pow(1.0 - NoV, 3.0);
    vec3 env = environment(R, rough, skyCol, groundCol, day, skyAccess);
    vec3 envF = fresnelSchlick(f0, NoV);
    vec3 shaded = env * envF * mix(1.35, 1.0, fres);

    // Direct key light: GGX highlight from the same fixed world direction as the reflected sun.
    vec3 L = KEY_LIGHT;
    vec3 H = normalize(V + L);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    vec3 lightCol = mix(vec3(0.62, 0.72, 1.00), vec3(1.00, 0.95, 0.86), day);
    vec3 specular = distributionGGX(NoH, a) * geometrySmithSchlickGGX(NoV, NoL, rough)
                  * fresnelSchlick(f0, VoH) * NoL * lightCol * 1.6 * (day * day);
    shaded += specular;

    // Entity shade directions (Light0/Light1) give the metal directional highlights that make it
    // read as shaped, not as a flat matte card. They are not torch positions, but in a cave lit by
    // a block source the lightmap is bright and without a directional lobe the wheel just looks
    // uniformly lit; scaled by lmLum (no constant floor) this adds shine only where there is light.
    vec3 Nv = normalize(mat3(ModelViewMat) * N);
    vec3 Vv = normalize(mat3(ModelViewMat) * V);
    float NoVv = max(dot(Nv, Vv), 1e-3);
    for (int i = 0; i < 2; i++) {
        vec3 Lv = normalize(i == 0 ? Light0_Direction : Light1_Direction);
        vec3 Hv = normalize(Vv + Lv);
        float NoLv = max(dot(Nv, Lv), 0.0);
        float NoHv = max(dot(Nv, Hv), 0.0);
        float VoHv = max(dot(Vv, Hv), 0.0);
        vec3 Fv = fresnelSchlick(f0, VoHv);
        float Dv = distributionGGX(NoHv, a);
        float Gv = geometrySmithSchlickGGX(NoVv, NoLv, rough);
        vec3 specV = (Dv * Gv * Fv) / max(4.0 * NoVv * NoLv, 0.001);
        shaded += specV * NoLv * lm * 0.55;
    }

    // A metal has no diffuse, but the wheel must still read as lit by the light it stands in.
    // Kept lower than the specular so a torch reads as shine, not as a flat fullbright wash.
    shaded += albedo * lm * 0.18;

    // Keep a little face-on body so a dim room does not erase the surface entirely, but only
    // where there is actually light; a constant floor here would be visible in pitch darkness.
    shaded += f0 * 0.015 * lmLum;

    // Subtle rim for separation, also gated by light.
    float rim = pow(1.0 - NoV, 3.0);
    shaded += mix(albedo, vec3(1.00, 0.88, 0.55), 0.20) * rim * 0.05 * (0.10 + 0.90 * lmLum);

    // Prevent blowout to white while preserving colour.
    shaded = min(shaded, vec3(1.15));
    shaded = aces(shaded);

    #ifdef OIT_ACCUMULATE
    vec4 outColor = sampleColorForAccumulation(vec4(shaded, color.a));
    vec4 fogColor = vec4(FogColor.rgb * outColor.a, FogColor.a);
    #else
    vec4 outColor = vec4(shaded, color.a);
    vec4 fogColor = FogColor;
    #endif

    #ifndef OIT_ALPHA_ONLY
    fragColor = apply_fog(outColor, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
