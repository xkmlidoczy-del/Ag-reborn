#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Source glAlphaFunc(GL_GREATER, .05F): equality is discarded too.
    if (color.a <= 0.05) discard;
    fragColor = color;
}
