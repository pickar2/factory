package xyz.sathro.factory.vulkan.models;

import lombok.RequiredArgsConstructor;

import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static xyz.sathro.factory.vulkan.Vulkan.*;

@RequiredArgsConstructor
public class CommandPool {
	public final long handle;
	public final long fence;

	public static CommandPool newDefault(int flags, VulkanQueue queue) {
		return new CommandPool(createCommandPool(flags, queue), createDefaultFence(true));
	}

	public void dispose() {
		vkDestroyFence(device, fence, null);
		vkDestroyCommandPool(device, handle, null);
	}
}
