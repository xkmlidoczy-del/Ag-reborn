#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FadeEnd;
in vec2 texCoord0;
in vec4 vertexColor;
in float viewDepth;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    // Keep the original position_tex_color cutoff BEFORE the new alpha fade.
    // A second cutoff afterwards would turn the smooth transition into a step.
    if (color.a < 0.1) discard;
    fragColor = color * ColorModulator;
    fragColor.a *= smoothstep(0.12, FadeEnd, viewDepth);
}
