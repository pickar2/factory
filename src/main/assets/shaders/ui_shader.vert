#version 450

struct Data {
	int posX_posY;
	int posZ_textureID;
	int sizeX_sizeY;
	int clipPosX_clipPosY;
	int clipSizeX_clipSizeY;
	int color;
	int rounding;

	int null;
};

layout(location = 0) in int vertexIndex;

layout(location = 0) out int index;
layout(location = 1) out vec2 fragTexCoord;

readonly layout(set = 0, binding = 0) uniform projectionMatrix {
	mat4 proj;
};

readonly layout(set = 1, binding = 0) uniform dataArray {
	Data[8] data;
};

vec4 intToRGBA(const int value) {
	float b = value & 0xFF;
	float g = (value >> 8) & 0xFF;
	float r = (value >> 16) & 0xFF;
	float a = (value >> 24) & 0xFF;

	return vec4(r, g, b, a) / 255.0;
}

int getLInt16(const int value) {
	return (value >> 16) & 0x0000FFFF;
}

int getRInt16(const int value) {
	return value & 0x0000FFFF;
}

const vec2[] vertexPos = { vec2(0, 0), vec2(1, 0), vec2(0, 1), vec2(1, 1) };

mat4 scalePos = mat4(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1);

void main() {
	const Data d = data[gl_InstanceIndex];

	scalePos[0][0] = getLInt16(d.sizeX_sizeY);
	scalePos[1][1] = getRInt16(d.sizeX_sizeY);

	scalePos[3][0] = getLInt16(d.posX_posY);
	scalePos[3][1] = getRInt16(d.posX_posY);
	scalePos[3][2] = getLInt16(d.posZ_textureID)/65536.0-0.01;

	gl_Position = proj * scalePos * vec4(vertexPos[vertexIndex], 0, 1.0);

	index = gl_InstanceIndex;
	fragTexCoord = vertexPos[vertexIndex];
}