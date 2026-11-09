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
    // Vanilla position_tex_color tests texture * vertex alpha before modulation.
    if (color.a < 0.1) discard;
    float visibility = smoothstep(0.12, FadeEnd, viewDepth);
    // The classic material writes depth. A zero-alpha fragment must not occlude.
    if (visibility <= 0.0) discard;
    fragColor = color * ColorModulator;
    fragColor.a *= visibility;
}
