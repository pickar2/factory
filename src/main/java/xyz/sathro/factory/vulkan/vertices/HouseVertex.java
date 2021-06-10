package xyz.sathro.factory.vulkan.vertices;

import org.joml.Vector2f;
import org.joml.Vector3f;
import xyz.sathro.vulkan.models.VulkanVertexInfo;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class HouseVertex implements IVertex {
	public static final int SIZEOF = (3 + 3 + 2) * Float.BYTES;
	public static final int OFFSETOF_POS = 0;
	public static final int OFFSETOF_COLOR = 3 * Float.BYTES;
	public static final int OFFSETOF_TEXTCOORDS = (3 + 3) * Float.BYTES;

	public static final VulkanVertexInfo vertexInfo;
	public Vector3f pos;
	public Vector3f color;
	public Vector2f texCoords;

	static {
		vertexInfo = VulkanVertexInfo.builder()
				.startBinding(0, SIZEOF, VK_VERTEX_INPUT_RATE_VERTEX)
				 .addAttribute(0, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_POS)
				 .addAttribute(1, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_COLOR)
				 .addAttribute(2, VK_FORMAT_R32G32_SFLOAT, OFFSETOF_TEXTCOORDS)
				.endBinding()
				.build();

		vertexInfo.registerToLateDisposal();
	}

	public HouseVertex() { }

	public HouseVertex(Vector3f pos, Vector3f color, Vector2f texCoords) {
		this.pos = pos;
		this.color = color;
		this.texCoords = texCoords;
	}

	@Override
	public VulkanVertexInfo getVertexInfo() {
		return vertexInfo;
	}

	@Override
	public void get(int index, ByteBuffer buffer) {
		pos.get(index + OFFSETOF_POS, buffer);
		color.get(index + OFFSETOF_COLOR, buffer);
		texCoords.get(index + OFFSETOF_TEXTCOORDS, buffer);
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
