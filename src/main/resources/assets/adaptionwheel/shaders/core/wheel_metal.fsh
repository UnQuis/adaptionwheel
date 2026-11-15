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
    vec3 sky = mix(baseGround, baseSky, smoothstep(0.25, 0.9, up));
    vec3 horizon = mix(baseGround, vec3(1.0, 0.97, 0.92), 0.6);
    vec3 env = mix(horizon, sky, smoothstep(0.35, 0.75, up));
    float specBand = pow(max(R.y, 0.0), mix(14.0, 2.5, roughness));
    env += vec3(1.0, 0.95, 0.85) * specBand * (0.45 * (1.0 - roughness * 0.6));
    float strip = pow(abs(sin(atan(R.z, R.x) * 3.0 + R.y * 2.0)), mix(18.0, 4.0, roughness));
    env += vec3(0.9, 0.95, 1.0) * strip * 0.08 * (1.0 - roughness);
    vec3 avg = mix(baseGround, baseSky, 0.45);
    return mix(env, avg, clamp(roughness * 0.7, 0.0, 0.85));
}

vec4 calculateFinalColor(vec4 color, vec3 N, vec3 V) {
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    // PBR-ish metal shading in view space (ported/adapted from 1.21.1 wheel_metal concept)
    vec3 albedo = color.rgb;
    float roughness = 0.42;
    float metallic = 1.0;
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

    vec3 radiance = vec3(1.05, 1.0, 0.95);
    vec3 Lo = (kD * albedo / 3.14159265 + specular) * radiance * NoL;

    float skyAmt = clamp(lightMapColor.r * 0.9 + 0.1, 0.1, 1.0);
    vec3 skyCol = mix(vec3(0.18, 0.2, 0.28), vec3(0.7, 0.82, 1.0), skyAmt);
    vec3 groundCol = vec3(0.16, 0.15, 0.13);
    vec3 R = reflect(-V, N);
    vec3 env = fakeEnvironment(R, roughness, skyCol, groundCol);
    vec3 ambientSpec = env * fresnelSchlick(F0, NoV) * (0.9 + 0.6 * (1.0 - roughness));
    vec3 ambientDiff = albedo * skyCol * 0.12 * (1.0 - metallic);

    float rim = pow(1.0 - NoV, 3.0);
    vec3 rimCol = vec3(1.0, 0.85, 0.35) * rim * 0.35;

    vec3 shaded = Lo + ambientDiff + ambientSpec + rimCol;

    #ifndef EMISSIVE
    shaded *= mix(vec3(0.55), vec3(1.0), clamp(lightMapColor.rgb, 0.0, 1.0));
    #else
    shaded *= vec3(1.0);
    #endif

    shaded = aces(shaded * 1.15);
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
    color *= faceVertexColor * ColorModulator;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color, N, V);
    #endif
}
