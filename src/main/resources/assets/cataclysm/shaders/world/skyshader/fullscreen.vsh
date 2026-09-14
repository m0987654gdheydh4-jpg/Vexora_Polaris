#version 150

in vec3 inPosition;
in vec2 inUV;

out vec2 vUV;

void main() {
    vUV = inUV;
    // inPosition is already in normalized device coordinates (z = 1 -> far plane)
    gl_Position = vec4(inPosition, 1.0);
}
