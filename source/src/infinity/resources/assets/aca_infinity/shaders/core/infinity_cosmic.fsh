#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float CosmicTime;
uniform float ReducedMotion;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float vfxMode;
in float vfxErosion;
in vec3 vViewNormal;
in vec3 vViewDir;

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

float silkFbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.54;
    mat2 rot = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 3; i++) {
        sum += amp * valueNoise(p);
        p = rot * p * 2.05 + vec2(1.7, 8.9);
        amp *= 0.48;
    }
    return sum;
}

/**
 * HSR / Destiny 2 Stylized 3-Stop Heterogeneous Color Ramp LUT:
 * - energy -> 1.0: Incandescent warm champagne-white core
 * - energy ~ 0.55: High-chroma saturated gem color
 * - energy -> 0.0: Hue-rotated deep abyssal violet/crimson/indigo shadow (never muddy grey!)
 */
vec3 stylizedLut(vec3 base, float energy) {
    float e = clamp(energy, 0.0, 1.0);
    vec3 cooledShadow = vec3(
        base.r * 0.24 + base.b * 0.30 + 0.03,
        base.g * 0.15 + base.r * 0.08 + 0.01,
        base.b * 0.40 + base.r * 0.28 + base.g * 0.16 + 0.06
    );
    vec3 saturatedMid = clamp(base * 1.16 + vec3(0.02), 0.0, 1.0);
    vec3 hotCore = mix(vec3(1.0, 0.985, 0.93), clamp(base + 0.58, 0.0, 1.0), 0.22);
    if (e < 0.54) {
        return mix(cooledShadow, saturatedMid, smoothstep(0.0, 0.54, e));
    }
    return mix(saturatedMid, hotCore, smoothstep(0.54, 1.0, e));
}

/** Dual-Phase Seamless Flow-Map Sampler (R=silk FBM, G=Voronoi shatter). */
vec2 sampleDualFlowRG(vec2 uv, float t, float strength) {
    vec2 flow = (texture(Sampler0, fract(uv * 0.65)).ba - 0.5) * 2.0;
    float ph0 = fract(t);
    float ph1 = fract(t + 0.5);
    float blend = abs(ph0 * 2.0 - 1.0);
    vec2 rg0 = texture(Sampler0, fract(uv - flow * ph0 * strength)).rg;
    vec2 rg1 = texture(Sampler0, fract(uv - flow * ph1 * strength)).rg;
    return mix(rg0, rg1, blend);
}

void main() {
    if (vertexColor.a <= 0.006) {
        discard;
    }
    float u = texCoord0.x;
    float v = clamp(texCoord0.y, 0.0, 1.0);
    float t = CosmicTime * ((ReducedMotion > 0.5) ? 0.25 : 1.0);

    vec3 N = normalize(vViewNormal);
    vec3 V = normalize(vViewDir);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fresnel = pow(1.0 - ndv, 2.25);

    // Mode 5: True 3D Hollow Fresnel Energy Shell / Crystal Spire Facet (hollow center, blazing rim!)
    if (vfxMode > 4.5) {
        vec2 flowRG = sampleDualFlowRG(vec2(u * 1.35, v * 1.35), t * 0.35, 0.16);
        float edgeGlow = clamp(fresnel * 1.18 + smoothstep(0.84, 0.99, v) * 0.55 + flowRG.g * 0.24, 0.0, 1.0);
        float dissolve = smoothstep(vfxErosion * 0.95 - 0.10, vfxErosion * 0.95 + 0.14, flowRG.r * 0.65 + fresnel * 0.55);
        vec3 rgb = stylizedLut(vertexColor.rgb, clamp(edgeGlow * 0.92 + 0.14, 0.0, 1.0));
        float hollowFill = 0.10 + 0.88 * pow(edgeGlow, 1.35);
        float alpha = vertexColor.a * hollowFill * dissolve;
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, clamp(alpha, 0.0, 1.0)) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 4: Voronoi Mirror Space-Shatter Shards & Fracture Planes (HSR / Mirror Dimension glass cracks!)
    if (vfxMode > 3.5) {
        vec2 vorUv = vec2(u * 1.65, v * 1.65);
        vec4 tex = texture(Sampler0, fract(vorUv));
        float crackRidge = smoothstep(0.72, 0.96, tex.g);
        float facetTone = clamp(tex.g * 1.15, 0.15, 0.95);
        float prismGlint = 0.5 + 0.5 * sin((facetTone * 11.0) + ndv * 5.2 - t * 2.2);
        float shardEdge = max(
            smoothstep(0.09, 0.005, min(min(u, 1.0 - u), min(v, 1.0 - v))),
            crackRidge
        );
        float erodeMask = smoothstep(vfxErosion * 1.08 - 0.08, vfxErosion * 1.08 + 0.10, facetTone * 0.72 + shardEdge * 0.45);
        float energy = clamp(shardEdge * 0.85 + fresnel * 0.55 + prismGlint * 0.35, 0.0, 1.0);
        vec3 rgb = stylizedLut(vertexColor.rgb, energy);
        // Subtle prismatic iridescent tint on mirror facets
        rgb += vec3(0.10, 0.16, 0.24) * prismGlint * fresnel * (1.0 - shardEdge);
        float alpha = vertexColor.a * erodeMask * clamp(0.24 + shardEdge * 0.76 + fresnel * 0.48, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(clamp(rgb, 0.0, 1.0), alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 3: Asymmetric Razor Crescent Shockwave / Arc + Dual-Phase Flow Wake +LUT
    if (vfxMode > 2.5) {
        float leadRazor = smoothstep(0.82, 0.965, v) * smoothstep(1.0, 0.972, v);
        float bodySweep = smoothstep(0.03, 0.90, v) * smoothstep(1.0, 0.90, v);

        vec2 flowRG = sampleDualFlowRG(vec2(u * 1.5 - t * 0.45, v * 1.7), t * 0.55, 0.22);
        float streakNoise = silkFbm(vec2(u * 4.5 - t * 2.5, v * 10.5 - u * 1.4));
        float compositeNoise = streakNoise * 0.56 + flowRG.r * 0.26 + flowRG.g * 0.18;

        float erodeThresh = mix(0.66, -0.08, pow(v, 1.35)) + vfxErosion * 0.62;
        float erodedMask = smoothstep(erodeThresh - 0.09, erodeThresh + 0.09, compositeNoise);

        vec2 sparkUv = vec2(u * 15.0 - t * 3.6, v * 8.5);
        vec2 sparkCell = floor(sparkUv);
        vec2 sparkLocal = fract(sparkUv) - 0.5 - (hash22(sparkCell) - 0.5) * 0.52;
        float spark = (1.0 - smoothstep(0.02, 0.09, length(sparkLocal * vec2(0.45, 1.55))))
                    * step(0.62, hash12(sparkCell + 5.3)) * smoothstep(0.06, 0.72, v) * (1.0 - vfxErosion);

        float lutPos = clamp(pow(v, 1.25) * 0.78 + leadRazor * 0.95 + spark * 0.85, 0.0, 1.0);
        vec3 rgb = stylizedLut(vertexColor.rgb, lutPos);
        float alpha = vertexColor.a * clamp(bodySweep * erodedMask * 0.82 + leadRazor * (1.0 - vfxErosion * 0.65) + spark * 0.88, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    float profile = sin(v * PI);
    if (profile <= 0.012) {
        discard;
    }

    // Mode 2: Broken Armillary Vernier Astrolabe Ring
    if (vfxMode > 1.5) {
        float sectorGap = smoothstep(-0.20, 0.22, sin(u * TAU * 3.0 + 0.35) + 0.38 * cos(u * TAU * 2.0 - t * 0.65));
        float outerHair = smoothstep(0.052, 0.009, abs(v - 0.84)) * (0.32 + 0.68 * sectorGap);
        float innerHair = smoothstep(0.045, 0.009, abs(v - 0.20)) * (0.50 + 0.50 * sectorGap);
        float vernier = smoothstep(0.89, 0.985, cos(u * TAU * 24.0)) * step(0.20, v) * step(v, 0.42) * sectorGap;
        float cardinal6 = smoothstep(0.968, 0.998, cos(u * TAU * 6.0)) * smoothstep(0.42, 0.02, abs(v - 0.52));

        float erodeNoise = valueNoise(vec2(u * 16.0, v * 6.0 + t * 0.75));
        float erodeMask = smoothstep(vfxErosion * 0.92 - 0.08, vfxErosion * 0.92 + 0.12, erodeNoise + cardinal6 * 0.35);

        float geom = clamp(outerHair + innerHair * 0.88 + vernier * 0.60 + cardinal6 * 1.25, 0.0, 1.0) * erodeMask;
        float whisperVeil = profile * 0.06 * sectorGap * (1.0 - vfxErosion);

        vec3 rgb = stylizedLut(vertexColor.rgb, clamp(0.48 + cardinal6 * 0.52 + outerHair * 0.25, 0.0, 1.0));
        float alpha = vertexColor.a * clamp(geom * 0.95 + whisperVeil, 0.0, 1.0);
        if (alpha <= 0.012) discard;
        fragColor = linear_fog(vec4(rgb, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 1: Dual-Phase Flowmap Silk Whip / Ribbon + 3-Stop Heterogeneous LUT + Fresnel Rim
    if (vfxMode > 0.5) {
        vec2 flowRG = sampleDualFlowRG(vec2(u * 0.95 - t * 0.55, v * 1.05), t * 0.52, 0.20);
        float strandGrooves = 0.55 + 0.45 * sin(v * PI * 5.0 + sin(u * 3.1 - t * 2.4) * 0.95);
        float fbm = silkFbm(vec2(u * 3.2 - t * 2.6, v * 8.0));
        float spine = smoothstep(0.44, 0.0, abs(v - 0.5));
        float razorCore = smoothstep(0.125, 0.0, abs(v - 0.5));

        float silkField = fbm * 0.48 + strandGrooves * 0.22 + flowRG.r * 0.30 + spine * 0.28;
        float thresh = 0.25 + vfxErosion * 0.74;
        float erodedSilk = smoothstep(thresh - 0.10, thresh + 0.12, silkField);

        float coreHold = razorCore * smoothstep(1.02, 0.42, vfxErosion + (1.0 - fbm) * 0.26);
        float lutEnergy = clamp(spine * 0.62 + flowRG.r * 0.24 + coreHold * 0.85 + fresnel * 0.22, 0.0, 1.0);
        vec3 rgb = stylizedLut(vertexColor.rgb, lutEnergy);
        float alpha = vertexColor.a * clamp(pow(profile, 0.80) * erodedSilk * 0.76 + coreHold * 0.96, 0.0, 1.0);
        if (alpha <= 0.010) discard;
        fragColor = linear_fog(vec4(clamp(rgb, 0.0, 1.0), alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // Mode 0: Abyssal Cosmic Dark Contrast Sheath (TRANSLUCENT pass — frames bright whips against bright sky/terrain!)
    float wisp = silkFbm(vec2(u * 3.2 - t * 1.75, v * 7.5 + sin(u * 2.0) * 0.4));
    float spineHold = smoothstep(0.48, 0.0, abs(v - 0.5));
    float thresh = 0.22 + vfxErosion * 0.66 - spineHold * 0.22;
    float mask = smoothstep(thresh - 0.12, thresh + 0.14, wisp);

    vec2 stUv = vec2(u * 9.0 - t * 1.15, v * 5.0);
    vec2 stCell = floor(stUv);
    float stDist = length(fract(stUv) - 0.5 - (hash22(stCell) - 0.5) * 0.56);
    float microStar = (1.0 - smoothstep(0.02, 0.085, stDist)) * step(0.58, hash12(stCell));

    vec3 obsidian = vec3(0.020, 0.028, 0.072);
    vec3 rimTint = mix(vec3(0.08, 0.09, 0.22), stylizedLut(vertexColor.rgb, 0.22), 0.65);
    vec3 rgb = mix(rimTint, obsidian, spineHold * 0.78) + vec3(0.92, 0.95, 1.0) * microStar * 0.54;
    float alpha = vertexColor.a * pow(profile, 0.66) * mask;
    if (alpha <= 0.012) discard;
    fragColor = linear_fog(vec4(rgb, clamp(alpha, 0.0, 0.90)) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
