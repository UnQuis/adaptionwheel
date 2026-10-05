#version 150

// Positions arrive already in world space: the entity renderer bakes the pose stack into them
// on the CPU, exactly as the sprite path did, so this pass does not inherit whatever model-view
// matrix happens to be bound. SlashProj therefore carries only the view-projection.

in vec3 Position;
in vec2 UV0;

uniform mat4 SlashProj;

out vec2 vLocal;

void main() {
    vLocal = UV0;
    gl_Position = SlashProj * vec4(Position, 1.0);
}