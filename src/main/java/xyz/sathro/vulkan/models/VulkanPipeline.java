package xyz.sathro.vulkan.models;

import lombok.RequiredArgsConstructor;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.vertex.IVertex;

import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

@RequiredArgsConstructor
public class VulkanPipeline implements IDisposable {
	public final Class<? extends IVertex> vertexType;
	public long layout;
	public long handle;

	public void dispose() {
		vkDestroyPipeline(Vulkan.device, handle, null);
		vkDestroyPipelineLayout(Vulkan.device, layout, null);
	}
}