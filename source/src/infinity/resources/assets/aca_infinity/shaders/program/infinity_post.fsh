#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform float PulseTime, RefractStrength, RefractRing, FocusUvX, FocusUvY, SakugaFlash, ChromaticBoost, BloomStrength;
uniform float TimeFieldStrength, TimeFieldRadius, FieldCenterX, FieldCenterY, FieldCenterZ;
uniform float Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7;
uniform float Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15;

in vec2 texCoord;
out vec4 fragColor;

vec3 extractSoftHighlight(vec3 c) {
    float luma = max(max(c.r, c.g), c.b) * 0.55 + dot(c, vec3(0.2126, 0.7152, 0.0722)) * 0.45;
    float knee = smoothstep(0.68, 1.02, luma);
    return c * knee * knee;
}

void main() {
    vec2 texel = 1.0 / max(vec2(1.0), OutSize);
    vec2 aspect = vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    vec2 focus = vec2(FocusUvX, FocusUvY);
    vec2 delta = (texCoord - focus) * aspect;
    float rDist = length(delta);
    vec2 radialDir = delta / max(1e-4, rDist);

    vec2 sampleUv = texCoord;

    // 1. Pre-Burst Vacuum Implosion + Radial Air-Density Shockwave + Heat-Haze Curl Shimmer
    if (SakugaFlash < -0.005) {
        float pull = -SakugaFlash * exp(-rDist * 2.8) * 0.018;
        sampleUv = clamp(sampleUv - (texCoord - focus) * pull, 0.001, 0.999);
    }
    if (RefractStrength > 0.002) {
        float ringDelta = rDist - RefractRing;
        float shockShell = exp(-ringDelta * ringDelta * 185.0) * (ringDelta > 0.0 ? 1.0 : -0.65);
        float heatHaze = sin(texCoord.y * 42.0 + PulseTime * 11.0) * cos(texCoord.x * 36.0 - PulseTime * 8.0)
                       * exp(-rDist * 3.2) * 0.16;
        vec2 warp = (radialDir / aspect) * (shockShell + heatHaze) * RefractStrength * 0.025;
        sampleUv = clamp(sampleUv + warp, 0.001, 0.999);
    }

    // 2. Chromatic Dispersion across Shockwave Shell & Heavy Impacts
    vec3 color;
    float dispAmt = RefractStrength * 0.0036 * exp(-pow((rDist - RefractRing) * 10.5, 2.0))
                  + ChromaticBoost * 0.0042 * smoothstep(0.08, 0.82, rDist);
    if (dispAmt > 0.00025) {
        vec2 dUv = (radialDir / aspect) * dispAmt;
        color.r = texture(DiffuseSampler, clamp(sampleUv + dUv, 0.001, 0.999)).r;
        color.g = texture(DiffuseSampler, sampleUv).g;
        color.b = texture(DiffuseSampler, clamp(sampleUv - dUv, 0.001, 0.999)).b;
    } else {
        color = texture(DiffuseSampler, sampleUv).rgb;
    }

    // 3. V1 Next-Gen 12-Tap Golden-Angle Dual-Radius Soft Highlight Bloom (silky halo without white blowout!)
    if (BloomStrength > 0.006) {
        vec3 bloomAcc = vec3(0.0);
        float wSum = 0.0;
        const float GOLDEN_ANG = 2.39996323;
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float radPx = 2.2 + fi * 1.65;
            float ang = fi * GOLDEN_ANG;
            vec2 off = vec2(cos(ang), sin(ang)) * texel * radPx;
            float w = exp(-fi * 0.14);
            vec3 sCol = texture(DiffuseSampler, clamp(sampleUv + off, 0.001, 0.999)).rgb;
            bloomAcc += extractSoftHighlight(sCol) * w;
            wSum += w;
        }
        vec3 softBloom = (bloomAcc / max(1e-4, wSum)) * BloomStrength;
        // Soft screen-like tonemap blend so bright white centers never clip harshly
        color = 1.0 - (1.0 - clamp(color, 0.0, 1.0)) * (1.0 - clamp(softBloom * 0.78, 0.0, 0.65));
    }

    // 4. Depth-Reconstructed 3D Time-Stop Stasis Field
    if (TimeFieldStrength > 0.005) {
        float depth = texture(DepthSampler, sampleUv).r;
        if (depth < 0.999995) {
            mat4 invProjView = mat4(Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7,
                                    Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15);
            vec4 clip = vec4(sampleUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec4 relWorld = invProjView * clip;
            vec3 point = relWorld.xyz / max(1e-5, relWorld.w);
            float distToCenter = length(point - vec3(FieldCenterX, FieldCenterY, FieldCenterZ));

            float insideMask = (1.0 - smoothstep(TimeFieldRadius - 0.85, TimeFieldRadius + 0.15, distToCenter)) * TimeFieldStrength;
            if (insideMask > 0.002) {
                float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                vec3 stasisTone = mix(vec3(luma), color, 0.36) * vec3(0.76, 0.97, 0.88)
                                + luma * vec3(0.02, 0.08, 0.05);
                color = mix(color, stasisTone, insideMask * 0.68);
            }

            float boundaryBand = (1.0 - smoothstep(0.05, 0.62, abs(distToCenter - TimeFieldRadius))) * TimeFieldStrength;
            float shimmer = 0.82 + 0.18 * sin(PulseTime * 5.0 + point.x * 1.7 + point.z * 1.7);
            color += mix(vec3(0.16, 0.96, 0.56), vec3(0.95, 0.92, 0.62), 0.25) * boundaryBand * shimmer * 0.44;
        }
    }

    // 5. Sakuga Pre-Burst Dimming OR High-Impact Speed-Ray Flash
    if (SakugaFlash < -0.005) {
        float vac = clamp(-SakugaFlash, 0.0, 1.0);
        float edgeDark = smoothstep(0.18, 0.94, rDist) * vac;
        color *= (1.0 - edgeDark * 0.35);
    } else if (SakugaFlash > 0.008) {
        float sak = clamp(SakugaFlash, 0.0, 1.0);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float ang = atan(delta.y, delta.x);
        float speedRays = pow(0.5 + 0.5 * sin(ang * 36.0 + sin(ang * 6.0) * 2.4), 12.0) * smoothstep(0.10, 0.62, rDist);
        float hiMask = smoothstep(0.40, 0.68, luma + speedRays * 0.36);
        vec3 sakugaCol = mix(vec3(0.04, 0.05, 0.11), vec3(0.98, 0.88, 0.62), hiMask);
        color = mix(color, sakugaCol, sak * 0.30);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
