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
layout(location = 7) in vec3 viewNormal;
layout(location = 8) in vec3 viewPos;
layout(location = 9) in vec3 localPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const float PI = 3.14159265;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, vec3(0.0), vec3(1.0)); }

// --- Noise/corrosion (ported from 1.21.1 wheel_metal; uses model-local coords for stability) ---
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

vec3 environment(vec3 R, float rough, vec3 sky, vec3 ground, float skyAmount) {
    float up = R.y * 0.5 + 0.5;
    float t = smoothstep(0.25, 0.75, up);
    vec3 env = mix(ground, sky, t);
    env += sky * pow(saturate(R.y), mix(18.0, 5.0, rough)) * (0.10 * skyAmount * (1.0 - rough * 0.6));
    // Mip roughness: blur towards average as roughness rises.
    vec3 avg = mix(ground, sky, 0.45);
    return mix(env, avg, saturate(rough * rough * 0.75));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    vec3 N = normalize(viewNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(-viewPos);

    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    // Authored OBJ vertex colors carry material tint (gold ring/spheres, bronze beams).
    vec3 albedo = color.rgb * vertexColor.rgb * ColorModulator.rgb;

    // Corrosion anchored to model-space mesh coords (stable as wheel moves/camera moves).
    vec3 local = localPos * 14.0;
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
    float lmLum = dot(lm, vec3(0.2126, 0.7152, 0.0722));
    float skyAmount = clamp(lmLum, 0.0, 1.0);
    float blockAmount = clamp(lmLum * 0.7, 0.0, 1.0);

    // Environment/ambient derived from lightmap brightness (view-space approximation).
    vec3 skyCol = mix(vec3(0.10, 0.12, 0.16), vec3(0.50, 0.58, 0.72), skyAmount);
    vec3 groundCol = vec3(0.12, 0.11, 0.10) * (0.30 + 0.70 * skyAmount);

    vec3 F0 = albedo; // metal reflectance
    float fres = pow(1.0 - NoV, 3.0);
    vec3 env = environment(R, rough, skyCol, groundCol, skyAmount);
    vec3 envF = fresnelSchlick(F0, NoV);
    vec3 envSpec = env * envF * (0.55 + 0.25 * (1.0 - rough));

    // Entity light directions (view space) for specular highlights.
    vec3 L0 = normalize(Light0_Direction);
    vec3 L1 = normalize(Light1_Direction);

    vec3 direct = vec3(0.0);
    // Primary key light
    {
        vec3 L = L0;
        vec3 H = normalize(V + L);
        float NoL = max(dot(N, L), 0.0);
        float NoH = max(dot(N, H), 0.0);
        float VoH = max(dot(V, H), 0.0);
        vec3 F = fresnelSchlick(F0, VoH);
        float D = distributionGGX(NoH, a);
        float G = geometrySmithSchlickGGX(NoV, NoL, rough);
        vec3 specular = (D * G * F) / max(4.0 * NoV * NoL, 0.001);
        vec3 lightCol = vec3(1.00, 0.97, 0.92) * (0.35 + 0.65 * skyAmount);
        direct += specular * NoL * lightCol * 1.4;
    }
    // Secondary fill light
    {
        vec3 L = L1;
        vec3 H = normalize(V + L);
        float NoL = max(dot(N, L), 0.0);
        float NoH = max(dot(N, H), 0.0);
        float VoH = max(dot(V, H), 0.0);
        vec3 F = fresnelSchlick(F0, VoH);
        float D = distributionGGX(NoH, a);
        float G = geometrySmithSchlickGGX(NoV, NoL, rough);
        vec3 specular = (D * G * F) / max(4.0 * NoV * NoL, 0.001);
        vec3 lightCol = vec3(0.70, 0.78, 0.95) * (0.15 + 0.35 * skyAmount);
        direct += specular * NoL * lightCol * 0.6;
    }

    // Metal base response: keep albedo/texture visible (avoid white washout).
    vec3 baseLit = albedo * mix(vec3(0.28), vec3(0.95), lm);

    // Warm torch/block contribution, modest.
    vec3 blockWarm = vec3(1.00, 0.62, 0.30);
    vec3 torch = albedo * blockWarm * (0.08 * blockAmount);

    // Subtle rim for separation.
    float rim = pow(1.0 - NoV, 3.0);
    vec3 rimCol = mix(albedo, vec3(1.0, 0.88, 0.55), 0.20) * rim * 0.08;

    vec3 shaded = baseLit * 0.70 + envSpec * 0.30 + direct + torch + rimCol;
    // Prevent blowout to white while preserving color
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
