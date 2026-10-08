#version 150
uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    float alpha = texture(Sampler0, texCoord0).a;
    if (alpha <= 0) discard;
    fragColor = vec4(0.0,0.0,0.0, alpha);
}