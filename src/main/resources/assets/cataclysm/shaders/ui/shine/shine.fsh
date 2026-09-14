#version 150

#moj_import <cataclysm:common.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform ShineParamsArray {
    vec4 params[2048]; // 512 shines * 4 vec4s
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 4;

    vec4 radius = params[base];
    vec4 sizeSmooth = params[base + 1]; // x=width, y=height, z=smoothness, w=progress
    vec4 dirBand = params[base + 2];    // x=dirX, y=dirY, z=bandWidth, w=intensity
    vec4 color = params[base + 3];

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmooth.xy, vec2(1.0));

    // Rounded-rect mask so the streak respects the panel corners.
    float alpha = ralpha(size, coord, max(radius, vec4(0.0)), sizeSmooth.z);

    // Sweep direction (defaults to bottom-right -> top-left).
    vec2 dir = dirBand.xy;
    float len = length(dir);
    if (len < 0.0001) {
        dir = vec2(-1.0, -1.0);
        len = length(dir);
    }
    vec2 dn = dir / len;

    // Normalise the projection of coord onto the sweep axis into [0, 1].
    float sMin = min(0.0, dn.x) + min(0.0, dn.y);
    float sMax = max(0.0, dn.x) + max(0.0, dn.y);
    float denom = max(sMax - sMin, 0.0001);
    float t = (dot(coord, dn) - sMin) / denom;

    float band = max(dirBand.z, 0.01);
    float center = mix(-band, 1.0 + band, sizeSmooth.w);
    float d = abs(t - center);
    float streak = 1.0 - smoothstep(0.0, band, d);
    streak = pow(clamp(streak, 0.0, 1.0), 1.6);

    vec4 finalColor = color;
    finalColor.a *= alpha * streak * dirBand.w;

    if (finalColor.a <= 0.001) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
