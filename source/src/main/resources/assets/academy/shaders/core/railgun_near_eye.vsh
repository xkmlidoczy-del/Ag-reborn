#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord0;
out vec4 vertexColor;
out float viewDepth;
void main() {
    // Position is already camera-relative (double subtraction on the CPU), then
    // transformed by the caller's pose. Use exactly the same view transform as
    // projection; do not subtract a world camera or apply the pose a second time.
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewDepth = -view.z;
    texCoord0 = UV0;
    vertexColor = Color;
}
