#version 150

uniform sampler2D InSampler;
uniform float Intensity;
uniform float time;
uniform vec2 resolution;
uniform float SkyPitch;
uniform float SkyYaw;
uniform float TanHalfFov;
uniform float Aspect;

in vec2 texCoord;

out vec4 fragColor;

const float TAU = 6.28318530718;
const int MAX_ITER = 5;

vec3 getSkyDirection(vec3 viewDirection) {
    float pitchCos = cos(SkyPitch);
    float pitchSin = sin(SkyPitch);
    vec3 pitchedDirection = normalize(vec3(
            viewDirection.x,
            viewDirection.y * pitchCos + viewDirection.z * pitchSin,
            viewDirection.z * pitchCos - viewDirection.y * pitchSin
    ));
    float yawCos = cos(SkyYaw);
    float yawSin = sin(SkyYaw);
    return normalize(vec3(
            pitchedDirection.x * yawCos + pitchedDirection.z * yawSin,
            pitchedDirection.y,
            pitchedDirection.z * yawCos - pitchedDirection.x * yawSin
    ));
}

float fullSkyAmount(float skyHeight, float amount) {
    float lowerSky = 1.0 - smoothstep(-0.18, 0.18, skyHeight);
    float enabled = smoothstep(0.02, 0.18, amount);
    return clamp(mix(amount, max(amount, 0.96), lowerSky * enabled), 0.0, 1.0);
}

vec3 aquaColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.36 + skyHeight, 0.32);
    vec2 p = mod((uv * 1.35 + vec2(time * 0.018, -time * 0.012)) * TAU, TAU) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.005;
    float localTime = time * 0.45 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = localTime * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
    }

    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);
    vec3 color = vec3(pow(abs(c), 8.0));
    color = clamp(color + vec3(0.0, 0.35, 0.50), 0.0, 1.0);

    float zenith = 1.0 - smoothstep(0.86, 1.0, skyHeight);
    vec3 base = mix(vec3(0.02, 0.06, 0.10), vec3(0.04, 0.18, 0.26), smoothstep(0.0, 0.72, skyHeight));
    return mix(base, color, zenith);
}

void main() {
    vec4 source = texture(InSampler, texCoord);
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 viewDirection = normalize(vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0));
    vec3 skyDirection = getSkyDirection(viewDirection);
    vec3 shaderColor = aquaColor(skyDirection);
    float amount = clamp(max(Intensity, 0.0) * 0.84, 0.0, 1.0);

    fragColor = vec4(mix(source.rgb, shaderColor, fullSkyAmount(skyDirection.y, amount)), source.a);
}
