package xyz.sathro.vulkan.models;

import org.lwjgl.vulkan.VkQueue;

public class VulkanQueue {
	public final Integer index;
	public VkQueue queue;

	public VulkanQueue(Integer index) {
		this.index = index;
	}
}
