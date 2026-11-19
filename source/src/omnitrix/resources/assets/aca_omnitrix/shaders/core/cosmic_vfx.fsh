#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float StarfieldSeconds;
uniform float ReducedMotion;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float vfxMode;
in float vfxErosion;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i + vec2(0.0, 0.0)), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

/** Anisotropic 3-octave FBM tuned for directional calligraphic silk wisps & sword-wake shredding. */
float silkFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.54;
    mat2 rot = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 3; i++) {
        sum += amp * valueNoise(p);
        p = rot * p * 2.07 + vec2(1.7, 9.2);
        amp *= 0.48;
    }
    return sum;
}

vec2 mirrored(vec2 p) {
    return 1.0 - abs(fract(p * 0.5) * 2.0 - 1.0);
}

vec3 flowTexture(vec2 p, float halfIndex) {
    vec2 uv = clamp(mirrored(p), vec2(0.002), vec2(0.998));
    return texture(Sampler0, vec2((uv.x + halfIndex) * 0.5, uv.y)).rgb;
}

/**
 * V1 Dual-Phase Seamless Flow-Map UV Distortion:
 * Eliminates repetition & pop artifacts by blending two half-cycle offset flow samples!
 */
vec2 dualPhaseFlow(vec2 uv, float time, float strength) {
    float p0 = fract(time * 0.36);
    float p1 = fract(time * 0.36 + 0.5);
    float blend = abs(0.5 - p0) * 2.0;
    vec2 flowDir = (flowTexture(uv * 0.55, 0.0).rg - 0.5) * 2.0;
    vec2 uv0 = uv - flowDir * p0 * strength;
    vec2 uv1 = uv - flowDir * p1 * strength;
    return mix(uv0, uv1, blend);
}

/**
 * V1 HSR/Destiny-2 3-Stop Heterogeneous Color-Ramp LUT:
 * Maps scalar intensity [0..1] across shifted deep chromatic tail -> saturated mid -> incandescent star-core!
 */
vec3 stylizedLut(vec3 baseHue, float energy) {
    float e = clamp(energy, 0.0, 1.0);
    // Chromatic hue-shift in shadows (warm->crimson-violet, green->deep-teal-indigo, blue/violet->abyssal-cobalt)
    vec3 deepTail = clamp(vec3(
        baseHue.r * 0.34 + baseHue.b * 0.24,
        baseHue.g * 0.30 + baseHue.r * 0.08,
        baseHue.b * 0.52 + baseHue.g * 0.26 + 0.10
    ), 0.0, 1.0);
    vec3 richMid = clamp(mix(baseHue, pow(max(baseHue, vec3(0.02)), vec3(0.84)) * 1.12, 0.65), 0.0, 1.0);
    vec3 starCore = mix(vec3(1.0, 0.985, 0.94), clamp(baseHue * 0.35 + vec3(0.72), 0.0, 1.0), 0.35);
    if (e < 0.55) {
        return mix(deepTail, richMid, smoothstep(0.0, 0.55, e));
    }
    return mix(richMid, starCore, smoothstep(0.55, 0.97, e));
}

/** Voronoi cell distance & fracture edge metric for Mode-17 Mirror Space-Shatter! */
vec3 voronoiShatter(vec2 x) {
    vec2 n = floor(x);
    vec2 f = fract(x);
    float md1 = 8.0;
    float md2 = 8.0;
    float cellId = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash22(n + g);
            vec2 r = g + 0.15 + 0.70 * o - f;
            float d = dot(r, r);
            if (d < md1) {
                md2 = md1;
                md1 = d;
                cellId = hash12(n + g + 13.7);
            } else if (d < md2) {
                md2 = d;
            }
        }
    }
    float edgeDist = sqrt(md2) - sqrt(md1);
    return vec3(edgeDist, cellId, sqrt(md1));
}

void main() {
    float v = clamp(texCoord0.y, 0.0, 1.0);
    if (vertexColor.a <= 0.006) {
        discard;
    }

    float speed = (ReducedMotion > 0.5) ? 0.22 : 1.0;
    float t = StarfieldSeconds * speed;
    float u = texCoord0.x;

    // Mode 17: Voronoi Mirror Space-Shatter Crown & Dimensional Fracture Shards (VFX 4.0)
    if (vfxMode > 16.5) {
        float profile = sin(v * PI);
        if (profile <= 0.01) discard;
        vec3 vor = voronoiShatter(vec2(u * 9.0, v * 3.4) + vec2(t * 0.08, -t * 0.04));
        float crack = 1.0 - smoothstep(0.018, 0.085, vor.x);
        float facetGlint = 0.45 + 0.55 * sin(vor.y * TAU + t * 2.4 + u * 5.0);
        float shardKeep = step(vfxErosion * 0.92, vor.y + crack * 0.32);
        if (shardKeep <= 0.01) discard;

        vec3 glassTint = stylizedLut(vertexColor.rgb, 0.35 + 0.52 * facetGlint + 0.42 * crack);
        vec3 specularRim = vec3(0.98, 0.99, 1.0) * crack * 0.95;
        vec3 rgb = clamp(glassTint * (0.62 + 0.48 * facetGlint) + specularRim, 0.0, 1.0);
        float alpha = vertexColor.a * shardKeep * pow(profile, 0.55) * clamp(0.36 * facetGlint + crack * 0.96, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    if (vfxMode > 15.5) {
        vec2 position = vec2(cos(u * TAU), sin(u * TAU)) * v;
        float cloud = silkFbm(position * vec2(8.0, 4.0) + vec2(t * 0.022, t * 0.012));
        vec3 haze = vertexColor.rgb * (0.39 + 0.61 * cloud);
        haze += vec3(0.066, 0.028, 0.095) * smoothstep(0.46, 0.76, cloud);
        vec2 grid = position * 106.0;
        vec2 cell = floor(grid);
        vec2 delta = fract(grid) - 0.5 - (hash22(cell) - 0.5) * 0.56;
        float size = max(0.026, min(0.11, length(fwidth(grid))));
        float star = (1.0 - smoothstep(0.027, 0.027 + size, length(delta))) * step(0.74, hash12(cell));
        vec3 stars = mix(vec3(0.42, 0.52, 0.69), vec3(0.75, 0.52, 0.42), hash12(cell + 27.0)) * star * 0.65;
        fragColor = vec4(haze + stars, vertexColor.a * (1.0 - smoothstep(0.55, 1.0, v))) * ColorModulator;
        return;
    }

    if (vfxMode > 14.5) {
        if (!gl_FrontFacing) discard;
        float latitude = (v - 0.5) * PI;
        float longitude = u * TAU;
        vec3 normal = vec3(cos(longitude) * cos(latitude), sin(latitude), sin(longitude) * cos(latitude));
        float light = dot(normal, normalize(vec3(-0.62, 0.43, -0.52)));
        float daySide = smoothstep(-0.15, 0.37, light);
        vec2 surface = vec2(normal.x + normal.z * 0.55, normal.y) * vec2(6.0, 8.0);
        float clouds = silkFbm(surface + vec2(t * 0.044, 0.0));
        float stripes = 0.64 + 0.36 * sin(normal.y * 36.0 + clouds * 5.0);
        vec3 hue = vertexColor.rgb;
        vec3 dark = mix(vec3(0.034, 0.028, 0.081), hue * 0.24, 0.48);
        vec3 lit = hue * (0.50 + stripes * 0.37 + clouds * 0.17);
        float rim = smoothstep(-0.28, 0.0, light) * (1.0 - smoothstep(0.0, 0.26, light));
        vec3 color = mix(dark, lit, daySide) + hue * rim * 0.18;
        fragColor = vec4(color, vertexColor.a) * ColorModulator;
        return;
    }

    if (vfxMode > 6.5) {
        float row = clamp(floor(vfxMode + 0.5) - 7.0, 0.0, 7.0);
        float drift = (row == 5.0 ? 0.85 : -0.44 - row * 0.045) * t;
        vec2 uv = vec2(fract(u + drift) * 0.992 + 0.004, (row * 64.0 + 1.0 + v * 62.0) / 512.0);
        vec3 grain = texture(Sampler0, uv).rgb;
        float edge = smoothstep(0.0, 0.27, sin(v * PI));
        float breakUp = smoothstep(vfxErosion * 0.78 - 0.16, vfxErosion * 0.78 + 0.13, grain.b);
        vec3 rgb = stylizedLut(vertexColor.rgb, grain.r * 0.76 + grain.g * 0.24);
        float alpha = vertexColor.a * edge * breakUp * (0.69 + grain.r * 0.3);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 6: Anamorphic Hypersonic Starlight Streak / Micro-Ember (5% focal accent)
    if (vfxMode > 5.5) {
        float dx = abs(u - 0.5) * 2.0;
        float dy = abs(v - 0.5) * 2.0;
        float core = exp(-dy * dy * 18.0) * smoothstep(1.0, 0.0, pow(dx, 0.72));
        float crossGlint = exp(-dx * dx * 28.0) * exp(-dy * dy * 3.2) * 0.55;
        float alpha = vertexColor.a * clamp(core + crossGlint, 0.0, 1.0) * (1.0 - vfxErosion * 0.85);
        if (alpha <= 0.012) discard;
        vec3 rgb = stylizedLut(vertexColor.rgb, clamp(0.42 + core * 0.58 + crossGlint * 0.35, 0.0, 1.0));
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 5: Asymmetric Razor Blade Arc / Crescent Wave (3-Stop LUT + Dual-Phase Flow Shredding!)
    if (vfxMode > 4.5) {
        float leadRazor = smoothstep(0.84, 0.965, v) * smoothstep(1.0, 0.972, v);
        float bodySweep = smoothstep(0.03, 0.88, v) * smoothstep(1.0, 0.89, v);

        vec2 flowUv = dualPhaseFlow(vec2(u * 4.2 - t * 2.2, v * 11.5 - u * 1.6), t, 0.34);
        float streakNoise = silkFbm(flowUv);
        float fineStrand = valueNoise(vec2(u * 9.5 - t * 4.2, v * 26.0));
        float compositeNoise = streakNoise * 0.70 + fineStrand * 0.30;

        float erodeThresh = mix(0.68, -0.08, pow(v, 1.45)) + vfxErosion * 0.62;
        float erodedMask = smoothstep(erodeThresh - 0.09, erodeThresh + 0.09, compositeNoise);

        vec2 sparkUv = vec2(u * 14.0 - t * 3.8, v * 9.0);
        vec2 sparkCell = floor(sparkUv);
        vec2 sparkLocal = fract(sparkUv) - 0.5 - (hash22(sparkCell) - 0.5) * 0.52;
        float spark = (1.0 - smoothstep(0.02, 0.09, length(sparkLocal * vec2(0.45, 1.6))))
                    * step(0.62, hash12(sparkCell + 7.1)) * smoothstep(0.05, 0.65, v) * (1.0 - vfxErosion);

        float lutEnergy = clamp(pow(v, 1.25) * 0.72 + compositeNoise * 0.22 + leadRazor * 0.65 + spark * 0.6, 0.0, 1.0);
        vec3 rgb = stylizedLut(vertexColor.rgb, lutEnergy);
        float alpha = vertexColor.a * clamp(bodySweep * erodedMask * 0.80 + leadRazor * (1.0 - vfxErosion * 0.65) + spark * 0.85, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 4: Abyssal Star-Void Contrast Sheath (rendered via TRANSLUCENT pass to carve dark negative space!)
    if (vfxMode > 3.5) {
        float profile = sin(v * PI);
        if (profile <= 0.015) discard;
        float wisp = silkFbm(vec2(u * 3.1 - t * 1.85, v * 7.8 + sin(u * 2.0) * 0.4));
        float spineHold = smoothstep(0.48, 0.0, abs(v - 0.5));
        float thresh = 0.24 + vfxErosion * 0.66 - spineHold * 0.22;
        float mask = smoothstep(thresh - 0.12, thresh + 0.14, wisp);

        vec2 stUv = vec2(u * 8.5 - t * 1.1, v * 5.2);
        vec2 stCell = floor(stUv);
        float stDist = length(fract(stUv) - 0.5 - (hash22(stCell) - 0.5) * 0.56);
        float microStar = (1.0 - smoothstep(0.02, 0.085, stDist)) * step(0.55, hash12(stCell));

        vec3 obsidian = vec3(0.032, 0.055, 0.125);
        vec3 indigoRim = mix(vec3(0.09, 0.16, 0.32), vertexColor.rgb * 0.35, 0.45);
        vec3 rgb = mix(indigoRim, obsidian, spineHold * 0.75) + vec3(0.88, 0.93, 1.0) * microStar * 0.55;
        float alpha = vertexColor.a * pow(profile, 0.68) * mask;
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, clamp(alpha, 0.0, 0.92)) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    float profile = sin(v * PI);
    if (profile <= 0.012) {
        discard;
    }

    // Mode 3: Non-Uniform Azmuth Armillary Astrolabe (70% breathable arc gaps, 25% hairline tracks, 5% cardinal diamond glints)
    if (vfxMode > 2.5) {
        float sectorGap = smoothstep(-0.18, 0.22, sin(u * TAU * 3.0 + 0.4) + 0.35 * cos(u * TAU * 2.0 - t * 0.6));
        float outerHair = smoothstep(0.048, 0.008, abs(v - 0.84)) * (0.35 + 0.65 * sectorGap);
        float innerHair = smoothstep(0.042, 0.008, abs(v - 0.22)) * (0.55 + 0.45 * sectorGap);
        float vernier = smoothstep(0.90, 0.985, cos(u * TAU * 18.0)) * step(0.22, v) * step(v, 0.38) * sectorGap;
        float cardinal3 = smoothstep(0.965, 0.998, cos(u * TAU * 3.0)) * smoothstep(0.42, 0.02, abs(v - 0.53));

        float erodeNoise = valueNoise(vec2(u * 16.0, v * 6.0 + t * 0.8));
        float erodeMask = smoothstep(vfxErosion * 0.92 - 0.08, vfxErosion * 0.92 + 0.12, erodeNoise + cardinal3 * 0.35);

        float geom = clamp(outerHair + innerHair * 0.88 + vernier * 0.58 + cardinal3 * 1.25, 0.0, 1.0) * erodeMask;
        float whisperVeil = profile * 0.06 * sectorGap * (1.0 - vfxErosion);

        vec3 baseTint = mix(vec3(0.72, 0.83, 0.97), vertexColor.rgb, 0.68);
        vec3 champagne = vec3(0.99, 0.95, 0.84);
        vec3 rgb = mix(baseTint, champagne, clamp(cardinal3 * 1.2, 0.0, 1.0));
        float alpha = vertexColor.a * clamp(geom * 0.94 + whisperVeil, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 2: V1 Dual-Phase Flow + 3-Stop LUT Multi-Strand Silk & Energy Whip Filament!
    if (vfxMode > 1.5) {
        vec2 warpedUv = dualPhaseFlow(vec2(u * 0.68 - t * 0.34, v * 0.85 + t * 0.04), t, 0.28);
        vec3 silverFlow = flowTexture(warpedUv, 0.0);
        float flowLuma = dot(silverFlow, vec3(0.2126, 0.7152, 0.0722));

        float strandGrooves = 0.55 + 0.45 * sin(v * PI * 5.0 + sin(u * 2.6 - t * 1.9) * 0.85);
        float fbm = silkFbm(dualPhaseFlow(vec2(u * 2.8 - t * 2.1, v * 8.5), t, 0.32));
        float spine = smoothstep(0.42, 0.0, abs(v - 0.5));
        float razorFilament = smoothstep(0.095, 0.0, abs(v - 0.5));

        float silkField = fbm * 0.62 + strandGrooves * 0.26 + flowLuma * 0.22 + spine * 0.28;
        float thresh = 0.28 + vfxErosion * 0.74;
        float erodedSilk = smoothstep(thresh - 0.10, thresh + 0.12, silkField);

        float coreHold = razorFilament * smoothstep(1.02, 0.45, vfxErosion + (1.0 - fbm) * 0.28);
        float lutEnergy = clamp(spine * 0.56 + flowLuma * 0.24 + coreHold * 0.58 + strandGrooves * 0.12, 0.0, 1.0);
        vec3 rgb = stylizedLut(vertexColor.rgb, lutEnergy);
        float alpha = vertexColor.a * clamp(pow(profile, 0.85) * erodedSilk * 0.72 + coreHold * 0.94, 0.0, 1.0);
        if (alpha <= 0.010) discard;
        fragColor = linear_fog(vec4(clamp(rgb, 0.0, 1.0), alpha) * ColorModulator,
                               vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 1: Crisp 3-Stop LUT Shockwave / Elemental Ribbon
    if (vfxMode > 0.5) {
        float wave = 0.82 + 0.18 * sin(u * 10.0 - t * 9.0 + sin(v * 6.0));
        float spine = smoothstep(0.42, 0.0, abs(v - 0.5));
        vec3 col = stylizedLut(vertexColor.rgb, clamp(0.24 + spine * 0.68, 0.0, 1.0)) * wave;
        float alpha = vertexColor.a * pow(profile, 0.72) * (0.72 + 0.28 * spine);
        fragColor = linear_fog(vec4(col, clamp(alpha, 0.0, 1.0)) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 0: Celestialsapien Forge-of-Creation Nebula + Dual-Layer Flowing Star Stream
    float flow = u * 3.8 - t * 2.15;
    float n1 = sin(flow * 2.2 + v * 5.2 + sin(u * 5.0 - t * 1.4) * 0.7);
    float n2 = cos(flow * 3.4 - v * 6.8 + cos(u * 3.1 + t * 1.1) * 0.6);
    float nebula = smoothstep(-0.45, 0.88, n1 * 0.58 + n2 * 0.42);
    if (ReducedMotion > 0.5) {
        nebula *= 0.75;
    }

    vec3 voidDark = vec3(0.045, 0.058, 0.115);
    vec3 deepSlate = vec3(0.20, 0.26, 0.46);
    vec3 accent = vertexColor.rgb;
    vec3 nebulaCol = mix(voidDark, mix(deepSlate, accent, 0.58), nebula * 0.84);

    vec2 dustUv = vec2(u * 13.5 - t * 4.4, v * 4.5 + 0.19);
    vec2 dustCell = floor(dustUv);
    vec2 dustLocal = fract(dustUv) - 0.5;
    vec2 h1 = hash22(dustCell);
    float d1 = length(dustLocal - (h1 - 0.5) * 0.62);
    float dustStar = 1.0 - smoothstep(0.035, 0.135, d1);
    vec3 dustCol = mix(vec3(0.82, 0.90, 1.0), accent, h1.x * 0.4) * dustStar * 0.92;

    vec2 starUv = vec2(u * 6.2 - t * 2.25, v * 2.4 + 0.53);
    vec2 starCell = floor(starUv);
    vec2 starLocal = (fract(starUv) - 0.5) - (hash22(starCell) - 0.5) * 0.54;
    float d2 = length(starLocal);
    float coreStar = 1.0 - smoothstep(0.045, 0.175, d2);
    float crossFlare = max(0.0, 1.0 - abs(starLocal.x) * 14.0) * max(0.0, 1.0 - abs(starLocal.y) * 5.5)
                     + max(0.0, 1.0 - abs(starLocal.y) * 14.0) * max(0.0, 1.0 - abs(starLocal.x) * 5.5);
    float twinkle = (ReducedMotion > 0.5) ? 0.85 : (0.78 + 0.22 * sin(t * 6.0 + starCell.x * 2.7 + starCell.y * 4.1));
    vec3 brightStarCol = mix(vec3(0.92, 0.96, 1.0), vec3(1.0, 0.94, 0.82), fract(starCell.x * 0.37))
                       * ((coreStar + crossFlare * 0.36) * twinkle * 1.32);

    float spine = smoothstep(0.30, 0.0, abs(v - 0.5));
    vec3 spineCol = mix(accent, vec3(0.90, 0.95, 1.0), 0.52) * spine * (0.24 + 0.22 * nebula);

    vec3 finalRgb = nebulaCol + dustCol + brightStarCol + spineCol;
    if (ReducedMotion > 0.5) {
        finalRgb *= 0.78;
    }

    float starBoost = clamp(dustStar * 0.45 + coreStar * 0.75, 0.0, 0.85);
    float alpha = vertexColor.a * pow(profile, 0.62) * clamp(0.68 + 0.24 * nebula + starBoost, 0.0, 1.0);
    fragColor = linear_fog(vec4(finalRgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
