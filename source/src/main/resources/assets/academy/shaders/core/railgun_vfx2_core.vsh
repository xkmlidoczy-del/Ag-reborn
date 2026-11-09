#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord0;
out vec4 vertexColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // UV.u contains only original per-quad UV + the beam's pause-aware age/1800.
    // No frame clock, uniform mutation or additional geometry enters this material.
    texCoord0 = UV0;
    vertexColor = Color;
}
