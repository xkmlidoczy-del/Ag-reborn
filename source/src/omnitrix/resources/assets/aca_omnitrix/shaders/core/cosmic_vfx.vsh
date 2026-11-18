#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float vfxMode;
out float vfxErosion;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color;
    texCoord0 = UV0;
    // UV1.x: shader mode (0..6), UV1.y: fixed-point [0..1000] erosion / dissolution progress
    vfxMode = float(UV1.x);
    vfxErosion = clamp(float(UV1.y) * 0.001, 0.0, 1.0);
}
