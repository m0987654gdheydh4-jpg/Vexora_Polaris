#version 330

// Fullscreen triangle pair. Zenith's smoke.vsh took a real quad and derived handUv from the
// vertex position; the 1.21.11 pipeline draws from an empty vertex format, so the positions
// live here and handUv keeps the same (Position.xy + 1) / 2 mapping.

out vec2 handUv;

void main() {
    vec2 positions[6] = vec2[](
        vec2(-1.0, -1.0),
        vec2(1.0, -1.0),
        vec2(1.0, 1.0),
        vec2(-1.0, -1.0),
        vec2(1.0, 1.0),
        vec2(-1.0, 1.0)
    );

    vec2 position = positions[gl_VertexID];
    gl_Position = vec4(position, 0.0, 1.0);
    handUv = (position + 1.0) / 2.0;
}
