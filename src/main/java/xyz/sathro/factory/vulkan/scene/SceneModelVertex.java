package xyz.sathro.factory.vulkan.scene;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.joml.Vector3f;
import xyz.sathro.vulkan.models.VulkanVertexInfo;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;

@AllArgsConstructor
@NoArgsConstructor
public class SceneModelVertex implements IVertex {
	public static final int SIZEOF = (3 + 3 + 3) * Float.BYTES;
	public static final int OFFSETOF_POS = 0;
	public static final int OFFSETOF_COLOR = 3 * Float.BYTES;
	public static final int OFFSETOF_NORMAL = 3 * 2 * Float.BYTES;

	public static final VulkanVertexInfo vertexInfo;

	public Vector3f pos;
	public Vector3f color;
	public Vector3f normal;

	static {
		vertexInfo = VulkanVertexInfo.builder()
				.startBinding(0, SIZEOF, VK_VERTEX_INPUT_RATE_VERTEX)
				.addAttribute(0, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_POS)
				.addAttribute(1, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_COLOR)
				.addAttribute(2, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_NORMAL)
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
		pos.get(index + OFFSETOF_POS, buffer);
		color.get(index + OFFSETOF_COLOR, buffer);
		normal.get(index + OFFSETOF_NORMAL, buffer);
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public SceneModelVertex copy() {
		return new SceneModelVertex(pos, color, normal);
	}
}
