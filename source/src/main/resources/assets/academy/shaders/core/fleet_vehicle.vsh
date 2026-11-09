#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;
in vec3 Normal;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 PoseNormalMat;
uniform sampler2D Sampler2;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform int FogShape;
out vec2 surfaceUv;
out vec4 surfaceLight;
out float surfaceDistance;
void main(){
 vec4 viewPoint=ModelViewMat*vec4(Position,1.0);
 gl_Position=ProjMat*viewPoint;
 surfaceUv=UV0;
 surfaceLight=minecraft_mix_light(Light0_Direction,Light1_Direction,normalize(PoseNormalMat*Normal),Color)*texelFetch(Sampler2,UV2/16,0);
 surfaceDistance=fog_distance(viewPoint.xyz,FogShape);
}
