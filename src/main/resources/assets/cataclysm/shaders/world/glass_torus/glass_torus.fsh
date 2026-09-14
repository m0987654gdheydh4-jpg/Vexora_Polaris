#version 410 core

// Rockstar Glass.glsl port — light, watery screen-space glass on torus mesh.
uniform sampler2D SceneSampler;

layout(std140) uniform Uniforms {
    mat4 uMVP;
    vec4 uParams;      // noiseValue, reflect, blurAmount, unused
    vec4 uColor;       // rgba
    vec4 uResolution;  // w, h, unused, unused
};

in vec4 vColor;
in vec3 vLocalPos;
in float vDistortion;

out vec4 fragColor;

#define TAU 6.28318530718

vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v){
    const vec2  C = vec2(1.0/6.0, 1.0/3.0);
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);
    vec3 i  = floor(v + dot(v, C.yyy));
    vec3 x0 =   v - i + dot(i, C.xxx);
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);
    vec3 x1 = x0 - i1 + 1.0 * C.xxx;
    vec3 x2 = x0 - i2 + 2.0 * C.xxx;
    vec3 x3 = x0 - 1. + 3.0 * C.xxx;
    i = mod(i, 289.0);
    vec4 p = permute(permute(permute(
        i.z + vec4(0.0, i1.z, i2.z, 1.0))
        + i.y + vec4(0.0, i1.y, i2.y, 1.0))
        + i.x + vec4(0.0, i1.x, i2.x, 1.0));
    float n_ = 1.0/7.0;
    vec3  ns = n_ * D.wyz - D.xzx;
    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);
    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);
    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);
    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));
    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
    p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;
    vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}

// Rockstar Glass blur — lighter (8 dir * 3 steps) for FPS
vec4 blur(vec2 uv, float blurAmount, vec2 resolution) {
    vec4 pixelColor = texture(SceneSampler, uv);
    float b = max(blurAmount, 0.0);
    if (b < 0.01) {
        return pixelColor;
    }
    vec2 radius = vec2(b) / max(resolution, vec2(1.0));
    float blurQuality = 3.0;
    float blurDirections = 8.0;
    for (float d = 0.0; d < TAU; d += TAU / blurDirections) {
        for (float i = 1.0 / blurQuality; i <= 1.0; i += 1.0 / blurQuality) {
            pixelColor += texture(SceneSampler, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }
    pixelColor /= (blurQuality * blurDirections + 1.0);
    return pixelColor;
}

void main() {
    // rockstar uniforms: noiseValue = noise*0.01, reflect, blurAmount
    float noiseValue = uParams.x;
    float reflectAmt = max(uParams.y, 1.0);
    float blurAmount = max(uParams.z, 0.0);
    vec2 resolution = max(uResolution.xy, vec2(1.0));

    vec2 uv = gl_FragCoord.xy / resolution;
    // rockstar: reflectedUV
    vec2 reflectedUV = vec2(uv.x, 1.0 - uv.y);

    float n = snoise(vec3(reflectedUV * reflectAmt, 1.0));
    vec2 noisyUV = reflectedUV + vec2(n * noiseValue, n * noiseValue);
    noisyUV = clamp(noisyUV, vec2(0.001), vec2(0.999));

    vec4 blurredColor = blur(noisyUV, blurAmount, resolution);
    // rockstar: fragColor = vec4(blurredColor.rgb, vertexColor.a);
    float a = clamp(vColor.a, 0.15, 0.95);
    fragColor = vec4(blurredColor.rgb, a);
}
