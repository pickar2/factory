#version 460

layout(location = 0) in vec2 fragTexCoord;
layout(location = 1) in vec3 fragColor;
layout(location = 2) in flat int texIndex;

layout(binding = 1) uniform sampler samp;
layout(binding = 2) uniform texture2D textures[texture_count];

layout(location = 0) out vec4 outColor;

void main() {
	vec4 texture = texture(sampler2D(textures[texIndex], samp), fragTexCoord);
	if (texture.a == 0) {
		discard;
	}
	outColor = vec4(fragColor * texture.rgb, texture.a);
}