#version 150

// Positions and normals arrive already in WORLD space: the entity renderer bakes the pose stack
// into them on the CPU, and the camera position is added back on top, so this stage does not
// inherit whatever model-view matrix happens to be bound.
//
// The camera has to be subtracted again before projecting, and forgetting it is silent. In 1.21.1
// the camera's view matrix carries the ROTATION only -- `GameRenderer` builds
// `new Matrix4f().rotation(camera.rotation().conjugate())` and `LevelRenderer` multiplies exactly
// that onto the model-view stack, with the translation living in the per-entity pose stack instead.
// So `WheelProj * vec4(worldPos, 1.0)` leaves the camera offset un-subtracted and places the wheel
// `|cameraPos|` units away in view space: off screen, thousands of blocks out, with no GL error, no
// failed link and no missing uniform. `worldPos` stays absolute for the fragment stage, because
// that is what the reflection is evaluated around.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 WheelProj;
uniform vec3 WheelCamera;

out vec2 texCoord;
out vec3 worldPos;
out vec3 worldNormal;
out vec4 vColor;

void main() {
    texCoord = UV0;
    worldPos = Position;
    worldNormal = Normal;
    vColor = Color;
    gl_Position = WheelProj * vec4(Position - WheelCamera, 1.0);
}
