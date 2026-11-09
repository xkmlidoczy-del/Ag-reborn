#version 150
// Canon FX4 (SH2 noise dissolve with a bright edge). Presentation only; see client/canon/CanonFx4.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// GameTime is the day fraction (1/24000 per tick); this converts it to seconds.
float seconds() { return GameTime * 1200.0; }

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    // Vertex alpha carries the dissolve amount (0 = whole, 1 = gone); Sampler1 is common_noise_tile (linear, use R).
    float amount = vertexColor.a;
    float n = texture(Sampler1, texCoord0 * 1.7).r;
    float cut = amount * 1.08 - 0.04;
    if (n < cut) discard;
    float edge = (1.0 - smoothstep(cut, cut + 0.08, n)) * step(0.001, amount);
    vec3 rgb = mix(tex.rgb * vertexColor.rgb, min(vec3(1.0), vertexColor.rgb * 0.7 + 0.55), edge);
    fragColor = vec4(rgb, tex.a) * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
