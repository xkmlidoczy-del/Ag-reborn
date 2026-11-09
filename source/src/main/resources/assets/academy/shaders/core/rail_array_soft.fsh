#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main(){
    // Original soft texture supplies the transverse profile once. The classic
    // 0.1 cutoff would erase most of this deliberately translucent shoulder.
    fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;
    if(fragColor.a<0.001)discard;
}
