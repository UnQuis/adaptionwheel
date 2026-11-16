#version 330
#extension GL_ARB_separate_shader_objects : require

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler1;
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif
#ifndef EMISSIVE
layout(location = 4) out vec4 lightMapColor;
#endif
#ifndef NO_OVERLAY
layout(location = 5) out vec4 overlayColor;
#endif
layout(location = 6) out vec2 texCoord0;
layout(location = 7) out vec3 worldNormal;
layout(location = 8) out vec3 relPos;
layout(location = 9) out vec3 worldPos;
layout(location = 10) out float skyLight;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

#ifdef PER_FACE_LIGHTING
    vec3 transformedNormal = normalize(mat3(ModelViewMat) * Normal);
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, transformedNormal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, Color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, Color);
#elif defined(NO_CARDINAL_LIGHTING)
    // Preserve authored OBJ material tint (gold/bronze) instead of baking cardinal lighting in.
    vertexColor = Color;
#else
    vec3 transformedNormal = normalize(mat3(ModelViewMat) * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, transformedNormal, Color);
#endif

#if !defined(EMISSIVE) && !defined(OIT_ALPHA_ONLY)
    lightMapColor = sample_lightmap(Sampler2, UV2);
#endif

#if !defined(NO_OVERLAY) && !defined(OIT_ALPHA_ONLY)
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;

    // The CPU bakes the entity pose into Position and transforms Normal by the same pose
    // (WheelRenderer.drawMesh), so Normal is already a camera-relative WORLD direction.
    // Decimals: keep it that way; multiplying by ModelViewMat here would rotate it a second
    // time and the metal would be lit for a wheel pointing somewhere else.
    worldNormal = normalize(Normal);
    relPos = Position;

    // Globals carries the camera as an integer block plus a negative fractional offset, so the
    // world position is reconstructed exactly enough for the corrosion noise to stay put.
    worldPos = Position + vec3(CameraBlockPos) - CameraOffset;

    // Sky light LEVEL (0..15) is what tells an open sky from a cave; the lightmap colour alone
    // cannot, because a torch is as bright as the sun in it.
    skyLight = clamp(float(UV2.y) / 15.0, 0.0, 1.0);
}
