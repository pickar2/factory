package xyz.sathro.factory.vulkan.vertices;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import xyz.sathro.vulkan.models.VulkanVertexInfo;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SINT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;

@NoArgsConstructor
@AllArgsConstructor
public class UIVertex implements IVertex {
	public static final int SIZEOF = 1 * Integer.BYTES;
	public static final int OFFSETOF_INDEX = 0;

	private static final VulkanVertexInfo vertexInfo;

	public int vertexIndex;

	static {
		vertexInfo = VulkanVertexInfo.builder()
				.startBinding(0, SIZEOF, VK_VERTEX_INPUT_RATE_VERTEX)
				 .addAttribute(0, VK_FORMAT_R32_SINT, OFFSETOF_INDEX)
				.endBinding()
				.build();

		vertexInfo.registerToLateDisposal();
	}

	@Override
	public VulkanVertexInfo getVertexInfo() {
		return vertexInfo;
	}

	@Override
	public void get(int index, ByteBuffer buffer) {
		buffer.putInt(index + OFFSETOF_INDEX, vertexIndex);
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public IVertex copy() {
		return new UIVertex(vertexIndex);
	}
}
