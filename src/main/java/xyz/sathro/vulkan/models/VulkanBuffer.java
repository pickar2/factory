package xyz.sathro.vulkan.models;

import xyz.sathro.vulkan.Vulkan;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

// TODO: Either dispose prev buffer when set new info, or make buffer and allocation final.
public class VulkanBuffer implements IDisposable {
	public long buffer;
	public long allocation;

	public VulkanBuffer set(long buffer, long allocation) {
		this.buffer = buffer;
		this.allocation = allocation;

		return this;
	}

	public VulkanBuffer set(VulkanBuffer other) {
		this.buffer = other.buffer;
		this.allocation = other.allocation;

		return this;
	}

	public void dispose() {
		vmaDestroyBuffer(Vulkan.vmaAllocator, buffer, allocation);
	}
}
