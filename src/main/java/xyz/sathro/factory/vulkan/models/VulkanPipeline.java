package xyz.sathro.factory.vulkan.models;

import lombok.RequiredArgsConstructor;
import xyz.sathro.factory.vulkan.Vulkan;
import xyz.sathro.factory.vulkan.vertex.IVertex;

import static org.lwjgl.vulkan.VK10.*;

@RequiredArgsConstructor
public class VulkanPipeline {
	public long layout;
	public long pipeline;
	public final Class<? extends IVertex> vertexType;

	public void dispose() {
		vkDestroyPipeline(Vulkan.device, pipeline, null);
		vkDestroyPipelineLayout(Vulkan.device, layout, null);
	}
}