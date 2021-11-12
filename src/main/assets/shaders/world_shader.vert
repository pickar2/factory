#version 460

#define BUFFER_SIZE 65536

#define TEXTURE_COUNT 256

#define SECTOR_SET_COUNT 1
#define SECTOR_SIZE 16
#define SECTOR_COUNT BUFFER_SIZE / SECTOR_SIZE
#define SECTOR_BITMASK SECTOR_COUNT - 1

#define MATERIALS_SET_COUNT_0 1
#define MATERIALS_SET_COUNT_1 1
#define MATERIALS_SET_COUNT_2 1
#define MATERIALS_SET_COUNT_3 1
#define MATERIALS_SET_COUNT_4 1

#define MATERIALS_SIZE_0 4
#define MATERIALS_SIZE_1 8
#define MATERIALS_SIZE_2 16
#define MATERIALS_SIZE_3 16
#define MATERIALS_SIZE_4 32

#define MATERIALS_COUNT_0 BUFFER_SIZE / MATERIALS_SIZE_0
#define MATERIALS_COUNT_1 BUFFER_SIZE / MATERIALS_SIZE_1
#define MATERIALS_COUNT_2 BUFFER_SIZE / MATERIALS_SIZE_2
#define MATERIALS_COUNT_3 BUFFER_SIZE / MATERIALS_SIZE_3
#define MATERIALS_COUNT_4 BUFFER_SIZE / MATERIALS_SIZE_4

#define MATERIALS_BITMASK_0 MATERIALS_COUNT_0 - 1
#define MATERIALS_BITMASK_1 MATERIALS_COUNT_1 - 1
#define MATERIALS_BITMASK_2 MATERIALS_COUNT_2 - 1
#define MATERIALS_BITMASK_3 MATERIALS_COUNT_3 - 1
#define MATERIALS_BITMASK_4 MATERIALS_COUNT_4 - 1

struct Material0 {
	int textureID;
};

struct Material1 {
	int textureID;
	int color;
};

struct Material2 {
	int texture1ID;
	int color1;
	int texture2ID;
	int color2;
};

struct Material3 {
	int textureID;
	int color;
	int texturePosX_texturePosY;
	int textureSizeX_textureSizeY;
};

struct Material4 {
	int texture1ID;
	int color1;
	int texture1PosX_texture1PosY;
	int texture1SizeX_texture1SizeY;

	int texture2ID;
	int color2;
	int texture2PosX_texture2PosY;
	int texture2SizeX_texture2SizeY;
};

/*
	int16 posX, posY,
	int16 posZ, materialID
	int32 data1: 3 bit materialType, 1 bit colorType, 2 bit ao, 6 bit skylight, 2 bit vertexIndex //, texture rotation, texture mirroring, 20 bits free
	int32 data2: 24 bit rgb light color, 8 bit light intensivity
*/

layout(location = 0) in int posX_posY;
layout(location = 1) in int posZ_materialID;
layout(location = 2) in int data1;
layout(location = 3) in int data2;

readonly layout(set = 0, binding = 0) uniform ViewProj {
	mat4 proj;
	mat4 view;
	vec3 skylightColor;
	int skylightIntensivity;
};

//readonly layout(set = 0, binding = 1) uniform sampler samp;

readonly layout(set = 1, binding = 0) uniform SectorPositions {
	ivec4 pos[SECTOR_COUNT];
} sectors[SECTOR_SET_COUNT];

readonly layout(set = 2, binding = 0) uniform Materials0 {
	Material0 array[MATERIALS_COUNT_0];
} materials0[MATERIALS_SET_COUNT_0];

readonly layout(set = 3, binding = 0) uniform Materials1 {
	Material1 array[MATERIALS_COUNT_1];
} materials1[MATERIALS_SET_COUNT_1];

readonly layout(set = 4, binding = 0) uniform Materials2 {
	Material2 array[MATERIALS_COUNT_2];
} materials2[MATERIALS_SET_COUNT_2];

readonly layout(set = 5, binding = 0) uniform Materials3 {
	Material3 array[MATERIALS_COUNT_3];
} materials3[MATERIALS_SET_COUNT_3];

readonly layout(set = 6, binding = 0) uniform Materials4 {
	Material4 array[MATERIALS_COUNT_4];
} materials4[MATERIALS_SET_COUNT_4];

readonly layout(set = 7, binding = 0) uniform sampler2D gradientTextures[TEXTURE_COUNT];

layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out vec3 fragColor;
layout(location = 2) out int texIndex;

int getLInt16(const int value) {
	return (value >> 16) & 0x0000FFFF;
}

int getRInt16(const int value) {
	return value & 0x0000FFFF;
}

ivec3 getSectorPos(const int setIndex, const int index) {
	const int indy = (index + 1);
	const int indz = (index + 2);

	return ivec3(sectors[setIndex].pos[index >> 2][index & 3], sectors[setIndex].pos[indy >> 2][indy & 3], sectors[setIndex].pos[indz >> 2][indz & 3]);
}

int getMaterialType(const int value) {
	return (value >> 29) & 8;
}

int getColorType(const int value) {
	return (value >> 28) & 1;
}

int getAO(const int value) {
	return (value >> 26) & 4;
}

int getSkyLight(const int value) {
	return (value >> 24) & 64;
}

int getVertexIndex(const int value) {
	return (value >> 18) & 0x4;
}

ivec3 getColor(const int value) {
	return ivec3((value >> 24) & 0xFF, (value >> 16) & 0xFF, (value >> 8) & 0xFF);
}

int getLightIntensivity(const int value) {
	return value & 0xFF;
}

vec4 getRGBAFromInt(const int colorValue) {
	return vec4((value >> 24) & 0xFF, (value >> 16) & 0xFF, (value >> 8) & 0xFF, colorValue & 0xFF);
}

vec4 getRGBAFromColorMap(const int colorMapValue) {
	const int textureID = (colorMapValue >> 20) & 0xFFF;
	const ivec2 pixelCoordinates = ivec2((colorMapValue >> 10) & 0x3FF, colorMapValue & 0x3FF);

	return texelFetch(gradientTextures[textureID], pixelCoordinates);
}

vec4 parseColor(const int colorValue) {
	const int colorType = getColorType(data1);
	if (colorType == 0) {
		return getRGBAFromColorMap(colorValue);
	} else {
		return getRGBAFromInt(colorValue);
	}
}

void parseMaterial0(const int materialID, out fragTexCoord, out fragColor, out texIndex) {
	fragTexCoord = defaultTexturePos[getVertexIndex(data1)];
	fragColor = vec3(getAo(vertexInfo)); // do color stuff
	texIndex = materials0[materialID / MATERIALS_COUNT_0].array[materialID & MATERIALS_BITMASK_0].textureID;
}

void parseMaterial1(const int materialID, out fragTexCoord, out fragColor, out texIndex) {
	const Material1 mat = materials1[materialID / MATERIALS_COUNT_1].array[materialID & MATERIALS_BITMASK_1];

	fragTexCoord = defaultTexturePos[getVertexIndex(data1)];
	fragColor = getAo(vertexInfo) * parseColor(mat.color); // do color stuff
	texIndex = mat.textureID;
}

const float[4] ao = { 1, 0.75, 0.55, 0.25 };
const vec2[] defaultTexturePos = { vec2(0, 0), vec2(1, 0), vec2(0, 1), vec2(1, 1) };

void main() {
	const ivec3 vertexPos = getSectorPos(gl_InstanceIndex / SECTOR_COUNT, gl_InstanceIndex & SECTOR_BITMASK) +
	ivec3(getLInt16(posX_posY), getRInt16(getLInt16), getLInt16(posZ_materialID));
	gl_Position = proj * view  * vec4(vertexPos, 1.0);

	// defaults, to be removed
	fragTexCoord = defaultTexturePos[getVertexIndex(data1)];
	fragColor = vec3(getAo(vertexInfo));
	texIndex = 0;

	// must be 5 (or even 10) different shaders
	const int materialType = getMaterialType(data1);
	switch (materialType) {
		case 0: {
			parseMaterial0(getRInt16(posZ_materialID), fragTexCoord, fragColor, texIndex);
			break;
		}
		case 1: {
			parseMaterial1(getRInt16(posZ_materialID), fragTexCoord, fragColor, texIndex);
			break;
		}
		case 2: {
			break;
		}
		case 3: {
			break;
		}
		case 4: {
			break;
		}
	}
}