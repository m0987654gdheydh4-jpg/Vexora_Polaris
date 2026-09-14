#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D Sampler0; // scene, this frame
uniform sampler2D Sampler1; // accumulated history

layout(std140) uniform MotionBlurData {
    float blend;     // how much of the history survives
    float threshold; // per-pixel change below which history is kept fully
};

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    vec4 history = texture(Sampler1, texCoord);

    // A hard cut (teleport, dimension change, big flash) must not smear across the screen:
    // where the frame changed drastically the history is dropped instead of blended.
    // The knee is deliberately tight and high — a wide ramp cancelled the blur on exactly the
    // moving edges the effect exists to smear.
    float change = abs(luminance(scene.rgb) - luminance(history.rgb));
    float keep = blend * (1.0 - smoothstep(threshold, threshold * 1.35 + 0.0001, change));

    // Alpha is passed through untouched: the pass does not blend, it replaces.
    fragColor = vec4(mix(scene.rgb, history.rgb, keep), scene.a);
}
