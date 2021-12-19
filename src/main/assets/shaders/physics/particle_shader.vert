#version 450

layout(set = 0, binding = 0) uniform viewproj {
	mat4 view;
	mat4 proj;
	vec3 cameraPos;
};

layout(set = 0, binding = 1) uniform models {
	vec3 pos[1024];
};

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;
layout(location = 2) in float size;

layout(location = 0) out vec3 fragColor;

void main() {
	gl_PointSize = size - (distance(cameraPos, pos[gl_InstanceIndex]) / size);
	gl_Position = proj * view * vec4(pos[gl_InstanceIndex], 1);
	fragColor = color;
}