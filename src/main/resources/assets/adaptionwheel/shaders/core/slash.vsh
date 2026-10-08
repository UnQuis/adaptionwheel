#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Attribute locations must be declared explicitly. Vanilla core shaders do this (entity.vsh uses
// 0 Position, 1 Color, 2 UV0, 3 UV1, 4 UV2, 5 Normal) because the locations come from the pipeline's
// vertex binding, not from the order the members happen to sit in the format. This pipeline binds
// DefaultVertexFormat.POSITION_TEX_COLOR, whose order is position, uv, colour.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

// SPIR-V requires an explicit location on every user input and output. Bare `out vec2 texCoord;`
// compiles as GLSL but fails the SPIR-V validation these pipelines actually go through, with
// 'location' : SPIR-V requires location for user input/output.
layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
}