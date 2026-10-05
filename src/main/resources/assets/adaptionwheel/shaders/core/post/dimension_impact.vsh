#version 150

in vec3 Position;
in vec2 UV0;

uniform vec2 ImpactSize;

out vec2 texCoord;

void main() {
    // The quad arrives in pixel coordinates. Turning it into clip space here instead of going
    // through ModelViewMat/ProjMat is deliberate: this pass runs from RenderGuiEvent.Pre, where
    // the projection on the GL stack is the GUI's ortho matrix and is none of our business.
    gl_Position = vec4(Position.x / ImpactSize.x * 2.0 - 1.0,
                       1.0 - Position.y / ImpactSize.y * 2.0,
                       0.0,
                       1.0);
    texCoord = UV0;
}