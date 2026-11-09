#version 150
// Canon FX4 (SH3 fluid surface: two-phase flow-map distortion). Presentation only; see client/canon/CanonFx4.
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
    // Sampler1 is common_flow_map: RG decoded with rg*2-1. Two half-offset phases hide the reset of each cycle.
    vec2 flow = texture(Sampler1, texCoord0 * vec2(0.35, 1.0) + vec2(t * 0.03, 0.0)).rg * 2.0 - 1.0;
    float p0 = fract(t * 0.6), p1 = fract(t * 0.6 + 0.5);
    vec4 a = texture(Sampler0, texCoord0 + flow * 0.09 * p0);
    vec4 b = texture(Sampler0, texCoord0 + flow * 0.09 * p1);
    vec4 c = mix(a, b, abs(1.0 - 2.0 * p0));
    // Moving glints where the flow converges.
    float glint = pow(max(0.0, 1.0 - length(flow)), 6.0) * 0.6;
    c.rgb += glint * c.a;
    fragColor = c * vertexColor * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
