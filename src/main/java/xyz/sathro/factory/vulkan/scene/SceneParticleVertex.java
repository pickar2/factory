package xyz.sathro.factory.vulkan.scene;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.joml.Vector3f;
import xyz.sathro.vulkan.models.VulkanVertexInfo;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

@AllArgsConstructor
@NoArgsConstructor
public class SceneParticleVertex implements IVertex {
	public static final int SIZEOF = (3 + 3 + 1) * Float.BYTES;
	public static final int OFFSETOF_POS = 0;
	public static final int OFFSETOF_COLOR = 3 * Float.BYTES;
	public static final int OFFSETOF_SIZE = (3 + 3) * Float.BYTES;

	public static final VulkanVertexInfo vertexInfo;

	public Vector3f pos;
	public Vector3f color;
	public float size;

	static {
		vertexInfo = VulkanVertexInfo.builder()
				.startBinding(0, SIZEOF, VK_VERTEX_INPUT_RATE_VERTEX)
				.addAttribute(0, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_POS)
				.addAttribute(1, VK_FORMAT_R32G32B32_SFLOAT, OFFSETOF_COLOR)
				.addAttribute(2, VK_FORMAT_R32_SFLOAT, OFFSETOF_SIZE)
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
		buffer.putFloat(index + OFFSETOF_SIZE, size);
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public SceneParticleVertex copy() {
		return new SceneParticleVertex(pos, color, size);
	}
}
