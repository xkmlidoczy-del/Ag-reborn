#version 150
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    // Analytic transverse shoulder exactly once; no opaque core or noise modulation.
    float distanceFromAxis = abs(texCoord0.y * 2.0 - 1.0);
    float transverse = 1.0 - smoothstep(0.0, 1.0, distanceFromAxis);
    // The unchanged classic core already supplies its ivory luminance. Keep a
    // small continuous contribution there; spend optional light on the shoulder.
    // No hard inner cutout: the surface stays continuous at the core boundary.
    float coreProtection = 0.04 + 0.46 * smoothstep(0.25, 0.75, distanceFromAxis);
    // No texture is sampled. x is a constant per-quad material profile: launch=0,
    // offset decorative trail=1. The narrow trail needs its own continuous center.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * transverse * (texCoord0.x < 0.5 ? coreProtection : 1.0)) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
