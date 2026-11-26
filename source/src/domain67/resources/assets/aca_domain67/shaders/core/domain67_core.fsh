#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float DomainTime;
uniform float ReducedMotion;

in vec4 vertexColor;
in vec2 texCoord0;
in float vfxMode;
in float vfxPhase;
in vec3 vViewNormal;
in vec3 vViewDir;

out vec4 fragColor;

void main() {
    float t = DomainTime * (ReducedMotion > 0.5 ? 0.25 : 1.0);
    float ndv = abs(dot(normalize(vViewNormal), normalize(vViewDir)));
    float fresnel = pow(1.0 - clamp(ndv, 0.0, 1.0), 2.2);

    vec4 s0 = texture(Sampler0, fract(texCoord0 * vec2(3.0, 2.0) + vec2(t * 0.07, -t * 0.05)));
    vec4 s1 = texture(Sampler0, fract(texCoord0 * vec2(5.0, 4.0) - vec2(t * 0.12, t * 0.08)));

    vec3 outCol = vertexColor.rgb;
    float outAlpha = vertexColor.a;

    if (vfxMode < 0.5) {
        // Mode 0: JJK Exterior Top-Down Curtain ("帐") with Brushed Champagne-Gold Filigree Rim!
        if (vfxPhase <= 0.505) {
            float sealProg = clamp(vfxPhase * 2.0, 0.0, 1.0);
            float drip = 0.035 * sin(texCoord0.x * 36.0 + t * 4.5) + 0.02 * cos(texCoord0.x * 64.0 - t * 2.8);
            float cutoffY = (1.0 - sealProg) + drip * (1.0 - sealProg);
            if (texCoord0.y < cutoffY) discard;

            float leadingEdge = 1.0 - smoothstep(cutoffY, cutoffY + 0.08, texCoord0.y);
            float hexSeam = smoothstep(0.16, 0.02, s0.r) * (0.55 + 0.45 * sin(t * 3.5 + texCoord0.x * 18.0));
            vec3 obsidianCurtain = vec3(0.035, 0.025, 0.020);
            vec3 goldFiligree = vec3(0.96, 0.80, 0.38) * (fresnel * 1.15 + hexSeam * 0.50);
            vec3 sealFire = vec3(1.0, 0.92, 0.56) * (leadingEdge * 1.7 + pow(fresnel, 2.5) * 0.55);
            outCol = obsidianCurtain + goldFiligree + sealFire;
            outAlpha = clamp(0.96 + leadingEdge * 0.04, 0.0, 0.99);
        } else {
            float shatter = clamp((vfxPhase - 0.5) * 2.0, 0.0, 1.0);
            float cellVal = s0.r;
            if (cellVal < shatter * 1.12 - 0.12) discard;
            float rim = 1.0 - smoothstep(shatter * 1.12 - 0.12, shatter * 1.12 + 0.08, cellVal);
            vec3 crackFire = mix(vec3(0.98, 0.80, 0.34), vec3(1.0, 0.97, 0.86), rim);
            outCol = mix(vec3(0.04, 0.03, 0.02), crackFire * 1.6, rim);
            outAlpha = clamp(0.95 * (1.0 - shatter * 0.3), 0.0, 0.99);
        }
    } else if (vfxMode < 2.5) {
        // Mode 2: Gilded Ivory-Gold Spatial Rift Portal (for Cursed Log spawns)
        vec2 c = texCoord0 * 2.0 - 1.0;
        float r = length(c);
        float swirl = sin(atan(c.y, c.x) * 6.0 - t * 8.0 + s1.g * 3.5);
        float ring = smoothstep(0.95, 0.45, r) * smoothstep(0.18, 0.58, r + swirl * 0.09);
        float core = smoothstep(0.46, 0.0, r);
        outCol = mix(vec3(0.14, 0.08, 0.03), vec3(0.98, 0.80, 0.34), ring)
               + vec3(1.0, 0.96, 0.80) * pow(ring, 2.0) * 1.05;
        outAlpha *= clamp(core * 0.88 + ring * 0.96, 0.0, 1.0);
    } else if (vfxMode < 3.5) {
        // Mode 3: Crimson-Gold Cursed Log Shockwave & Lightning Sheath
        float bolt = max(s0.b, smoothstep(0.58, 0.90, s1.g)) * (0.7 + 0.3 * sin(t * 20.0 + texCoord0.x * 18.0));
        vec3 crimsonGold = mix(vec3(0.32, 0.08, 0.04), vec3(1.00, 0.48, 0.22), bolt);
        outCol = mix(crimsonGold, vec3(1.0, 0.92, 0.54), fresnel * 0.65 + bolt * 0.40);
        outAlpha *= clamp(0.40 + bolt * 0.55 + fresnel * 0.40, 0.0, 1.0);
    } else if (vfxMode < 4.5) {
        // Mode 4: Warm Champagne-Gold Halo & Broadcast Acoustic Wave
        float pulse = 0.72 + 0.28 * sin(t * 10.0 - texCoord0.x * 16.0);
        outCol = mix(vec3(0.98, 0.80, 0.34), vec3(1.0, 0.96, 0.82), s0.g) * pulse;
        outAlpha *= (1.0 - vfxPhase * 0.85) * (0.60 + 0.40 * s1.g);
    } else {
        // Mode 5: MJ 78-Yuan Bento Golden-Amber Sauce Wave
        float wave = 0.70 + 0.30 * sin(t * 8.0 + texCoord0.x * 14.0);
        outCol = mix(vec3(0.86, 0.62, 0.22), vec3(1.0, 0.94, 0.68), s0.g * 0.65 + fresnel * 0.5) * wave;
        outAlpha *= (1.0 - vfxPhase * 0.75) * 0.90;
    }

    if (outAlpha < 0.01) discard;
    fragColor = vec4(outCol, outAlpha) * ColorModulator;
}
