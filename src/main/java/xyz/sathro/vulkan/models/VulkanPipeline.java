package xyz.sathro.vulkan.models;

import lombok.RequiredArgsConstructor;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.vertex.IVertex;

import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

@RequiredArgsConstructor
public class VulkanPipeline implements IDisposable {
	public long layout;
	public long pipeline;
	public final Class<? extends IVertex> vertexType;

	public void dispose() {
		vkDestroyPipeline(Vulkan.device, pipeline, null);
		vkDestroyPipelineLayout(Vulkan.device, layout, null);
	}
}