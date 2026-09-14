#version 150

uniform sampler2D InSampler;
uniform vec4 FirstColor;
uniform vec4 SecondColor;
uniform float Intensity;
uniform float SkyPitch;
uniform float TanHalfFov;
uniform float Aspect;

in vec2 texCoord;

out vec4 fragColor;

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 viewDirection = normalize(vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0));
    float skyHeight = viewDirection.y * cos(SkyPitch) + viewDirection.z * sin(SkyPitch);
    float gradient = smoothstep(-0.22, 0.86, skyHeight);
    vec3 gradientColor = mix(FirstColor.rgb, SecondColor.rgb, gradient);
    float colorAlpha = clamp(max(FirstColor.a, SecondColor.a), 0.0, 1.0);
    float amount = clamp(max(Intensity, 0.0) * colorAlpha, 0.0, 1.0);

    fragColor = vec4(mix(source.rgb, gradientColor, fullSkyAmount(skyHeight, amount)), source.a);
}
