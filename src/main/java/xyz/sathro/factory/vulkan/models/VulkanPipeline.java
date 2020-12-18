package xyz.sathro.factory.vulkan.models;

import xyz.sathro.factory.vulkan.Vulkan;

import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

public class VulkanPipeline {
	public long layout;
	public long pipeline;

	public void dispose() {
		vkDestroyPipeline(Vulkan.device, pipeline, null);
		vkDestroyPipelineLayout(Vulkan.device, layout, null);
	}
}