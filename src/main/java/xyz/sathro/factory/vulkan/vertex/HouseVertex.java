package xyz.sathro.factory.vulkan.vertex;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class HouseVertex implements IVertex {
	public static final int SIZEOF = (3 + 3 + 2) * Float.BYTES;
	public static final int OFFSETOF_POS = 0;
	public static final int OFFSETOF_COLOR = 3 * Float.BYTES;
	public static final int OFFSETOF_TEXTCOORDS = (3 + 3) * Float.BYTES;

	private static final VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1);
	private static final VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(3);
	public Vector3f pos;
	public Vector3f color;
	public Vector2f texCoords;

	static {
		bindingDescription.get(0)
				.binding(0)
				.stride(HouseVertex.SIZEOF)
				.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		// Position
		VkVertexInputAttributeDescription posDescription = attributeDescriptions.get(0);
		posDescription.binding(0);
		posDescription.location(0);
		posDescription.format(VK_FORMAT_R32G32B32_SFLOAT);
		posDescription.offset(OFFSETOF_POS);

		// Color
		VkVertexInputAttributeDescription colorDescription = attributeDescriptions.get(1);
		colorDescription.binding(0);
		colorDescription.location(1);
		colorDescription.format(VK_FORMAT_R32G32B32_SFLOAT);
		colorDescription.offset(OFFSETOF_COLOR);

		// Texture coordinates
		VkVertexInputAttributeDescription texCoordsDescription = attributeDescriptions.get(2);
		texCoordsDescription.binding(0);
		texCoordsDescription.location(2);
		texCoordsDescription.format(VK_FORMAT_R32G32_SFLOAT);
		texCoordsDescription.offset(OFFSETOF_TEXTCOORDS);
	}

	public HouseVertex() { }

	public HouseVertex(Vector3f pos, Vector3f color, Vector2f texCoords) {
		this.pos = pos;
		this.color = color;
		this.texCoords = texCoords;
	}

	@Override
	public VkVertexInputBindingDescription.Buffer getBindingDescription() {
		return bindingDescription;
	}

	@Override
	public VkVertexInputAttributeDescription.Buffer getAttributeDescriptions() {
		return attributeDescriptions;
	}

	@Override
	public void get(ByteBuffer buffer) {
		buffer.putFloat(pos.x());
		buffer.putFloat(pos.y());
		buffer.putFloat(pos.z());

		buffer.putFloat(color.x());
		buffer.putFloat(color.y());
		buffer.putFloat(color.z());

		buffer.putFloat(texCoords.x());
		buffer.putFloat(texCoords.y());
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public IVertex copy() {
		return new HouseVertex(pos, color, texCoords);
	}
}
