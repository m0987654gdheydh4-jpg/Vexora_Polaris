#version 410 core

layout(std140) uniform Uniforms {
    mat4 uMVP;
    vec4 uParams;      // noise, reflect, blur, time
    vec4 uColor;       // rgb + baseAlpha
    vec4 uResolution;  // w, h, distortStrength, rimBoost
};

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec4 inColor;

out vec4 vColor;
out vec3 vLocalPos;
out float vDistortion;

void main() {
    // inColor.a encodes per-vertex distortion weight (tube=1, disc hole>1)
    vLocalPos = inPosition;
    vDistortion = inColor.a;
    vColor = vec4(inColor.rgb * uColor.rgb, uColor.a);
    gl_Position = uMVP * vec4(inPosition, 1.0);
}
