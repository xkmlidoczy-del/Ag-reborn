#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FadeEnd;
in vec2 texCoord0;
in vec4 vertexColor;
in float viewDepth;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Classic MD's strict GL_GREATER .05 includes ColorModulator.
    if (color.a <= 0.05) discard;
    float visibility = smoothstep(0.12, FadeEnd, viewDepth);
    // Discard only zero visibility; no new positive-alpha cutoff or depth wall.
    if (visibility <= 0.0) discard;
    fragColor = color;
    fragColor.a *= visibility;
}
