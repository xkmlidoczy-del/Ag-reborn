#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main(){
    // Original ACA stroke texture supplies its transverse profile once.
    // Keep the low-alpha shoulder and near-eye fade instead of a hard 0.1 cutoff.
    fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;
    if(fragColor.a<0.001)discard;
}
