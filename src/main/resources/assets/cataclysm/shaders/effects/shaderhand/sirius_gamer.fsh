#version 330

// Zenith ShaderHand prelude, ported to the 1.21.11 render-pipeline shader format.
//
// Zenith renders the hands into a dedicated framebuffer cleared to transparent black, then
// runs this shader with that framebuffer's colour+depth bound. Vanilla 1.21.11 clears the
// main depth texture right before renderItemInHand, so after the hand features are flushed
// the depth attachment is 1.0 everywhere except on hand pixels — the same exact silhouette
// Zenith gets from its own framebuffer, with no colour-delta guessing. ColorTexture is a
// copy of the scene, DepthTexture a copy of that depth, and handTexture() rebuilds the
// "hand FBO" alpha channel the original shader bodies expect.

in vec2 handUv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;

layout(std140) uniform ShaderHandData {
    vec2 resolution;
    vec2 handMotion;
    vec2 speed;
    vec2 iMouse;
    float time;
    float shift;
    float effectAlpha;
    float shaderHandPadding;
};

#define surfacePosition ((handUv - handMotion) * 2.0)

float handDepthMask(vec2 sampleUv) {
    if (sampleUv.x < 0.0 || sampleUv.y < 0.0 || sampleUv.x > 1.0 || sampleUv.y > 1.0) {
        return 0.0;
    }

    float depthValue = texture(DepthTexture, sampleUv).r;
    return smoothstep(0.999, 0.990, depthValue);
}

float sampleMask(vec2 sampleUv) {
    if (sampleUv.x < 0.0 || sampleUv.y < 0.0 || sampleUv.x > 1.0 || sampleUv.y > 1.0) {
        return 0.0;
    }

    return handDepthMask(sampleUv);
}

vec4 handTexture(vec2 sampleUv) {
    vec4 sampledColor = texture(ColorTexture, sampleUv);
    sampledColor.a = sampleMask(sampleUv);
    return sampledColor;
}

#define texture2D(sourceTexture, sampleUv) handTexture(sampleUv)

vec2 handEffectCoord(vec2 sampleUv) {
    return (sampleUv - handMotion + 0.5) * resolution.xy;
}

vec4 handFragCoord4() {
    return vec4(handEffectCoord(handUv), 0.0, 1.0);
}

// Converted from Sirius chams shader: gamer.frag
#define NUM_OCTAVES 16
mat3 rotX(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
    1, 0, 0,
    0, c, -s,
    0, s, c
    );
}
mat3 rotY(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
    c, 0, -s,
    0, 1, 0,
    s, 0, c
    );
}

float random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = random(i + vec2(0.0, 0.0));
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 pos) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i=0; i<NUM_OCTAVES; i++) {
        v += a * noise(pos);
        pos = rot * pos * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

void main(void) {
    vec4 centerCol = texture2D(texture, handUv);

    if (centerCol.a <= 0.001) {
        discard;
    }
    vec2 p = (handFragCoord4().xy * 2.0 - resolution.xy) / min(resolution.x, resolution.y);

    float t = 0.0, d;
    float alpha = 0;

    float time2 = 3.0 * time / 2.0;

    vec2 q = vec2(0.0);
    q.x = fbm(p + 0.00 * time2);
    q.y = fbm(p + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = fbm(p + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time2);
    r.y = fbm(p + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time2);
    float f = fbm(p + r);
    vec3 color = mix(
    vec3(0.101961, 0.866667, 0.319608),
    vec3(.666667, 0.598039, 0.366667),
    clamp((f * f) * 4.0, 0.0, 1.0)
    );

    color = mix(
    color,
    vec3(0.34509803921, 0.06666666666, 0.83137254902),
    clamp(length(q), 0.0, 1.0)
    );


    color = mix(
    color,
    vec3(0.1, -0.5, 0.1),
    clamp(length(r.x), 0.0, 1.0)
    );

    color = (f *f * f + 0.6 * f * f + 0.5 * f) * color;

    alpha = 1.0;

    outColor = vec4(color, alpha);
    outColor.a *= effectAlpha;
}
