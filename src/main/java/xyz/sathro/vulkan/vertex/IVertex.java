package xyz.sathro.vulkan.vertex;

import xyz.sathro.vulkan.models.IBufferObject;
import xyz.sathro.vulkan.models.VulkanVertexInfo;

public interface IVertex extends IBufferObject {
	VulkanVertexInfo getVertexInfo();

	IVertex copy();
}
