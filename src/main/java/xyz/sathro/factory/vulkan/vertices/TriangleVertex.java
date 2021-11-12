package xyz.sathro.factory.vulkan.vertices;

import org.joml.Vector3f;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import xyz.sathro.vulkan.models.VulkanVertexInfo;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;

public class TriangleVertex implements IVertex {
	public static final int SIZEOF = 3 * Float.BYTES;
	public static final int OFFSETOF_POS = 0;

	public static final VulkanVertexInfo vertexInfo;
	public Vector3f pos;

	static {
		VkVertexInputBindingDescription.Buffer bindingDescriptions = VkVertexInputBindingDescription.calloc(1)
				.binding(0)
				.stride(SIZEOF)
				.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(1)
				.binding(0)
				.location(0)
				.format(VK_FORMAT_R32G32B32_SFLOAT)
				.offset(OFFSETOF_POS);

		vertexInfo = new VulkanVertexInfo(attributeDescriptions, bindingDescriptions);

		vertexInfo.registerToLateDisposal();
	}

	public TriangleVertex() { }

	public TriangleVertex(Vector3f pos) {
		this.pos = pos;
	}

	@Override
	public VulkanVertexInfo getVertexInfo() {
		return vertexInfo;
	}

	@Override
	public void get(int index, ByteBuffer buffer) {
		pos.get(index + OFFSETOF_POS, buffer);
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public IVertex copy() {
		return new TriangleVertex(pos);
	}
}
