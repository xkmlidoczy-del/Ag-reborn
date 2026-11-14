#version 150
#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec2 localUV;
out vec4 tint;
out float distanceToEye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localUV = UV0;
    tint = Color;
    distanceToEye = fog_distance(Position, FogShape);
}
