#version 330
#extension GL_ARB_separate_shader_objects : require

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

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec3 fresnelSchlick(vec3 f0, float cosTheta) {
    float f = pow(1.0 - cosTheta, 5.0);
    return f0 + (1.0 - f0) * f;
}

float distributionGGX(float NoH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float denom = (NoH * NoH) * (a2 - 1.0) + 1.0;
    return a2 / (3.14159265 * denom * denom + 1e-6);
}

float geometrySmith(float NoV, float NoL, float roughness) {
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
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 fakeEnvironment(vec3 R, float roughness, vec3 baseSky, vec3 baseGround) {
    float up = clamp(R.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 sky = mix(baseGround, baseSky, smoothstep(0.2, 0.95, up));
    vec3 horizon = mix(baseGround, baseSky * 0.9 + vec3(0.15, 0.14, 0.12), 0.5);
    vec3 env = mix(horizon, sky, smoothstep(0.3, 0.8, up));
    float specBand = pow(max(R.y, 0.0), mix(10.0, 3.0, roughness));
    env += baseSky * specBand * (0.18 * (1.0 - roughness * 0.5));
    vec3 avg = mix(baseGround, baseSky, 0.4);
    return mix(env, avg, clamp(roughness * 0.55, 0.0, 0.8));
}

vec4 calculateFinalColor(vec4 color, vec3 N, vec3 V) {
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    vec3 albedo = color.rgb;
    // Preserve texture/material detail; avoid washing out to white
    float roughness = 0.48;
    float metallic = 0.92;
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 L = normalize(vec3(0.35, 0.85, 0.45));
    vec3 H = normalize(V + L);
    float NoV = max(dot(N, V), 0.001);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);

    vec3 F = fresnelSchlick(F0, VoH);
    float NDF = distributionGGX(NoH, roughness);
    float G = geometrySmith(NoV, NoL, roughness);
    vec3 specular = (NDF * G * F) / max(4.0 * NoV * NoL, 0.001);
    vec3 kS = F;
    vec3 kD = (1.0 - kS) * (1.0 - metallic);

    vec3 radiance = vec3(0.95, 0.92, 0.86);
    vec3 Lo = (kD * albedo / 3.14159265 + specular * 0.8) * radiance * NoL;

    #ifndef EMISSIVE
    vec3 lm = clamp(lightMapColor.rgb, 0.0, 1.0);
    #else
    vec3 lm = vec3(1.0);
    #endif
    float skyAmt = clamp(lm.r * 0.85 + lm.g * 0.15, 0.15, 1.0);
    vec3 skyCol = mix(vec3(0.22, 0.24, 0.30), vec3(0.62, 0.72, 0.88), skyAmt);
    vec3 groundCol = vec3(0.14, 0.13, 0.12);
    vec3 R = reflect(-V, N);
    vec3 env = fakeEnvironment(R, roughness, skyCol, groundCol);
    vec3 ambientSpec = env * fresnelSchlick(F0, NoV) * (0.55 + 0.35 * (1.0 - roughness));
    vec3 ambientDiff = albedo * skyCol * 0.18 * (1.0 - metallic);

    float rim = pow(1.0 - NoV, 3.0);
    vec3 rimCol = mix(albedo, vec3(1.0, 0.85, 0.45), 0.35) * rim * 0.18;

    vec3 shaded = Lo + ambientDiff + ambientSpec + rimCol;
    // keep texture readable: blend with lit albedo base
    vec3 baseLit = albedo * mix(vec3(0.45), vec3(1.0), lm);
    shaded = mix(baseLit, shaded, 0.55);

    shaded = aces(shaded * 1.02);
    color = vec4(shaded, color.a);

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    #else
    vec4 faceVertexColor = vertexColor;
    #endif

    vec3 N = normalize(viewNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(-viewPos);
    // Face/vertex colors carry mesh material tint and directional shading; keep but don't over-darken
    color *= vec4(mix(vec3(1.0), faceVertexColor.rgb, 0.5), faceVertexColor.a) * ColorModulator;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color, N, V);
    #endif
}
