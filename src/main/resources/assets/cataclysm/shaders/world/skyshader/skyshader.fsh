#version 150

in vec2 vUV;
out vec4 fragColor;

layout(std140) uniform SkyData {
    mat4 invViewProj;   // inverse(projection * modelView)
    vec4 colorA;        // primary color (rgb) + unused
    vec4 colorB;        // secondary color (rgb) + unused
    vec4 params;        // x = time, y = style, z = opacity, w = unused
};

#define PI 3.14159265359
#define TAU 6.28318

// galaxy ("Star Nest" style) parameters
#define NUM_LAYERS 8.0
#define GALAXY_VELOCITY 0.025
#define GALAXY_STAR_GLOW 0.025
#define GALAXY_CANVAS_VIEW 20.0

// ----- hashing / noise helpers -----
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float skyNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    // quintic smootherstep interpolation removes the directional grid artifacts
    // that the cheaper cubic curve leaves behind on value noise
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    // rotate the domain between octaves so the value-noise lattice never lines
    // up across scales; this is what kills the "square clouds" look
    const mat2 rot = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        v += a * skyNoise2(p);
        p = rot * p * 2.02;
        a *= 0.5;
    }
    return v;
}

// reconstruct world-space view ray direction for this pixel
vec3 rayDir() {
    vec2 ndc = vUV * 2.0 - 1.0;
    vec4 p0 = invViewProj * vec4(ndc, -1.0, 1.0);
    vec4 p1 = invViewProj * vec4(ndc, 1.0, 1.0);
    return normalize(p1.xyz / p1.w - p0.xyz / p0.w);
}

// Seamless sky-dome projection (stereographic from the nadir).
// Unlike atan/asin equirectangular mapping it has no singularity at the
// zenith (straight up) and no wrap-around seam, so full-sky fields such as
// the galaxy and digital styles stay continuous overhead. The only singular
// point is straight down, which is always hidden by the terrain.
vec2 domeProject(vec3 dir) {
    float denom = max(dir.y + 1.0, 0.2);
    return dir.xz / denom;
}

// dense procedural star field based on view direction
float starField(vec3 dir, float density, float sharpness) {
    vec3 g = dir * density;
    vec3 id = floor(g);
    vec3 f = fract(g) - 0.5;
    float star = 0.0;
    float r = hash31(id);
    if (r > 0.965) {
        float d = length(f);
        star = smoothstep(sharpness, 0.0, d) * (r - 0.965) * 28.0;
    }
    return star;
}

// ----- ported galaxy star field -----
float galaxyStar(vec2 uv, float flare) {
    float d = length(uv);
    float m = sin(GALAXY_STAR_GLOW * 1.2) / d;
    float rays = max(0.0, 0.5 - abs(uv.x * uv.y * 1000.0));
    m += (rays * flare) * 2.0;
    m *= smoothstep(1.0, 0.1, d);
    return m;
}

float galaxyHash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 galaxyStarLayer(vec2 uv, float time) {
    vec3 col = vec3(0.0);
    vec2 gv = fract(uv);
    vec2 id = floor(uv);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offs = vec2(float(x), float(y));
            float n = galaxyHash21(id + offs);
            float size = fract(n);
            float star = galaxyStar(gv - offs - vec2(n, fract(n * 34.0)) + 0.5, smoothstep(0.1, 0.9, size) * 0.46);
            vec3 color = sin(vec3(0.2, 0.3, 0.9) * fract(n * 2345.2) * TAU) * 0.25 + 0.75;
            color = color * vec3(0.9, 0.59, 0.9 + size);
            star *= sin(time * 0.6 + n * TAU) * 0.5 + 0.5;
            col += star * size * color;
        }
    }
    return col;
}

vec3 galaxyField(vec2 uv, float time) {
    float t = time * GALAXY_VELOCITY;
    vec2 M = vec2(0.0);
    M -= vec2(M.x + sin(time * 0.22), M.y - cos(time * 0.22));
    vec3 col = vec3(0.0);
    for (float i = 0.0; i < 1.0; i += 1.0 / NUM_LAYERS) {
        float depth = fract(i + t);
        float scale = mix(GALAXY_CANVAS_VIEW, 0.5, depth);
        float fade = depth * smoothstep(1.0, 0.9, depth);
        col += galaxyStarLayer(uv * scale + i * 453.2 - time * 0.05 + M, time) * fade;
    }
    return col;
}

// ----- ported "didjital" voronoi-electric field (by srtuss) -----
vec2 digiRotate(vec2 p, float a) {
    return vec2(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a));
}

float digiRand(float n) {
    return fract(sin(n) * 43758.5453123);
}

vec2 digiRand2(in vec2 p) {
    return fract(vec2(sin(p.x * 591.32 + p.y * 154.077), cos(p.x * 391.32 + p.y * 49.077)));
}

float digiNoise1(float p) {
    float fl = floor(p);
    float fc = fract(p);
    return mix(digiRand(fl), digiRand(fl + 1.0), fc);
}

// voronoi distance noise, based on iq's articles
float digiVoronoi(in vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    vec2 res = vec2(8.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(float(i), float(j));
            vec2 r = b - f + digiRand2(p + b);
            float d = max(abs(r.x), abs(r.y)); // chebyshev distance
            if (d < res.x) {
                res.y = res.x;
                res.x = d;
            } else if (d < res.y) {
                res.y = d;
            }
        }
    }
    return res.y - res.x;
}

// ----- styles -----

vec3 styleAurora(vec3 dir, float t) {
    float up = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(colorB.rgb * 0.15, colorA.rgb * 0.08, up);
    base = mix(base, vec3(0.01, 0.02, 0.06), pow(up, 1.5));

    // stars in the upper sky
    float stars = starField(dir, 220.0, 0.06) * smoothstep(0.0, 0.35, dir.y);
    base += stars * vec3(0.8, 0.9, 1.0);

    // aurora curtains projected on the sky dome
    vec2 sky = dir.xz / max(dir.y * 0.6 + 0.4, 0.05);
    float curtain = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wave = fbm(vec2(sky.x * 1.5 + t * 0.15 + fi * 4.0, t * 0.25 + fi));
        float band = sin(sky.x * 2.0 + wave * 6.0 + t * 0.4 + fi * 2.0) * 0.5 + 0.5;
        float h = sky.y - (wave * 1.2 - 0.4 + fi * 0.25);
        float glow = exp(-h * h * 6.0) * band;
        curtain += glow;
    }
    curtain *= smoothstep(-0.05, 0.4, dir.y);
    vec3 auroraCol = mix(colorA.rgb, colorB.rgb, 0.5 + 0.5 * sin(t * 0.3 + sky.x));
    base += auroraCol * curtain * 0.9;
    return base;
}

vec3 styleGalaxy(vec3 dir, float t) {
    // seamless dome mapping: no zenith pole, no wrap seam, pans as you look around
    vec2 uv = domeProject(dir) * 1.6;

    vec3 stars = galaxyField(uv, t);

    // faint nebula background driven by the user colors
    float up = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 bg = mix(colorB.rgb, colorA.rgb, up) * 0.07;

    // tint the star field with the chosen colors while keeping its structure
    vec3 tint = mix(colorA.rgb, colorB.rgb, 0.5);
    stars *= 0.65 + 0.7 * tint;

    return bg + stars;
}

vec3 styleSunset(vec3 dir, float t) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = colorB.rgb;
    vec3 zenith = colorA.rgb;
    vec3 base = mix(horizon, zenith, pow(h, 0.7));

    // moving sun glow near the horizon
    vec3 sunDir = normalize(vec3(sin(t * 0.05) * 0.6, 0.05, -1.0));
    float sun = max(dot(dir, sunDir), 0.0);
    base += colorB.rgb * pow(sun, 8.0) * 1.2;
    base += vec3(1.0, 0.85, 0.6) * pow(sun, 220.0) * 3.0;

    // soft drifting clouds (seamless dome projection, no zenith convergence)
    vec2 sky = domeProject(dir) * 3.0;
    float cloud = fbm(sky * 1.8 + vec2(t * 0.06, t * 0.01));
    cloud = smoothstep(0.55, 0.95, cloud) * smoothstep(0.0, 0.25, dir.y);
    base = mix(base, mix(vec3(1.0, 0.95, 0.9), colorB.rgb, 0.4), cloud * 0.6);
    return base;
}

vec3 styleDaylight(vec3 dir, float t) {
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 zenith = colorA.rgb;
    vec3 horizon = colorB.rgb;
    vec3 base = mix(horizon, zenith, pow(clamp(dir.y, 0.0, 1.0), 0.45));

    // sun
    vec3 sunDir = normalize(vec3(0.3, 0.5, -1.0));
    float sun = max(dot(dir, sunDir), 0.0);
    base += vec3(1.0, 0.98, 0.9) * pow(sun, 900.0) * 4.0;
    base += vec3(1.0, 0.9, 0.75) * pow(sun, 15.0) * 0.25;

    // puffy animated clouds (seamless dome projection, no zenith convergence)
    vec2 sky = domeProject(dir) * 3.0;
    float cloud = fbm(sky * 1.5 + vec2(t * 0.05, 0.0));
    cloud += fbm(sky * 4.0 - vec2(t * 0.03, 0.0)) * 0.4;
    cloud = smoothstep(0.6, 1.0, cloud) * smoothstep(0.0, 0.2, dir.y);
    base = mix(base, vec3(1.0), cloud * 0.85);
    return base;
}

vec3 styleDigital(vec3 dir, float t) {
    float flicker = digiNoise1(t * 2.0) * 0.8 + 0.4;

    // seamless dome mapping: no zenith pole, no wrap seam, pans as you look around
    vec2 uv = domeProject(dir) * 2.0;
    vec2 suv = uv * 0.5;

    // a bit of camera movement
    uv *= 0.6 + sin(t * 0.1) * 0.4;
    uv = digiRotate(uv, sin(t * 0.3) * 1.0);
    uv += t * 0.4;

    float v = 0.0;
    float a = 0.6, f = 1.0;
    for (int i = 0; i < 3; i++) {
        float v1 = digiVoronoi(uv * f + 5.0);
        float v2 = 0.0;
        if (i > 0) {
            // moving "electrons" effect for higher octaves
            v2 = digiVoronoi(uv * f * 0.5 + 50.0 + t);
            float va = 1.0 - smoothstep(0.0, 0.1, v1);
            float vb = 1.0 - smoothstep(0.0, 0.08, v2);
            v += a * pow(va * (0.5 + vb), 2.0);
        }
        v1 = 1.0 - smoothstep(0.0, 0.3, v1); // sharp edges
        v2 = a * digiNoise1(v1 * 5.5 + 0.1); // noise as intensity map
        if (i == 0) {
            v += v2 * flicker;
        } else {
            v += v2;
        }
        f *= 3.0;
        a *= 0.7;
    }

    // slight vignetting
    v *= exp(-0.6 * length(suv)) * 1.2;

    // procedural coloring driven by the user colors (replaces the iChannel0 texture)
    vec3 tint = clamp(mix(colorA.rgb, colorB.rgb, 0.5 + 0.5 * sin(t * 0.2)), 0.0, 1.0);
    vec3 cexp = mix(vec3(7.0), vec3(1.8), tint);
    return vec3(pow(v, cexp.x), pow(v, cexp.y), pow(v, cexp.z)) * 2.0;
}

void main() {
    vec3 dir = rayDir();
    float t = params.x;
    int style = int(params.y + 0.5);

    vec3 col;
    if (style == 0) {
        col = styleAurora(dir, t);
    } else if (style == 1) {
        col = styleGalaxy(dir, t);
    } else if (style == 2) {
        col = styleSunset(dir, t);
    } else if (style == 3) {
        col = styleDaylight(dir, t);
    } else {
        col = styleDigital(dir, t);
    }

    // gentle tone mapping
    col = col / (col + vec3(0.6)) * 1.6;
    col = pow(clamp(col, 0.0, 1.0), vec3(0.9));

    fragColor = vec4(col, clamp(params.z, 0.0, 1.0));
}
