#version 330
#extension GL_ARB_separate_shader_objects : require

// A fullscreen triangle built from gl_VertexIndex, so no vertex buffer has to be bound at all.
// Byte-for-byte the same shape as vanilla's own core/screenquad.vsh, vendored rather than
// referenced so this mod does not depend on a vanilla shader path that could be moved or renamed.
layout(location = 0) out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}