#version 150

// Zenith ShaderFog modes: 0 Gradient, 1 Galaxy, 2 Aqua, 3 Purple, 4 Overcast

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform ShaderFogData {
    vec4 firstColor;   // rgb + a
    vec4 secondColor;  // rgb + a
    vec4 purpleColor;  // rgb + a
    vec4 cameraPos;    // xyz + unused
    vec4 params0;      // x=intensity, y=time, z=skyPitch, w=skyYaw
    vec4 params1;      // x=tanHalfFov, y=aspect, z=mode, w=unused
    vec4 params2;      // x=worldFlash, y=strikePeriod, z=lightningPower, w=realTime
};

// HARD sky mask by depth only — never by view angle (that looked like fog on the world).
// Supports standard Z (sky/far ≈ 1) and reverse-Z (sky/far ≈ 0).
float skyMask() {
    float d = texture(DepthSampler, texCoord).r;
    // reverse-Z: cleared sky is ~0, geometry writes > 0
    float revSky = 1.0 - smoothstep(0.00005, 0.0025, d);
    // classic Z: cleared sky is ~1, geometry writes < 1
    float classicSky = smoothstep(0.9985, 0.99995, d);
    // Only one convention should fire; take max of tight thresholds
    return clamp(max(revSky, classicSky), 0.0, 1.0);
}

const int ITERATIONS = 7;
const int VOL_STEPS = 7;
const float FORM_PARAM = 0.50;
const float STEP_SIZE = 0.29;
const float TILE = 0.95;
const float BRIGHTNESS = 0.0045;
const float DIST_FADING = 0.66;
const float SATURATION = 0.82;
const float TAU = 6.28318530718;
const int MAX_ITER = 5;

float Intensity() { return params0.x; }
float time() { return params0.y; }
float SkyPitch() { return params0.z; }
float SkyYaw() { return params0.w; }
float TanHalfFov() { return params1.x; }
float Aspect() { return params1.y; }
float Mode() { return params1.z; }
float WorldFlash() { return params2.x; }
float StrikePeriod() { return max(params2.y, 0.5); }
float LightningPower() { return params2.z; }
// Real seconds, NOT scaled by Time Speed: storms should keep their own pace even when the
// cloud animation is slowed down (and must not stop dead at Time Speed 0).
float RealTime() { return params2.w; }

vec3 getSkyDirection(vec3 viewDirection) {
    float pitchCos = cos(SkyPitch());
    float pitchSin = sin(SkyPitch());
    vec3 pitchedDirection = normalize(vec3(
            viewDirection.x,
            viewDirection.y * pitchCos + viewDirection.z * pitchSin,
            viewDirection.z * pitchCos - viewDirection.y * pitchSin
    ));
    float yawCos = cos(SkyYaw());
    float yawSin = sin(SkyYaw());
    return normalize(vec3(
            pitchedDirection.x * yawCos + pitchedDirection.z * yawSin,
            pitchedDirection.y,
            pitchedDirection.z * yawCos - pitchedDirection.x * yawSin
    ));
}

// On true sky pixels only: intensity is how strongly we replace the sky color.
// Does NOT fade by view angle over terrain (that was the fog look).
float skyReplaceAmount(float amount) {
    return clamp(amount, 0.0, 1.0);
}

float field(vec3 p) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(time()) * 4373.11));
    float accum = 0.0;
    float previous = 0.0;
    float totalWeight = 0.0;
    for (int i = 0; i < 6; i++) {
        float mag = dot(p, p);
        p = abs(p) / max(mag, 0.001) + vec3(-0.5, -0.8 + 0.1 * sin(time() * 0.7 + 2.0), -1.1 + 0.3 * cos(time() * 0.3));
        float weight = exp(-float(i) / 7.0);
        accum += weight * exp(-strength * pow(abs(mag - previous), 2.3));
        totalWeight += weight;
        previous = mag;
    }
    return max(0.0, 5.0 * accum / max(totalWeight, 0.001) - 0.7);
}

vec3 galaxyColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.42 + skyHeight, 0.34);
    uv *= 0.82;
    float time2 = time() * 0.16;
    vec3 dir = normalize(vec3(uv * 0.9, 1.0));
    vec3 from = vec3(0.0);
    vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += 1.2 * cos(0.3 * time2) + 0.018 * time2;
    from.y += 1.2 * sin(0.2 * time2) + 0.018 * time2;
    from.z += 0.04 * time2;
    float angleXZ = 0.9;
    float angleYZ = -0.6;
    float angleXY = 0.9 + time2 * 0.22;
    mat2 rotXZ = mat2(cos(angleXZ), sin(angleXZ), -sin(angleXZ), cos(angleXZ));
    mat2 rotYZ = mat2(cos(angleYZ), sin(angleYZ), -sin(angleYZ), cos(angleYZ));
    mat2 rotXY = mat2(cos(angleXY), sin(angleXY), -sin(angleXY), cos(angleXY));
    dir.xy = rotXY * dir.xy;
    forward.xy = rotXY * forward.xy;
    dir.xz = rotXZ * dir.xz;
    forward.xz = rotXZ * forward.xz;
    dir.yz = rotYZ * dir.yz;
    forward.yz = rotYZ * forward.yz;
    from.xy = -(rotXY * from.xy);
    from.xz = rotXZ * from.xz;
    from.yz = rotYZ * from.yz;
    float zoom = time2 * -0.012;
    from += forward * zoom;
    float sampleShift = mod(zoom, STEP_SIZE);
    float zOffset = -sampleShift;
    sampleShift /= STEP_SIZE;
    float s = 0.24;
    float s3 = s + STEP_SIZE * 0.5;
    vec3 volume = vec3(0.0);
    vec3 cloudColor = vec3(0.0);
    for (int r = 0; r < VOL_STEPS; r++) {
        vec3 p = from + (s + zOffset) * dir;
        vec3 pCloud = from + (s3 + zOffset) * dir;
        p = abs(vec3(TILE) - mod(p, vec3(TILE * 2.0)));
        pCloud = abs(vec3(TILE) - mod(pCloud, vec3(TILE * 2.0)));
        float cloud = field(pCloud);
        float previousAmount = 0.0;
        float amount = 0.0;
        for (int i = 0; i < ITERATIONS; i++) {
            p = abs(p) / max(dot(p, p), 0.001) - FORM_PARAM;
            float diff = abs(length(p) - previousAmount);
            amount += i > 5 ? min(12.0, diff) : diff;
            previousAmount = length(p);
        }
        amount *= amount * amount;
        float depth = s + zOffset;
        float fade = pow(DIST_FADING, max(0.0, float(r) - sampleShift));
        if (r == 0) fade *= 1.0 - sampleShift;
        if (r == VOL_STEPS - 1) fade *= sampleShift;
        volume += vec3(depth, depth * depth, depth * depth * depth * depth) * amount * BRIGHTNESS * fade;
        cloudColor += vec3(1.8 * cloud * cloud * cloud, 1.4 * cloud * cloud, cloud) * fade * 0.31;
        s += STEP_SIZE;
        s3 += STEP_SIZE;
    }
    volume = mix(vec3(length(volume)), volume, SATURATION);
    cloudColor.b *= 1.8;
    cloudColor.r *= 0.06;
    cloudColor.g *= 0.12;
    vec3 color = volume * 0.36 + cloudColor;
    float zenithFade = 1.0 - smoothstep(0.98, 1.0, skyHeight);
    vec3 base = mix(vec3(0.015, 0.018, 0.052), vec3(0.035, 0.055, 0.13), smoothstep(0.0, 0.72, skyHeight));
    return base + color * zenithFade;
}

vec3 aquaColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.36 + skyHeight, 0.32);
    vec2 p = mod((uv * 1.35 + vec2(time() * 0.018, -time() * 0.012)) * TAU, TAU) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.005;
    float localTime = time() * 0.45 + 23.0;
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

vec3 purpleShaderColor(vec3 worldDirection) {
    float skyHeight = abs(worldDirection.y);
    vec2 uv = worldDirection.xz / max(0.36 + skyHeight, 0.32);
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 i = p;
    float c = 1.0;
    float inten = 0.005;
    float localTime = time() * 1.5 + 23.0;
    for (int n = 0; n < MAX_ITER; n++) {
        float t = localTime * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
    }
    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);
    vec3 shaderColor = vec3(pow(abs(c), 8.0));
    shaderColor = clamp(shaderColor + purpleColor.rgb, 0.0, 1.0);
    float zenith = 1.0 - smoothstep(0.86, 1.0, skyHeight);
    vec3 baseColor = mix(purpleColor.rgb * 0.10, purpleColor.rgb * 0.28, smoothstep(0.0, 0.72, skyHeight));
    return mix(baseColor, shaderColor, zenith);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float hash1(float p) {
    return fract(sin(p * 91.3458) * 47453.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = mat2(1.62, 1.10, -1.10, 1.62) * p + vec2(18.7, 7.3);
        amplitude *= 0.52;
    }
    return value;
}

vec3 overcastSky(vec3 worldDirection) {
    float skyHeight = clamp(worldDirection.y * 0.5 + 0.5, 0.0, 1.0);
    vec2 cameraDrift = cameraPos.xz * 0.0009;
    vec2 cloudUv = worldDirection.xz / max(0.28 + abs(worldDirection.y), 0.24);
    cloudUv += cameraDrift + vec2(time() * 0.012, time() * -0.009);
    float broadClouds = fbm(cloudUv * 1.55);
    float tornClouds = fbm(cloudUv * 4.15 + broadClouds * 1.70);
    float stormVeins = fbm(cloudUv * vec2(9.0, 2.1) + vec2(time() * -0.04, time() * 0.01));
    float cloudMass = smoothstep(0.24, 0.88, broadClouds * 0.78 + tornClouds * 0.50);
    float underside = 1.0 - smoothstep(0.48, 0.96, skyHeight);
    float horizonMist = 1.0 - smoothstep(0.02, 0.52, skyHeight);
    vec3 horizon = vec3(0.28, 0.31, 0.33);
    vec3 zenith = vec3(0.055, 0.065, 0.085);
    vec3 base = mix(horizon, zenith, smoothstep(0.0, 1.0, skyHeight));
    vec3 cloudLight = vec3(0.35, 0.37, 0.39);
    vec3 cloudDark = vec3(0.035, 0.040, 0.055);
    vec3 clouds = mix(cloudLight, cloudDark, cloudMass * (0.60 + underside * 0.48));
    float streakShadow = fbm(cloudUv * vec2(0.65, 2.4) + vec2(0.0, time() * 0.035));
    vec3 sky = mix(base, clouds, 0.58 + cloudMass * 0.34);
    sky *= 0.58 + streakShadow * 0.13 - stormVeins * underside * 0.13;
    sky = mix(sky, vec3(0.30, 0.33, 0.35), horizonMist * 0.24);
    return sky;
}

float rainLayer(vec2 uv, float columns, float speed, float slant, float width, float length, float seed) {
    vec2 rainUv = uv;
    rainUv.x += rainUv.y * slant;
    rainUv.x += sin(time() * 0.95 + rainUv.y * 6.0 + seed) * 0.008;
    rainUv.y -= time() * speed;
    vec2 grid = vec2(columns, columns * 0.33);
    vec2 cell = floor(rainUv * grid);
    vec2 local = fract(rainUv * grid);
    float random = hash(cell + seed);
    float x = abs(local.x - random);
    float streak = 1.0 - smoothstep(width, width * 2.6, x);
    float body = smoothstep(0.0, length, local.y) * (1.0 - smoothstep(length, 1.0, local.y));
    float head = exp(-local.y * 9.0) * 0.55;
    return streak * (body + head) * step(0.28, random);
}

// ---------------------------------------------------------------------------------------
// Lightning
//
// Time is diced into fixed slots; a per-slot hash decides whether that slot strikes, when
// inside the slot it fires, and where the bolt lands. Everything is a pure function of
// RealTime(), so there is no state to keep on the CPU and every frame agrees with itself.
// ---------------------------------------------------------------------------------------

// x = seconds since this slot's strike (negative before it fires)
// y = 0..1 horizontal placement, z = 0..1 slot roll (also used as the bolt shape seed)
vec3 strikeState() {
    float period = StrikePeriod();
    float slot = floor(RealTime() / period);
    float within = RealTime() - slot * period;
    float roll = hash1(slot * 4.19 + 9.77);
    float start = 0.10 + hash1(slot * 1.37 + 0.11) * (period * 0.55);
    return vec3(within - start, hash1(slot * 2.71 + 5.23), roll);
}

// One stroke: silent until its delay, then a sharp attack and an exponential tail.
float stroke(float dt, float delay, float decay, float gain) {
    float d = dt - delay;
    return d < 0.0 ? 0.0 : exp(-d * decay) * gain;
}

// Real lightning is not one clean pulse: a hard leader stroke followed by a couple of
// weaker return strokes. Delaying the later strokes is what produces the flicker — adding
// them from t=0 would just make one oversized flash that fades monotonically.
float flashEnvelope(float dt) {
    if (dt < 0.0) {
        return 0.0;
    }
    return min(stroke(dt, 0.00, 7.5, 1.00)
             + stroke(dt, 0.10, 10.0, 0.70)
             + stroke(dt, 0.26, 13.0, 0.40), 1.25);
}

float boltJitter(float y, float seed) {
    return sin(y * 6.0 + seed * 11.0) * 0.060
         + sin(y * 15.0 + seed * 23.0) * 0.026
         + sin(y * 37.0 + seed * 47.0) * 0.010;
}

// uv.x is aspect-corrected screen x, uv.y is 0 at the bottom of the frame.
float boltShape(vec2 uv, float baseX, float seed, float yTop, float yBottom, float thickness) {
    float span = smoothstep(yBottom, yBottom + 0.10, uv.y) * (1.0 - smoothstep(yTop - 0.06, yTop, uv.y));
    if (span <= 0.0) {
        return 0.0;
    }
    float dist = abs(uv.x - (baseX + boltJitter(uv.y, seed)));
    float core = 1.0 - smoothstep(thickness * 0.35, thickness, dist);
    float glow = exp(-dist * 40.0);
    return (core + glow * 0.45) * span;
}

void main() {
    vec4 source = texture(InSampler, texCoord);

    float sky = skyMask();
    float modeId = Mode();
    bool overcast = modeId >= 3.5;

    // World / entities / blocks: never touch (not fog).
    if (sky < 0.5) {
        // The one exception: a lightning flash briefly lifts the terrain too, otherwise the
        // sky strobes against a dead-flat world. Strictly gated by the flash envelope, so it
        // decays to nothing within a fraction of a second and can never read as fog.
        if (overcast && WorldFlash() > 0.0 && LightningPower() > 0.0) {
            vec3 st = strikeState();
            if (st.z > 0.45) {
                // Capped so Bolt Power at 200% cannot strobe the world to pure white.
                float f = min(flashEnvelope(st.x) * LightningPower() * WorldFlash(), 0.85);
                if (f > 0.002) {
                    vec3 lit = source.rgb + source.rgb * f * 0.85 + vec3(0.055, 0.062, 0.080) * f;
                    fragColor = vec4(clamp(lit, 0.0, 1.0), source.a);
                    return;
                }
            }
        }
        fragColor = source;
        return;
    }

    // Pure sky pixel — replace with sky shader (like custom skybox).
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 viewDirection = normalize(vec3(ndc.x * Aspect() * TanHalfFov(), ndc.y * TanHalfFov(), -1.0));
    vec3 skyDirection = getSkyDirection(viewDirection);
    float mode = modeId;
    float intensity = max(Intensity(), 0.0);

    vec3 skyColor;
    float amount;

    if (mode < 0.5) {
        float skyHeight = viewDirection.y * cos(SkyPitch()) + viewDirection.z * sin(SkyPitch());
        float gradient = smoothstep(-0.22, 0.86, skyHeight);
        skyColor = mix(firstColor.rgb, secondColor.rgb, gradient);
        float colorAlpha = clamp(max(firstColor.a, secondColor.a), 0.0, 1.0);
        // Keep gradient readable even if color alpha is low
        amount = clamp(intensity * max(colorAlpha, 0.85), 0.0, 1.0);
    } else if (mode < 1.5) {
        skyColor = galaxyColor(skyDirection);
        amount = clamp(intensity * 1.0, 0.0, 1.0);
    } else if (mode < 2.5) {
        skyColor = aquaColor(skyDirection);
        amount = clamp(intensity * 1.0, 0.0, 1.0);
    } else if (mode < 3.5) {
        skyColor = purpleShaderColor(skyDirection);
        amount = clamp(intensity * max(purpleColor.a, 0.85), 0.0, 1.0);
    } else {
        skyColor = overcastSky(skyDirection);
        amount = clamp(intensity * 1.0, 0.0, 1.0);
        // Rain streaks only on sky pixels
        vec2 rainUv = vec2(texCoord.x * Aspect(), 1.0 - texCoord.y);
        rainUv += vec2(cameraPos.x * 0.006 + cameraPos.z * 0.002, cameraPos.y * 0.012);
        float farRain = rainLayer(rainUv * vec2(1.0, 1.35), 42.0, 2.7, -0.34, 0.010, 0.72, 2.0);
        float midRain = rainLayer(rainUv * vec2(1.0, 1.18) + 19.3, 62.0, 3.8, -0.42, 0.007, 0.64, 7.0);
        float nearRain = rainLayer(rainUv * vec2(1.0, 1.04) + 41.8, 86.0, 5.1, -0.50, 0.006, 0.58, 13.0);
        float rain = farRain * 0.42 + midRain * 0.58 + nearRain * 0.74;
        float rainFade = 0.56 + (1.0 - smoothstep(0.12, 0.92, texCoord.y)) * 0.44;
        skyColor = mix(skyColor, vec3(0.50, 0.56, 0.61), clamp(rain * 0.55 * amount * rainFade, 0.0, 0.40));

        // ---- Lightning ----
        vec3 st = strikeState();
        if (LightningPower() > 0.0 && st.z > 0.45) {
            float flash = flashEnvelope(st.x) * LightningPower();
            if (flash > 0.002) {
                vec2 boltUv = vec2(texCoord.x * Aspect(), texCoord.y);
                float baseX = (0.12 + st.y * 0.76) * Aspect();
                float seed = st.z * 37.0 + st.y * 13.0;

                // The bolt itself lives far shorter than the afterglow it throws on the clouds.
                float boltLife = exp(-max(st.x, 0.0) * 17.0);

                float trunkEnd = 0.30 + st.y * 0.16;
                float bolt = boltShape(boltUv, baseX, seed, 1.20, trunkEnd, 0.0060);
                // Forks: independent jitter seeds so they read as separate branches rather
                // than parallel copies of the trunk.
                bolt += boltShape(boltUv, baseX + 0.055, seed + 3.10, 0.86, trunkEnd + 0.16, 0.0038) * 0.55;
                bolt += boltShape(boltUv, baseX - 0.062, seed + 7.70, 0.78, trunkEnd + 0.21, 0.0034) * 0.45;

                // Backlight the clouds, strongest around the bolt's column.
                float proximity = exp(-abs(boltUv.x - baseX) * 1.9);
                float height = smoothstep(-0.10, 0.75, texCoord.y);
                vec3 flashTint = vec3(0.62, 0.68, 0.86);
                skyColor += flashTint * flash * (0.16 + proximity * 0.42) * height;

                // Rain catches the light for the duration of the stroke.
                skyColor += vec3(0.70, 0.74, 0.82) * rain * flash * proximity * 0.30;

                // Core of the bolt: near-white, additive so it survives a bright sky.
                float boltIntensity = clamp(bolt * boltLife * min(LightningPower(), 1.5), 0.0, 2.5);
                skyColor += vec3(1.00, 0.98, 0.92) * boltIntensity;

                skyColor = clamp(skyColor, 0.0, 1.0);
            }
        }
    }

    float blend = sky * skyReplaceAmount(amount);
    // Replace sky, leave nothing fog-like on terrain
    fragColor = vec4(mix(source.rgb, skyColor, blend), source.a);
}
