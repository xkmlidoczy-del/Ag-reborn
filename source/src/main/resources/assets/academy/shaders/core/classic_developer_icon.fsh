#version 150
// Modern equivalent of classic skill_back alpha-test stencil and ShaderMono arithmetic.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Monochrome;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    if(texture(Sampler1,(texCoord0*14.0+4.5)/23.0).a<=0.3)discard;
    vec4 pixel=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;
    float value=(pixel.r+pixel.g+pixel.b)/3.0;
    fragColor=vec4(mix(pixel.rgb,vec3(value),Monochrome),pixel.a);
}
