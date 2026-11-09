#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec2 surfaceUv;
in vec4 surfaceLight;
in float surfaceDistance;
out vec4 fragColor;
void main(){
 vec4 base=texture(Sampler0,surfaceUv);
 if(base.a<0.1)discard;
 fragColor=linear_fog(base*surfaceLight*ColorModulator,surfaceDistance,FogStart,FogEnd,FogColor);
}
