package xyz.sathro.factory.vulkan.models;

import xyz.sathro.factory.vulkan.Vulkan;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

public class VulkanBuffer {
	public long buffer;
	public long allocation;

	public void dispose() {
		vmaDestroyBuffer(Vulkan.vmaAllocator, buffer, allocation);
	}
}
