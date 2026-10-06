#version 150

// Positions arrive already in world space: the entity renderer bakes the pose stack into them
// on the CPU, exactly as the sprite path did, so this pass does not inherit whatever model-view
// matrix happens to be bound. SlashProj therefore carries only the view-projection.
//
// The output is named `texCoord` because that is what slash.fsh reads. The varying name is part
// of the link contract: a mismatch here is not a compile error in either stage on its own, and
// the program fails to link at runtime with nothing but a blank quad to show for it.

in vec3 Position;
in vec2 UV0;

uniform mat4 SlashProj;

out vec2 texCoord;

void main() {
    texCoord = UV0;
    gl_Position = SlashProj * vec4(Position, 1.0);
}