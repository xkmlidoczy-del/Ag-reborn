#version 150
// AcademyCraft 1.0.7 cpbar_overload.frag; fract explicitly preserves legacy repeat wrapping.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float TexOffset;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 colorTex = vertexColor * ColorModulator * texture(Sampler0, vec2(fract(texCoord0.x + TexOffset),texCoord0.y));
    fragColor = vec4(colorTex.rgb,texture(Sampler1,texCoord0).a * colorTex.a);
}
