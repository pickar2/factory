package xyz.sathro.vulkan.models;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.UINT64_MAX;
import static xyz.sathro.vulkan.Vulkan.device;

public class VulkanQueue {
	public final int index;
	public final Set<Flag> flags = new ObjectOpenHashSet<>(Flag.values().length);
	public VkQueue queue;

	public VulkanQueue(int index) {
		this.index = index;
	}

	public boolean hasGraphicsFlag() {
		return flags.contains(Flag.GRAPHICS);
	}

	public boolean hasTransfersFlag() {
		return flags.contains(Flag.TRANSFER);
	}

	public boolean hasComputeFlag() {
		return flags.contains(Flag.COMPUTE);
	}

	public boolean hasPresentFlag() {
		return flags.contains(Flag.PRESENT);
	}

	public void submit(VkSubmitInfo.Buffer submitInfo, long fence) {
		synchronized (this) {
			vkQueueSubmit(queue, submitInfo, fence);
			vkWaitForFences(device, fence, true, UINT64_MAX);
		}
	}

	public void submit(VkSubmitInfo.Buffer submitInfo) {
		synchronized (this) {
			vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
		}
	}

	public enum Flag {
		GRAPHICS, TRANSFER, COMPUTE, PRESENT
	}
}