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
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPosition;

out vec4 fragColor;

const float TAU = 6.28318530718;

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// Keep all exit values explicit for Veil's GLSL processor.
vec4 shadeFragment() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.02) {
        discard;
    }

    // Mask check: a < 0.5 means masked region (eyes, badge, white hands)
    if (vertexColor.a < 0.5) {
        vec4 color = texColor * ColorModulator;
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightMapColor;
        return linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    }

    // Bind-pose model coordinates decoded from vertexColor.rgb
    vec3 mPos = vertexColor.rgb * vec3(64.0, 48.0, 64.0) - vec3(32.0, 4.0, 32.0);

    float speed = (ReducedMotion > 0.5) ? 0.25 : 1.0;
    float t = StarfieldSeconds * speed;

    // --- 1. Upward Flowing Deep Blue-Violet Nebula (12s cycle) ---
    float ny = mPos.y * 0.14 - (t / 12.0) * TAU;
    float n1 = sin(mPos.x * 0.20 + ny + sin(mPos.z * 0.31) * 0.8);
    float n2 = cos(mPos.z * 0.22 - ny * 2.0 + sin(mPos.x * 0.21));
    float n3 = sin((mPos.x - mPos.z) * 0.16 + ny * 2.0);
    float nebula = smoothstep(-0.31, 0.85, n1 * 0.55 + n2 * 0.30 + n3 * 0.15);
    float upperLight = smoothstep(4.0, 29.0, mPos.y);
    if (ReducedMotion > 0.5) {
        nebula *= 0.67;
    }

    vec3 obsidian = vec3(0.028, 0.034, 0.071);
    vec3 violetGlow = vec3(0.261, 0.156, 0.420);
    vec3 blueGlow = vec3(0.102, 0.222, 0.434);
    vec3 coreColor = mix(violetGlow, blueGlow, smoothstep(-0.68, 0.68, n3));
    vec3 baseColor = mix(obsidian, coreColor, nebula * (0.59 + 0.25 * upperLight));
    baseColor *= .85 + .31 * dot(texColor.rgb, vec3(.2126, .7152, .0722));

    // --- 2. Far Layer Small Stars (24s cycle) ---
    float farT = t / 24.0;
    vec3 farCoord = (mPos * 0.75) + vec3(sin(farT * TAU) * 0.5, -farT * 12.0, cos(farT * TAU) * 0.5);
    vec3 farCell = floor(farCoord);
    vec3 farFrac = fract(farCoord) - 0.5;
    vec3 farH = hash33(vec3(farCell.x, mod(farCell.y, 12.0), farCell.z));
    float farDist = length(farFrac - (farH - 0.5) * 0.65);
    float farStar = 1.0 - smoothstep(0.030, 0.127, farDist);
    vec3 farStarCol = mix(vec3(0.86, 0.91, 1.0), vec3(0.58, 0.72, 0.98), farH.x) * farStar * 0.73;

    // --- 3. Near Layer Brighter Stars with Twinkle (8s cycle) ---
    float nearT = t / 8.0;
    vec3 nearCoord = (mPos * 0.42) + vec3(sin(nearT * TAU) * 0.46, -nearT * 8.0, cos(nearT * TAU) * 0.43);
    vec3 nearCell = floor(nearCoord);
    vec3 nearFrac = fract(nearCoord) - 0.5;
    vec3 nearH = hash33(vec3(nearCell.x, mod(nearCell.y, 8.0), nearCell.z));
    float nearDist = length(nearFrac - (nearH - 0.5) * 0.60);
    float nearStar = 1.0 - smoothstep(0.044, 0.162, nearDist);
    float twinkle = (ReducedMotion > 0.5) ? 1.0 : (0.82 + 0.18 * sin(nearT * TAU + nearH.y * TAU));
    vec3 nearStarCol = mix(vec3(0.81, 0.90, 1.0), vec3(1.0, 0.91, 0.80), nearH.z)
            * (nearStar * twinkle * 1.10);

    // --- 4. Cool Edge Silhouette Lighting ---
    vec3 viewDir = normalize(-viewPosition);
    vec3 norm = normalize(viewNormal);
    float rim = 1.0 - clamp(abs(dot(viewDir, norm)), 0.0, 1.0);
    rim = pow(rim, 2.6) * (0.30 + 0.12 * upperLight);
    vec3 rimCol = vec3(0.43, 0.55, 0.81) * rim;

    // Composite starfield
    vec3 litStar = baseColor + farStarCol + nearStarCol + rimCol;

    // Softly mix lightmap so the body is emissive in the dark but integrates with world light
    litStar *= (0.82 + 0.18 * lightMapColor.rgb);

    // Preserve original texture transparency (e.g. wings, vapor tail)
    vec4 finalColor = vec4(litStar, texColor.a * ColorModulator.a);
    finalColor.rgb = mix(overlayColor.rgb, finalColor.rgb, overlayColor.a);

    return linear_fog(finalColor, vertexDistance, FogStart, FogEnd, FogColor);
}

void main() {
    fragColor = shadeFragment();
}
