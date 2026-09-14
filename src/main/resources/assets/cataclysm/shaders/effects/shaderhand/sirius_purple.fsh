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

// Converted from Sirius chams shader: purple.frag
#define TAU 6.28318530718
#define MAX_ITER 5
void main() {
    vec4 centerCol = texture2D(texture, handUv);

    if (centerCol.a <= 0.001) {
        discard;
    }
    float letime = time * .5+23.0;
    // uv should be the 0-1 uv of texture...
    vec2 uv = handFragCoord4().xy / resolution.xy;

#ifdef SHOW_TILING
    vec2 p = mod(uv*TAU*2.0, TAU)-250.0;
#else
    vec2 p = mod(uv*TAU, TAU)-250.0;
#endif
    vec2 i = vec2(p);
    float c = 1.0;
    float inten = .005;

    for (int n = 0; n < MAX_ITER; n++)
    {
        float t = letime * (1.0 - (3.5 / float(n+1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0/length(vec2(p.x / (sin(i.x+t)/inten),p.y / (cos(i.y+t)/inten)));
    }
    c /= float(MAX_ITER);
    c = 1.17-pow(c, 1.4);
    vec3 colour = vec3(pow(abs(c), 8.0));
    colour = clamp(colour + vec3(0.727,0.221,0.765), 0.0, 1.0);


    #ifdef SHOW_TILING
    // Flash tile borders...
    vec2 pixel = 100000000.0 / iResolution.xy;
    uv *= 2.0;

    float f = floor(mod(iTime*.5, 2.0));    // Flash value.
    vec2 first = step(pixel, uv) * f;           // Rule out first screen pixels and flash.
    uv  = step(fract(uv), pixel);               // Add one line of pixels per tile.
    colour = mix(colour, vec3(0.714,0.438,0.765), (uv.x + uv.y) * first.x * first.y); // Yellow line

    #endif
    outColor = vec4(colour, centerCol.a);
    outColor.a *= effectAlpha;
}
