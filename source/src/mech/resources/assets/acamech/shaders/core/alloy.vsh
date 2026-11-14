#version 150
#moj_import <fog.glsl>
#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform sampler2D Sampler2;

out vec3 viewPosition;
out vec3 viewNormal;
out vec3 keyLight;
out vec4 shadedColor;
out vec3 worldLight;
out vec2 texCoord;
out float distanceToEye;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPosition = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    keyLight = mat3(ModelViewMat) * Light0_Direction;
    shadedColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    worldLight = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    texCoord = UV0;
    distanceToEye = fog_distance(Position, FogShape);
}
