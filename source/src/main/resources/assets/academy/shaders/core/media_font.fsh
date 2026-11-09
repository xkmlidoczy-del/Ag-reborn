#version 150
// LambdaLib 1.2.3 TrueTypeFont.draw explicitly disables GL_ALPHA_TEST.
// Keep every antialiased/fading glyph fragment; plain text retains its original tint.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = texture(Sampler0,texCoord0) * vertexColor * ColorModulator;
}
