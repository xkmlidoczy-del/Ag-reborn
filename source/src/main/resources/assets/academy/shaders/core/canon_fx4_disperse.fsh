#version 150
// Canon FX4 (SH5 chromatic dispersion). Presentation only; see client/canon/CanonFx4.
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
    float t = seconds();
    vec2 screen = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec2 q = texCoord0 * 2.0 - 1.0;
    float mask = clamp(1.0 - dot(q, q), 0.0, 1.0);
    mask *= mask;
    // Sampler0 is a normal map or noise (linear); Sampler1 is a copy of the scene taken just before this batch.
    vec3 n = texture(Sampler0, texCoord0 * 0.9 + vec2(t * 0.025, t * 0.017)).rgb * 2.0 - 1.0;
    vec2 offset = n.xy * vertexColor.a * 0.04 * mask;
    vec3 col = vec3(texture(Sampler1, screen + offset * 1.35).r, texture(Sampler1, screen + offset).g, texture(Sampler1, screen + offset * 0.65).b);
    col += vertexColor.rgb * 0.12 * mask;
    fragColor = vec4(col, mask) * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
