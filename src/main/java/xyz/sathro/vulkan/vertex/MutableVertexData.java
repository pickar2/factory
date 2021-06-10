package xyz.sathro.vulkan.vertex;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MutableVertexData {
	private int vertexOffset;
	private int indexOffset;
	private int indexCount;
}
