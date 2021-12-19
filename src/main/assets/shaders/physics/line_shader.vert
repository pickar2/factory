#version 450

layout(binding = 0) uniform viewproj {
    mat4 view;
    mat4 proj;
};

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = proj * view * vec4(position, 1.0);
    fragColor = color;
}