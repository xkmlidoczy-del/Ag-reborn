#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // RenderIcon GL_GREATER0: transparent texels cannot write source depth.
    if (color.a <= 0.0) discard;
    fragColor = color;
}
