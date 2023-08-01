#version 450

layout(binding = 0) uniform scene {
	mat4 view;
	mat4 proj;
//	vec3 lightingPos;
};

layout(binding = 1) uniform models {
	mat4 model;
};

const vec3 lightPos = vec3(0, 30, 0);

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;
layout(location = 2) in vec3 normal;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec3 fragNormal;
layout(location = 2) out vec3 lightDirection;
layout(location = 3) out float distanceSquared;

void main() {
	gl_Position = proj * view * model * vec4(position, 1.0);
	fragColor = color;
	fragNormal = normalize((view * model * vec4(normal, 0)).xyz);

	vec3 vertexPosition_cameraspace = ( view * model * vec4(position, 1)).xyz;
	vec3 eyeDir = -vertexPosition_cameraspace;
	vec3 LightPosition_cameraspace = (view * vec4(lightPos, 1)).xyz;

	lightDirection = normalize(eyeDir);

	vec3 distance = vertexPosition_cameraspace - eyeDir;
	distanceSquared = 1 / dot(distance, distance);
}