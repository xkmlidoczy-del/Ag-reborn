#version 150
// LambdaLib 1.2.3 ShaderMono arithmetic, not luminance-weighted greyscale.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 result = vertexColor * ColorModulator * texture(Sampler0,texCoord0);
    float value = (result.r + result.g + result.b) / 3.0;
    fragColor = vec4(value,value,value,result.a);
}
