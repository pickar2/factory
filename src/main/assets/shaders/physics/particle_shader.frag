#version 450

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
	vec3 N;
	N.xy = gl_PointCoord * 2.0 - vec2(1.0);
	float mag = dot(N.xy, N.xy);
	if (mag > 1.0) discard;// kill pixels outside circle
	N.z = sqrt(1.0 - mag);

	// calculate lighting
	float diffuse = max(0.5, dot(vec3(0, -1, 0), N));

	outColor = vec4(fragColor * diffuse, 1);
}