#version 150
// Canon FX4 (SH6 hologram: scan lines, flicker and a moving sweep band). Presentation only; see client/canon/CanonFx4.
uniform sampler2D Sampler0;
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
    vec4 c = texture(Sampler0, texCoord0) * vertexColor;
    float scan = 0.72 + 0.28 * sin(gl_FragCoord.y * 1.4 - t * 24.0);
    float sweep = smoothstep(0.96, 1.0, fract(gl_FragCoord.y / max(1.0, ScreenSize.y) * 3.0 - t * 0.7));
    float flicker = 0.92 + 0.08 * sin(t * 47.0);
    c.a *= scan * flicker;
    c.rgb *= 1.0 + 0.8 * sweep;
    fragColor = c * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
