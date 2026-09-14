#version 150

#moj_import <cataclysm:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

uniform sampler2D Sampler0;

layout(std140) uniform GlassParamsArray {
    vec4 params[2560];
};

layout(std140) uniform BlurRegion {
    vec4 Region;
};

out vec4 OutColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r, float smoothness) {
    r = min(r, vec4(b.x, b.y, b.x, b.y));
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x = (p.y > 0.0) ? r.x : r.y;
    vec2 q = abs(p) - b + r.x;
    vec2 q_clamped = max(q, 0.0);
    float len = pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0 / smoothness);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    int base = QuadIndex * 5;
    vec4 radius = max(params[base], vec4(0.0));
    vec4 sizeSmoothCorner = params[base + 1];
    vec4 alphaPowerMix = params[base + 2];
    vec4 fresnelColor = params[base + 3];
    vec4 flagsDistortZ = params[base + 4];

    vec2 size = max(sizeSmoothCorner.xy, vec2(1.0));
    float smoothness = sizeSmoothCorner.z;
    float cornerSmoothness = max(sizeSmoothCorner.w, 0.001);
    float globalAlpha = clamp(alphaPowerMix.x, 0.0, 1.0);
    float fresnelPower = max(alphaPowerMix.y, 0.001);
    float baseAlpha = clamp(alphaPowerMix.z, 0.0, 1.0);
    float fresnelMix = clamp(alphaPowerMix.w, 0.0, 1.0);
    float fresnelInvert = flagsDistortZ.x;
    float distortStrength = flagsDistortZ.y;

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 pos = center - coord * size;

    float distance = roundedBoxSDF(-pos, halfSize, radius, cornerSmoothness);
    float alpha = 1.0 - smoothstep(1.0 - smoothness, 1.0, distance);

    float distToEdge = abs(roundedBoxSDF(pos, halfSize, radius, cornerSmoothness));
    float maxDistNorm = max(min(halfSize.x, halfSize.y), 0.001);
    float edgeGradient = 1.0 - clamp(distToEdge / maxDistNorm, 0.0, 1.0);
    float fresnelBase = (fresnelInvert > 0.5) ? edgeGradient : (1.0 - edgeGradient);

    float fresnel;
    if (fresnelPower > 20.0) {
        fresnel = exp(fresnelPower * log(clamp(fresnelBase, 0.001, 1.0)));
    } else {
        fresnel = pow(clamp(fresnelBase, 0.0, 1.0), fresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = normalize(pos);
    vec2 texCoord = clamp((gl_FragCoord.xy - Region.xy) / max(Region.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec4 texColor = texture(Sampler0, texCoord + dir * fresnel * distortStrength);

    vec3 finalColor = mix(texColor.rgb, fresnelColor.rgb, fresnel * fresnelMix);
    float finalAlpha = mix(baseAlpha, fresnelColor.a, fresnel) * alpha * globalAlpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha);
}
