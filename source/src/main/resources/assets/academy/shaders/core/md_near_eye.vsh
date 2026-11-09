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
    // CPU vertices already contain camera-relative position and the caller pose.
    // Fade and projection share exactly one final ModelView transform.
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    // Preserve vanilla's multiplication order for the projected geometry.
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewDepth = -view.z;
    texCoord0 = UV0;
    vertexColor = Color;
}
