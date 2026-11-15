#version 150

// Positions and normals arrive already in WORLD space: the entity renderer bakes the pose stack
// into them on the CPU, exactly as the slash pass does, so this stage does not inherit whatever
// model-view matrix happens to be bound. WheelProj therefore carries view-projection only.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 WheelProj;

out vec2 texCoord;
out vec3 worldPos;
out vec3 worldNormal;
out vec4 vColor;

void main() {
    texCoord = UV0;
    worldPos = Position;
    worldNormal = Normal;
    vColor = Color;
    gl_Position = WheelProj * vec4(Position, 1.0);
}
