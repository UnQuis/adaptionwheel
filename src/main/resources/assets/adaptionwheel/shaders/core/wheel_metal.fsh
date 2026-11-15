#version 150

// Cook-Torrance metal with a reflected environment, written as a Shadertoy-style analytic scene
// rather than as a screen-space pass: the wheel is drawn in the middle of the world pass, where
// the framebuffer it would have to read is the one it is writing, and that read is undefined.
// Everything the metal reflects is therefore evaluated per pixel from the ray direction -- sky
// gradient, sun/moon disc, a bright horizon band and a couple of studio strips -- which is what
// the metal shaders this is modelled on do too, and it costs nothing to make it follow the game's
// own sky colour and sun angle.

uniform sampler2D Sampler0;

uniform vec3 WheelCamera;   // world-space eye
uniform vec3 WheelSun;      // unit vector towards the sun, or towards the moon at night
uniform vec4 WheelSky;      // rgb = the level's sky colour, a = how much sky light there is (0..1)
uniform vec4 WheelParams;   // x = roughness, y = night flag, z = block light (0..1), w = exposure
uniform vec4 WheelGround;   // rgb = what the ground reflects

in vec2 texCoord;
in vec3 worldPos;
in vec3 worldNormal;
in vec4 vColor;

out vec4 fragColor;

const float PI = 3.14159265359;

// ---------------------------------------------------------------------------------------------
// Environment
// ---------------------------------------------------------------------------------------------

/**
 * The sky, seen along a reflected ray. The horizon band is the part that makes metal read as
 * metal: a smooth gradient alone gives a flat tinted blob, while a hard bright line across it is
 * what the eye recognises as a reflection of something.
 */
vec3 environment(vec3 R, float rough) {
    float up = R.y;

    vec3 zenith = WheelSky.rgb * 0.62;
    vec3 horizon = mix(WheelSky.rgb * 1.30, vec3(1.00, 0.96, 0.88), 0.32);
    vec3 sky = mix(horizon, zenith, smoothstep(-0.02, 0.72, up));

    vec3 ground = mix(WheelGround.rgb, WheelGround.rgb * 0.35, smoothstep(0.0, -0.55, up));
    vec3 col = mix(ground, sky, smoothstep(-0.045, 0.055, up));

    // Sun or moon: a hard disc, a tight bloom and a wide halo.
    vec3 lightCol = mix(vec3(1.00, 0.95, 0.86), vec3(0.62, 0.72, 1.00), WheelParams.y);
    float sd = max(dot(R, WheelSun), 0.0);
    col += lightCol * (pow(sd, 3000.0) * 40.0 + pow(sd, 90.0) * 0.55 + pow(sd, 8.0) * 0.08);

    // A soft bright band sitting just above the horizon -- the studio softbox every metal demo
    // reflects, and the reason the wheel's upper half catches a line of light.
    float band = smoothstep(0.015, 0.10, up) * (1.0 - smoothstep(0.10, 0.40, up));
    col += vec3(1.00, 0.98, 0.94) * band * 0.55 * (0.25 + 0.75 * WheelSky.a);

    // Two vertical strips, so the reflection has structure the wheel can sweep across.
    float az = atan(R.x, R.z);
    float strips = pow(max(0.0, cos(az * 2.0 + 1.2)), 24.0) * smoothstep(0.0, 0.30, up);
    col += vec3(0.90, 0.95, 1.00) * strips * 0.30 * (0.25 + 0.75 * WheelSky.a);

    // Rough surfaces average the environment out instead of mirroring it.
    vec3 average = mix(horizon, ground, 0.5);
    col = mix(col, average, clamp(rough * 0.75, 0.0, 0.85));
    return col;
}

// ---------------------------------------------------------------------------------------------
// Cook-Torrance
// ---------------------------------------------------------------------------------------------

float distributionGGX(float NoH, float a) {
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d + 1.0e-6);
}

float visibilitySmithGGX(float NoV, float NoL, float a) {
    float a2 = a * a;
    float gv = NoL * sqrt(NoV * NoV * (1.0 - a2) + a2);
    float gl = NoV * sqrt(NoL * NoL * (1.0 - a2) + a2);
    return 0.5 / max(gv + gl, 1.0e-5);
}

vec3 fresnelSchlick(vec3 f0, float u) {
    float f = pow(1.0 - u, 5.0);
    return f0 + (vec3(1.0) - f0) * f;
}

/** Narkowicz's ACES fit. The raw environment is far brighter than 1.0 at the sun disc. */
vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 N = normalize(worldNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(WheelCamera - worldPos);
    float NoV = max(dot(N, V), 1.0e-3);
    vec3 R = reflect(-V, N);

    float rough = clamp(WheelParams.x, 0.06, 1.0);
    float a = rough * rough;

    // A metal has no diffuse: its base colour is its reflectance, and it tints the reflection.
    vec3 albedo = texture(Sampler0, texCoord).rgb * vColor.rgb;
    vec3 f0 = albedo;

    vec3 color = environment(R, rough) * fresnelSchlick(f0, NoV);

    // Direct light: GGX highlight from the sun or moon.
    vec3 L = WheelSun;
    vec3 H = normalize(L + V);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    vec3 lightCol = mix(vec3(1.00, 0.95, 0.86), vec3(0.62, 0.72, 1.00), WheelParams.y);
    float sun = mix(0.12, 1.0, WheelSky.a);
    vec3 specular = distributionGGX(NoH, a) * visibilitySmithGGX(NoV, NoL, a)
                  * fresnelSchlick(f0, VoH) * NoL * lightCol * 6.0 * sun;
    color += specular;

    // Torchlight: the engine has already composited every nearby source into one brightness, and
    // a metal in a lit room is not black, so it lands as a warm ambient on the reflectance.
    color += albedo * vec3(1.00, 0.62, 0.30) * WheelParams.z * 0.55;
    color += f0 * 0.035;

    color = aces(color * WheelParams.w);
    fragColor = vec4(color, 1.0);
}
