#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D AscalonFlowSampler;
uniform vec2 OutSize;
uniform float Impact, Blood, PulseTime, Freeze, AscalonAura, SyzygyFlash;
uniform float RefractStrength, RefractRing, FocusUvX, FocusUvY, SakugaFlash, BloomBoost;
uniform float Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7;
uniform float Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15;
uniform float CenterX, CenterY, CenterZ;
uniform float CasterX, CasterY, CasterZ;
in vec2 texCoord;
out vec4 fragColor;

vec2 mirrorFlow(vec2 p) {
    return clamp(1.0 - abs(fract(p * 0.5) * 2.0 - 1.0), vec2(0.002), vec2(0.998));
}

vec3 extractBright(vec2 uv) {
    vec3 s = texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb;
    float luma = dot(s, vec3(0.2126, 0.7152, 0.0722));
    float knee = smoothstep(0.80, 0.985, luma);
    return s * knee;
}

void main() {
    vec2 aspect = vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    vec2 focus = vec2(FocusUvX, FocusUvY);
    vec2 delta = (texCoord - focus) * aspect;
    float rDist = length(delta);
    vec2 radialDir = delta / max(1e-4, rDist);

    vec2 sampleUv = texCoord;

    // 1. Pre-Burst Vacuum Implosion (SakugaFlash < 0) & Air-Density Shockwave Refraction (RefractStrength > 0)
    if (SakugaFlash < -0.005) {
        float pull = -SakugaFlash * exp(-rDist * 2.8) * 0.018;
        sampleUv -= (texCoord - focus) * pull;
    }
    if (RefractStrength > 0.002) {
        float ringDelta = rDist - RefractRing;
        float shockShell = exp(-ringDelta * ringDelta * 190.0) * (ringDelta > 0.0 ? 1.0 : -0.65);
        vec2 warp = (radialDir / aspect) * shockShell * RefractStrength * 0.022;
        sampleUv = clamp(sampleUv + warp, 0.001, 0.999);
    }
    if (SyzygyFlash > 0.005) {
        float waveRing = sin(rDist * 20.0 - (1.0 - SyzygyFlash) * 15.0) * SyzygyFlash * 0.009;
        sampleUv = clamp(sampleUv + (radialDir / aspect) * waveRing, 0.001, 0.999);
    }

    // Subtle chromatic dispersion across air-density shockwave shell
    vec3 color;
    if (RefractStrength > 0.015) {
        float disp = RefractStrength * 0.0032 * exp(-pow((rDist - RefractRing) * 11.0, 2.0));
        vec2 dUv = (radialDir / aspect) * disp;
        color.r = texture(DiffuseSampler, clamp(sampleUv + dUv, 0.001, 0.999)).r;
        color.g = texture(DiffuseSampler, sampleUv).g;
        color.b = texture(DiffuseSampler, clamp(sampleUv - dUv, 0.001, 0.999)).b;
    } else {
        color = texture(DiffuseSampler, sampleUv).rgb;
    }

    // 2. High-Threshold Golden-Angle Optical Bloom & Anamorphic Glint (makes blade cores & stars halate naturally!)
    if (BloomBoost > 0.005) {
        vec2 texel = 1.0 / max(vec2(320.0, 180.0), OutSize);
        vec3 acc = vec3(0.0);
        // 8-tap wide + anamorphic horizontal spread
        acc += extractBright(sampleUv + vec2(-3.5,  0.0) * texel) * 0.16;
        acc += extractBright(sampleUv + vec2( 3.5,  0.0) * texel) * 0.16;
        acc += extractBright(sampleUv + vec2(-7.5,  0.0) * texel) * 0.11;
        acc += extractBright(sampleUv + vec2( 7.5,  0.0) * texel) * 0.11;
        acc += extractBright(sampleUv + vec2(-2.2, -2.2) * texel) * 0.115;
        acc += extractBright(sampleUv + vec2( 2.2, -2.2) * texel) * 0.115;
        acc += extractBright(sampleUv + vec2(-2.2,  2.2) * texel) * 0.115;
        acc += extractBright(sampleUv + vec2( 2.2,  2.2) * texel) * 0.115;
        vec3 haloTint = mix(vec3(0.76, 0.86, 1.0), vec3(0.99, 0.94, 0.82), 0.32);
        color += acc * haloTint * (BloomBoost * 0.46);
    }

    float flowLuma = 0.0;
    if (AscalonAura > 0.002 || SyzygyFlash > 0.002) {
        vec2 materialUv = mirrorFlow(texCoord * vec2(1.4, 0.95) + vec2(PulseTime / 7.5, -PulseTime * 0.18));
        flowLuma = dot(texture(AscalonFlowSampler, materialUv).rgb, vec3(0.2126, 0.7152, 0.0722));
    }

    if (Impact > 0.002) {
        vec2 offset = (texCoord - focus) * Impact * 0.034;
        vec3 a = texture(DiffuseSampler, clamp(sampleUv - offset, 0.001, 0.999)).rgb;
        vec3 b = texture(DiffuseSampler, clamp(sampleUv + offset * 0.55, 0.001, 0.999)).rgb;
        color = mix(color, vec3(a.r, (a.g + b.g) * 0.5, b.b), 0.46 * Impact);
    }

    if (Freeze > 0.0) {
        float depth = texture(DepthSampler, sampleUv).r;
        if (depth < 0.999999) {
            mat4 invView = mat4(Inv0, Inv1, Inv2, Inv3, Inv4, Inv5, Inv6, Inv7,
                                Inv8, Inv9, Inv10, Inv11, Inv12, Inv13, Inv14, Inv15);
            vec4 scene = invView * vec4(sampleUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec3 point = scene.xyz / scene.w;
            float distanceInField = length(point - vec3(CenterX, CenterY, CenterZ));
            float mask = (1.0 - smoothstep(39.2, 40.0, distanceInField)) * Freeze;
            float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
            vec3 cooled = color * vec3(0.74, 0.83, 1.0) + luma * vec3(0.035, 0.045, 0.095);
            vec3 local = point - vec3(CasterX, CasterY, CasterZ);
            float star = (1.0 - smoothstep(0.55, 0.9, length(local.xz))) * step(0.15, local.y) * step(local.y, 2.9);
            cooled += vec3(0.035, 0.08, 0.135) * star * smoothstep(0.52, 0.92, luma);
            color = mix(color, cooled, mask * 0.69);
            color += vec3(0.09, 0.12, 0.21) * (1.0 - smoothstep(0.1, 0.75, abs(distanceInField - 40.0))) * Freeze;
        }
    }

    // 3. Pre-Burst Vacuum Contrast Dimming OR 1-2 Frame Sakuga Radial Impact Frame!
    if (SakugaFlash < -0.005) {
        float vac = clamp(-SakugaFlash, 0.0, 1.0);
        float edgeDark = smoothstep(0.18, 0.95, rDist) * vac;
        color *= (1.0 - edgeDark * 0.34);
        // Subtle inward convergence lines toward focal point
        float ang = atan(delta.y, delta.x);
        float inLines = pow(0.5 + 0.5 * sin(ang * 32.0 + sin(ang * 5.0) * 2.0), 16.0) * smoothstep(0.22, 0.85, rDist);
        color += vec3(0.78, 0.88, 1.0) * inLines * vac * 0.18;
    } else if (SakugaFlash > 0.008) {
        float sak = clamp(SakugaFlash, 0.0, 1.0);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float ang = atan(delta.y, delta.x);
        float speedRays = pow(0.5 + 0.5 * sin(ang * 38.0 + sin(ang * 7.0) * 2.6), 12.0) * smoothstep(0.10, 0.58, rDist);
        float slashCut = exp(-abs(delta.y * 0.92 + delta.x * 0.39) * 36.0) * smoothstep(0.95, 0.05, rDist);
        float hiContrast = smoothstep(0.38, 0.66, luma + speedRays * 0.34 + slashCut * 0.65);
        vec3 inkObsidian = vec3(0.035, 0.055, 0.115);
        vec3 ivoryGold = vec3(0.90, 0.70, 0.41);
        vec3 sakugaRgb = mix(inkObsidian, ivoryGold, hiContrast);
        color = mix(color, sakugaRgb, sak * 0.35);
    }

    // 4. Restrained Abyssal-Slate Vignette (darkens borders slightly to boost central contrast instead of washing out!)
    if (AscalonAura > 0.002) {
        vec2 edge = (texCoord - 0.5) * vec2(1.52, 1.42);
        float er = length(edge);
        float crown = smoothstep(0.60, 1.08, er) * AscalonAura;
        color = mix(color, color * vec3(0.72, 0.80, 0.94), crown * 0.55);
        color += vec3(0.74, 0.85, 0.98) * crown * flowLuma * 0.07;
    }

    // 5. Syzygy Eclipse Contrast & Ivory-Gold Horizon Wave
    if (SyzygyFlash > 0.002) {
        float ringPos = (1.0 - SyzygyFlash) * 1.05;
        float shockBand = exp(-pow((rDist - ringPos) * 14.0, 2.0)) * SyzygyFlash;
        vec3 eclipseTone = mix(color, color * vec3(0.75, 0.67, 0.88), SyzygyFlash * 0.36);
        color = eclipseTone + vec3(0.77, 0.51, 0.29) * shockBand * (0.30 + 0.11 * flowLuma);
    }

    if (Blood > 0.0) {
        vec2 edge = (texCoord - 0.5) * vec2(1.58, 1.42);
        float radius = length(edge);
        float heartbeat = 0.85 + 0.15 * pow(max(0.0, sin(PulseTime * 9.0)), 5.0);
        float shade = smoothstep(0.44, 0.94, radius) * Blood * heartbeat;
        float angle = atan(edge.y, edge.x);
        float filaments = pow(0.5 + 0.5 * sin(angle * 27.0 + radius * 16.0 + sin(angle * 5.0) * 3.0), 14.0);
        color = mix(color, vec3(0.16, 0.013, 0.034), shade * 0.83);
        color += vec3(0.22, 0.025, 0.047) * filaments * shade * smoothstep(0.63, 0.91, radius);
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
