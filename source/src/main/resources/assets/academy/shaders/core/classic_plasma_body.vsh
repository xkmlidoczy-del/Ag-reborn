#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec3 camspace;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 cam = ModelViewMat * vec4(Position, 1.0);
    camspace = cam.xyz / cam.w;
}
