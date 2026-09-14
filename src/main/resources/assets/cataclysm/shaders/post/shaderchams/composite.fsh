#version 150

// ShaderChams composite. Mask = vanilla entity-outline buffer (through walls):
// R = players, G = mobs, B = other entities, A = silhouette coverage.
// Modes: 0 Glow, 1 Outline, 2 Neon, 3 Rainbow, 4 Ghost, 5 Pulse,
//        6 Glass, 7 Fill, 8 Gradient, 9 Fresnel

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;

layout(std140) uniform ShaderChamsData {
    vec4 playerColor;  // rgb + a
    vec4 mobColor;     // rgb + a
    vec4 otherColor;   // rgb + a
    vec4 params0;      // x=mode, y=time, z=outlineWidthPx, w=glowRadiusPx
    vec4 params1;      // x=fillOpacity, y=glowStrength, z=texelX, w=texelY
    vec4 params2;      // x=rainbowScale, yzw unused
};

const float TAU = 6.28318530718;
const int EDGE_DIRS = 16;
const int GLOW_DIRS = 12;
const int GLOW_RINGS = 3;

float Mode() { return params0.x; }
float Time() { return params0.y; }
float OutlineWidth() { return max(params0.z, 1.0); }
float GlowRadius() { return max(params0.w, 2.0); }
float FillOpacity() { return params1.x; }
float GlowStrength() { return params1.y; }
vec2 Texel() { return params1.zw; }
float RainbowScale() { return params2.x; }

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// Map the category-encoded mask color to the configured chams color.
vec3 catColor(vec3 enc) {
    float total = enc.r + enc.g + enc.b;
    if (total < 1e-4) {
        return otherColor.rgb;
    }
    return clamp((playerColor.rgb * enc.r + mobColor.rgb * enc.g + otherColor.rgb * enc.b) / total, 0.0, 1.0);
}

void main() {
    vec2 texel = Texel();
    vec4 center = texture(MaskSampler, texCoord);
    float aC = center.a;

    // --- Edge ring: dilate and erode the silhouette by OutlineWidth pixels ---
    // The eroded copy is what makes the glass-like modes possible: `aC - eroded` is a band
    // that hugs the *inside* of the silhouette, so a rim can be lit without the interior.
    float dilated = 0.0;
    float eroded = 1.0;
    vec3 encEdge = vec3(0.0);
    float encEdgeW = 0.0;
    for (int i = 0; i < EDGE_DIRS; i++) {
        float ang = TAU * (float(i) / float(EDGE_DIRS));
        vec2 offset = vec2(cos(ang), sin(ang)) * OutlineWidth() * texel;
        vec4 s = texture(MaskSampler, texCoord + offset);
        dilated = max(dilated, s.a);
        eroded = min(eroded, s.a);
        encEdge += s.rgb * s.a;
        encEdgeW += s.a;
    }

    // --- Glow rings: soft halo out to GlowRadius pixels ---
    float glow = 0.0;
    vec3 encGlow = vec3(0.0);
    float encGlowW = 0.0;
    for (int ring = 1; ring <= GLOW_RINGS; ring++) {
        float radius = GlowRadius() * float(ring) / float(GLOW_RINGS);
        float weight = 1.0 - float(ring - 1) / float(GLOW_RINGS);
        for (int i = 0; i < GLOW_DIRS; i++) {
            float ang = TAU * (float(i) / float(GLOW_DIRS)) + float(ring) * 0.26;
            vec2 offset = vec2(cos(ang), sin(ang)) * radius * texel;
            vec4 s = texture(MaskSampler, texCoord + offset);
            glow += s.a * weight;
            encGlow += s.rgb * s.a;
            encGlowW += s.a;
        }
    }
    glow /= float(GLOW_DIRS) * 2.0; // sum of ring weights = 1 + 2/3 + 1/3 = 2
    glow = glow * glow; // sharpen falloff toward the silhouette

    float outlineBand = clamp(dilated - aC, 0.0, 1.0);
    float innerRim = clamp(aC - eroded, 0.0, 1.0);
    if (aC < 0.003 && outlineBand < 0.003 && glow < 0.003) {
        discard;
    }

    // Pick the encoded category color from the closest available source.
    vec3 enc;
    if (aC > 0.01) {
        enc = center.rgb;
    } else if (encEdgeW > 1e-3) {
        enc = encEdge / encEdgeW;
    } else {
        enc = encGlowW > 1e-3 ? encGlow / encGlowW : vec3(0.0);
    }

    float mode = Mode();
    float t = Time();
    float fill = FillOpacity();
    float gs = GlowStrength();

    vec3 col;
    if (mode > 2.5 && mode < 3.5) {
        // Rainbow: hue scrolls over the screen and time
        float hue = fract(t * 0.25 + (texCoord.x + texCoord.y) * RainbowScale());
        col = hsv2rgb(vec3(hue, 0.85, 1.0));
    } else {
        col = catColor(enc);
    }

    float alpha;
    vec3 outCol = col;

    if (mode < 0.5) {
        // Glow — soft halo + light fill
        alpha = aC * fill + outlineBand * 0.9 + glow * (1.0 - aC) * gs;
    } else if (mode < 1.5) {
        // Outline — crisp edge + optional fill
        alpha = aC * fill + outlineBand;
    } else if (mode < 2.5) {
        // Neon — hot rim with a strong additive-looking halo
        alpha = aC * fill + outlineBand + glow * (1.0 - aC) * gs * 1.4;
        outCol = mix(col, vec3(1.0), outlineBand * 0.35);
    } else if (mode < 3.5) {
        // Rainbow — animated hue fill + rim + halo
        alpha = aC * max(fill, 0.25) + outlineBand * 0.9 + glow * (1.0 - aC) * gs;
    } else if (mode < 4.5) {
        // Ghost — hologram scanlines with flicker
        float pixY = texCoord.y / max(texel.y, 1e-6);
        float scan = 0.55 + 0.45 * sin(pixY * 0.55 - t * 6.0);
        float flicker = 0.92 + 0.08 * sin(t * 23.0 + pixY * 0.07);
        alpha = aC * fill * scan * flicker + outlineBand * 0.85 + glow * (1.0 - aC) * gs * 0.6;
        outCol = mix(col, vec3(1.0), outlineBand * 0.25);
    } else if (mode < 5.5) {
        // Pulse — breathing halo and fill
        float pulse = 0.5 + 0.5 * sin(t * 3.2);
        alpha = aC * fill * (0.6 + 0.4 * pulse)
                + outlineBand * (0.5 + 0.5 * pulse)
                + glow * (1.0 - aC) * gs * pulse;
    } else if (mode < 6.5) {
        // Glass — the body stays see-through and the silhouette is carried by a bright inner
        // rim, the way a tinted pane reads: thin where you look through it, lit at the edge.
        float rim = innerRim * innerRim;
        alpha = aC * fill * 0.40 + rim * 0.85 + outlineBand * 0.55 + glow * (1.0 - aC) * gs * 0.35;
        outCol = mix(col, vec3(1.0), rim * 0.55);
    } else if (mode < 7.5) {
        // Fill — flat opaque silhouette, no halo. The blunt one.
        alpha = aC;
        outCol = col;
    } else if (mode < 8.5) {
        // Gradient — the fill fades out toward the top of the screen and brightens toward the
        // bottom, so tall entities read as a wash rather than a solid slab.
        float ramp = clamp(1.0 - texCoord.y, 0.0, 1.0);
        alpha = aC * max(fill, 0.30) * (0.35 + 0.65 * ramp) + outlineBand * 0.8
                + glow * (1.0 - aC) * gs * 0.6;
        outCol = mix(col * 0.55, mix(col, vec3(1.0), 0.25), ramp);
    } else {
        // Fresnel — rim light only. The interior is nearly clear, which keeps the shape
        // readable through walls without hiding what the entity is actually doing.
        float rim = innerRim;
        alpha = aC * fill * 0.12 + rim + outlineBand * 0.9 + glow * (1.0 - aC) * gs * 0.5;
        outCol = mix(col, vec3(1.0), rim * 0.35);
    }

    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha < 0.003) {
        discard;
    }
    fragColor = vec4(outCol, alpha);
}
