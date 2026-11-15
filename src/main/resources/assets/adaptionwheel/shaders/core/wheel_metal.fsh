#version 150

// Cook-Torrance metal with a reflected environment, written as a Shadertoy-style analytic scene
// rather than as a screen-space pass: the wheel is drawn in the middle of the world pass, where
// the framebuffer it would have to read is the one it is writing, and that read is undefined.
// Everything the metal reflects is therefore evaluated per pixel from the ray direction -- sky
// gradient, sun/moon disc, a bright horizon band, studio strips and a reflected studio grid --
// which is what the metal shaders this is modelled on do too, and it costs nothing to make it
// follow the game's own sky colour and sun angle.
//
// Three of those ideas are lifted from the shaders on metalshaders.com (the `shader.frag` files in
// github.com/Archetapp/MetalShaders), which are 2D fullscreen effects and so cannot be used as a
// surface shader directly -- what transfers is the vocabulary:
//   * `liquid-chrome`'s Fresnel floor, `mix(0.6, 1.0, ...)`. A real metal reflects strongly at
//     every angle; plain Schlick with f0 = albedo leaves the face-on reflection at half strength
//     and the surface reads as painted.
//   * `liquid-chrome`'s reflected GRID. A gradient plus a sun disc is not enough for the eye to
//     read "reflection" -- it needs structure that slides across the surface as the surface turns.
//   * `rust-corrosion`'s multi-octave noise driving colour, roughness and pitting. Corrosion is not
//     a uniform roughness: it is polished plate interrupted by matte pits, and that variation is
//     most of what makes it look corroded rather than sandblasted.

uniform sampler2D Sampler0;

uniform vec3 WheelCamera;   // world-space eye
uniform vec3 WheelCentre;   // world-space centre of the wheel, so noise can be wheel-local
uniform vec3 WheelSun;      // unit vector towards the sun, or towards the moon at night
uniform vec4 WheelSky;      // rgb = the level's sky colour, a = how much sky light there is (0..1)
uniform vec4 WheelParams;   // x = roughness, y = night flag, z = block light (0..1), w = exposure
uniform vec4 WheelGround;   // rgb = what the ground reflects

// The wheel's own light, once the wearer has adapted to darkness. x = amount (0..1).
uniform vec4 WheelGlow;

// The glowing blocks around the wheel: xyz = world position, w = intensity; then rgb = colour,
// w = the emitter's apparent radius in blocks. Eight of them, because a wheel is 1.3 blocks across
// and a torch nine blocks away is smaller in its reflection than the corrosion noise. See
// WheelLights.java for how they are chosen.
uniform vec4 WheelLightPos0;
uniform vec4 WheelLightPos1;
uniform vec4 WheelLightPos2;
uniform vec4 WheelLightPos3;
uniform vec4 WheelLightPos4;
uniform vec4 WheelLightPos5;
uniform vec4 WheelLightPos6;
uniform vec4 WheelLightPos7;

uniform vec4 WheelLightCol0;
uniform vec4 WheelLightCol1;
uniform vec4 WheelLightCol2;
uniform vec4 WheelLightCol3;
uniform vec4 WheelLightCol4;
uniform vec4 WheelLightCol5;
uniform vec4 WheelLightCol6;
uniform vec4 WheelLightCol7;

in vec2 texCoord;
in vec3 worldPos;
in vec3 worldNormal;
in vec4 vColor;

out vec4 fragColor;

const float PI = 3.14159265359;

// ---------------------------------------------------------------------------------------------
// Noise -- value noise, four octaves. Cheap, and it only has to look like corrosion, not like
// Perlin: the pits it carves are a few centimetres across on a 1.3-block wheel.
// ---------------------------------------------------------------------------------------------

float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

// ---------------------------------------------------------------------------------------------
// Environment
// ---------------------------------------------------------------------------------------------

/** A line every 1/n of the way along `v`, `w` wide. Used for the reflected studio grid. */
float gridLine(float v, float n, float w) {
    float f = fract(v * n);
    return smoothstep(0.5 - w, 0.5, abs(f - 0.5));
}

/**
 * The sky, seen along a reflected ray. The horizon band is the part that makes metal read as
 * metal: a smooth gradient alone gives a flat tinted blob, while a hard bright line across it is
 * what the eye recognises as a reflection of something. The grid goes one step further and gives
 * the reflection *structure*, so it slides across the wheel as the wheel turns.
 */
vec3 environment(vec3 R, float rough) {
    float up = R.y;

    // How much of the sky this wheel can actually see. Sky light in this game is not "daylight" --
    // it is "nothing overhead", full in an open field at midnight and zero in a cave -- so it is
    // the right gate for the whole sky half of this function. Without it a wheel in a sealed cave
    // mirrors a bright horizon, which is both wrong and enough to drown the torch reflections
    // below, and those are the only reflection a cave has.
    float skyVis = 0.08 + 0.92 * WheelSky.a;

    vec3 zenith = WheelSky.rgb * 0.62;
    vec3 horizon = mix(WheelSky.rgb * 1.30, vec3(1.00, 0.96, 0.88), 0.32);
    vec3 sky = mix(horizon, zenith, smoothstep(-0.02, 0.72, up)) * skyVis;

    vec3 ground = mix(WheelGround.rgb, WheelGround.rgb * 0.35, smoothstep(0.0, -0.55, up));
    vec3 col = mix(ground, sky, smoothstep(-0.045, 0.055, up));

    // Sun or moon: a hard disc, a tight bloom and a wide halo.
    vec3 lightCol = mix(vec3(1.00, 0.95, 0.86), vec3(0.62, 0.72, 1.00), WheelParams.y);
    float sd = max(dot(R, WheelSun), 0.0);
    col += lightCol * (pow(sd, 3000.0) * 40.0 + pow(sd, 90.0) * 0.55 + pow(sd, 8.0) * 0.08) * skyVis;

    // A soft bright band sitting just above the horizon -- the studio softbox every metal demo
    // reflects, and the reason the wheel's upper half catches a line of light.
    float band = smoothstep(0.015, 0.10, up) * (1.0 - smoothstep(0.10, 0.40, up));
    col += vec3(1.00, 0.98, 0.94) * band * 0.55 * skyVis;

    // Two vertical strips, so the reflection has structure the wheel can sweep across.
    float az = atan(R.x, R.z);
    float strips = pow(max(0.0, cos(az * 2.0 + 1.2)), 24.0) * smoothstep(0.0, 0.30, up);
    col += vec3(0.90, 0.95, 1.00) * strips * 0.30 * skyVis;

    // The reflected studio grid: meridians and parallels of a dim white cage around the wheel.
    // `az` is divided by 2*PI so the pattern closes seamlessly at the atan seam -- with 8 meridians
    // the value at +/-PI is exactly +/-4, whose fractional part is 0 on both sides.
    float cage = gridLine(az * (1.0 / (2.0 * PI)), 8.0, 0.012) * 0.75
               + gridLine(up, 6.0, 0.010) * 0.45;
    col += vec3(0.95, 0.97, 1.00) * cage * 0.42 * skyVis * (1.0 - 0.7 * rough);

    // Rough surfaces average the environment out instead of mirroring it.
    vec3 average = mix(horizon * skyVis, ground, 0.5);
    col = mix(col, average, clamp(rough * 0.75, 0.0, 0.85));
    return col;
}

// ---------------------------------------------------------------------------------------------
// Reflected light sources
// ---------------------------------------------------------------------------------------------

/**
 * One glowing block, seen in the metal.
 *
 * <p>A light source reflected in a polished object is not a highlight: it is a small bright image
 * that sits where the mirror image of the light is and <i>slides across the surface</i> as the
 * surface turns. That motion is the entire cue -- a highlight that does not move with the geometry
 * reads as a paint smear. So this is evaluated along the reflected ray `R` rather than around the
 * half-vector: the disc is where `R` points at the light, its angular size is the light's own size
 * divided by its distance, and roughness scatters it wider and dimmer.
 */
vec3 lightBlob(vec3 R, vec3 P, vec4 pos, vec4 col, float rough) {
    if (pos.w <= 0.0) {
        return vec3(0.0);
    }
    vec3 toLight = pos.xyz - P;
    float dist = length(toLight);
    if (dist < 0.05) {
        return vec3(0.0);
    }
    float angle = acos(clamp(dot(R, toLight / dist), -1.0, 1.0));
    float theta = atan(col.w / dist) * (1.0 + rough * 1.6);
    float core = 1.0 - smoothstep(theta * 0.20, theta * 1.05, angle);
    float halo = (1.0 - smoothstep(theta * 0.60, theta * 3.50, angle)) * 0.22;
    float attenuation = pos.w / (1.0 + 0.11 * dist * dist);
    return col.rgb * (core + halo) * attenuation;
}

vec3 reflectedLights(vec3 R, vec3 P, float rough) {
    return lightBlob(R, P, WheelLightPos0, WheelLightCol0, rough)
         + lightBlob(R, P, WheelLightPos1, WheelLightCol1, rough)
         + lightBlob(R, P, WheelLightPos2, WheelLightCol2, rough)
         + lightBlob(R, P, WheelLightPos3, WheelLightCol3, rough)
         + lightBlob(R, P, WheelLightPos4, WheelLightCol4, rough)
         + lightBlob(R, P, WheelLightPos5, WheelLightCol5, rough)
         + lightBlob(R, P, WheelLightPos6, WheelLightCol6, rough)
         + lightBlob(R, P, WheelLightPos7, WheelLightCol7, rough);
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

    // Corrosion, in wheel-local units so it travels with the wheel instead of swimming past it as
    // the player walks. `pit` is the large-scale field (which patches are eaten), `grit` the fine
    // one (individual pinpricks).
    vec3 local = (worldPos - WheelCentre) * 14.0;
    float pit = fbm(local);
    float grit = fbm(local * 3.7 + vec3(11.0, 5.0, 7.0));

    // The pitting is what makes the surface corroded rather than sandblasted: a few percent of
    // normal wobble, plus a roughness that swings from near-mirror plate to matte scabs.
    vec3 grad = vec3(
        fbm(local + vec3(0.06, 0.0, 0.0)) - fbm(local - vec3(0.06, 0.0, 0.0)),
        fbm(local + vec3(0.0, 0.06, 0.0)) - fbm(local - vec3(0.0, 0.06, 0.0)),
        fbm(local + vec3(0.0, 0.0, 0.06)) - fbm(local - vec3(0.0, 0.0, 0.06)));
    N = normalize(N + grad * 0.55);

    float NoV = max(dot(N, V), 1.0e-3);
    vec3 R = reflect(-V, N);

    float baseRough = clamp(WheelParams.x, 0.06, 1.0);
    float rough = clamp(baseRough + (smoothstep(0.40, 0.78, pit) - 0.15) * 0.55, 0.06, 1.0);
    float a = rough * rough;

    // A metal has no diffuse: its base colour is its reflectance, and it tints the reflection.
    // Pits are also darker, which is the cheapest possible ambient occlusion and the thing that
    // stops the noise from reading as a pattern rather than as damage.
    vec3 albedo = texture(Sampler0, texCoord).rgb * vColor.rgb;
    albedo *= 1.0 - 0.35 * smoothstep(0.55, 0.85, pit) - 0.12 * smoothstep(0.60, 0.90, grit);
    vec3 f0 = albedo;

    // The Fresnel floor from liquid-chrome: a metal is never 4% reflective face-on, and letting
    // the reflection fall to f0 makes gold look like matte paint.
    float fres = pow(1.0 - NoV, 3.0);
    vec3 color = environment(R, rough) * fresnelSchlick(f0, NoV) * mix(1.45, 1.0, fres);

    // Every glowing block within reach, reflected in the same metal. This is what makes the wheel
    // look like it is standing in a room: outdoors the sky and the sun do all the work, and indoors
    // they are both nearly absent, so without this the wheel turns into a dark disc lit by one
    // composited ambient value. Tinted by f0 like the rest of the reflection, because a reflection
    // off gold is gold.
    color += reflectedLights(R, worldPos, rough) * fresnelSchlick(f0, NoV) * mix(1.45, 1.0, fres);

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
    // a metal in a lit room is not black, so it lands as a warm ambient on the reflectance. Kept
    // deliberately dim, because it is now only the *indirect* half of the light -- the sources
    // themselves are reflected individually above. At the old 0.55 a torch's reflection measured
    // 1.4x the surrounding metal, i.e. a wash; at 0.20 it measures 2.5x, which reads as an image.
    color += albedo * vec3(1.00, 0.62, 0.30) * WheelParams.z * 0.20;
    color += f0 * 0.035;

    // The wheel's own light, once the wearer has adapted to darkness. Emissive, not reflective:
    // it does not depend on anything around it, which is the point. The lightmap lift makes the
    // world readable, and this makes the wheel itself a source in that world -- the metal glows
    // gold along its edges and its corrosion reads as hot rather than merely visible. Brightest
    // where the surface turns away from the eye, because an emissive rim is what an edge looks
    // like when the light comes from inside it.
    if (WheelGlow.x > 0.0) {
        vec3 emissive = albedo * vec3(1.00, 0.86, 0.52);
        color += emissive * WheelGlow.x * (0.80 + 1.10 * pow(1.0 - NoV, 2.0));
    }

    color = aces(color * WheelParams.w);
    fragColor = vec4(color, 1.0);
}
