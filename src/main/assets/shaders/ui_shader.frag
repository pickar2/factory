#version 450

struct Data {
	int posX_posY;
	int posZ_textureID;
	int sizeX_sizeY;
	int clipPosX_clipPosY;
	int clipSizeX_clipSizeY;
	int color;
	int rounding_clipRounding;

	int null;
};

layout(location = 0) in flat int index;
layout(location = 1) in vec2 fragTexCoord;

layout(location = 0) out vec4 outColor;

readonly layout(set = 1, binding = 0) uniform dataArray {
	Data[2048] data;
};

layout(set = 2, binding = 0) uniform sampler2D textures[256];

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

float getRoundingVecSize(const vec2 currentCoord, const vec2 pos, const vec2 size, const int rounding) {
	return length(max(abs(currentCoord - pos - size / 2) + (rounding - size) / 2, 0.0)) - rounding / 2;
}

const int HALF_INT16 = 32768;

void main() {
	const Data d = data[index];
	outColor = intToRGBA(d.color);

	const int textureID = getRInt16(d.posZ_textureID)-1;
	if (textureID != -1) {
		outColor *= texture(textures[textureID], fragTexCoord);
	}
	if (outColor.a == 0) {
		discard;
	}

	const vec2 pos = vec2(getLInt16(d.posX_posY), getRInt16(d.posX_posY)) - HALF_INT16;
	const vec2 size = vec2(getLInt16(d.sizeX_sizeY), getRInt16(d.sizeX_sizeY));

	const vec2 clipPos = vec2(getLInt16(d.clipPosX_clipPosY), getRInt16(d.clipPosX_clipPosY)) + pos - HALF_INT16;
	const vec2 clipSize = vec2(getLInt16(d.clipSizeX_clipSizeY), getRInt16(d.clipSizeX_clipSizeY));

	const vec2 currentCoord = vec2(fragTexCoord.x * size.x, fragTexCoord.y * size.y) + pos;

	const int rounding = getLInt16(d.rounding_clipRounding);
	const int clipRounding = getRInt16(d.rounding_clipRounding);

	if (currentCoord.x > clipPos.x && currentCoord.y > clipPos.y && currentCoord.x <= clipPos.x + clipSize.x && currentCoord.y <= clipPos.y + clipSize.y) {
		if (clipRounding > 0) {
			const float f = getRoundingVecSize(currentCoord, clipPos, clipSize, clipRounding);
			if (f < 0.0) {
				discard;
			}
		} else {
			discard;
		}
	}
	if (rounding > 0) {
		const float f = getRoundingVecSize(currentCoord, pos, size, rounding);
		if (f > 0.0) {
			discard;
		}
	}
}