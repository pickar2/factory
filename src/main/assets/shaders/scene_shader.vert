#version 450

layout(binding = 0) uniform viewproj {
    mat4 view;
    mat4 proj;
} vp;

layout(binding = 1) uniform models {
    mat4 model[4];
};

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = vp.proj * vp.view * model[gl_InstanceIndex] * vec4(inPosition, 1.0);
    fragColor = inColor;
}