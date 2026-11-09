#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float SwirlGain;
uniform float FlowAmount;
uniform float VisualPhase;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 source = texture(Sampler0, texCoord0);
    vec3 artworkRgb = source.rgb;
    // SHA-pinned artwork has a row125->126 RGB edge. Feather only this 16-texel band.
    // The reference contributes RGB only; coverage always uses the unshifted source alpha.
    if (texCoord0.y > 124.5 / 512.0 && texCoord0.y < 140.5 / 512.0) {
        vec3 referenceRgb = texture(Sampler0, vec2(texCoord0.x, 124.5 / 512.0)).rgb;
        float feather = smoothstep(124.5 / 512.0, 140.5 / 512.0, texCoord0.y);
        artworkRgb = mix(referenceRgb, source.rgb, feather);
    }
    // Outside the feather band the original base green disk remains unchanged.
    float paleSwirl = smoothstep(0.32, 0.82, min(artworkRgb.r, artworkRgb.b));
    // LOW sets zero flow uniformly and skips both trigonometric operations.
    float flow = 0.0;
    if (FlowAmount > 0.0) {
        // Periodic in both UV axes and phase; no texture offset, angle seam, or independent clock.
        flow = sin(6.28318530718 * (2.0 * texCoord0.x + texCoord0.y - VisualPhase))
             * cos(6.28318530718 * (texCoord0.y + VisualPhase));
    }
    float uplift = clamp(SwirlGain, 0.0, 0.28) * paleSwirl * (1.0 + clamp(FlowAmount, 0.0, 0.18) * flow);
    vec3 swirlRgb = mix(artworkRgb, vec3(1.0), uplift);
    // Original unshifted alpha, vertex color, and global modulation each enter exactly once.
    vec4 color = vec4(swirlRgb, source.a) * vertexColor * ColorModulator;
    if (color.a <= 0.05) discard;
    fragColor = color;
}
