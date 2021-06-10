package xyz.sathro.vulkan.vertex;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class VertexData {
	private final int vertexOffset;
	private final int indexOffset;
	private final int indexCount;
}
