package xyz.sathro.vulkan.utils;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.VulkanQueue;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.utils.VulkanUtils.VkCheck;

public class CommandBuffers {
	private CommandBuffers() { }

	public static void endSingleTimeCommands(VkCommandBuffer commandBuffer, long commandPoolHandle, VulkanQueue queue, MemoryStack stack) {
		vkEndCommandBuffer(commandBuffer);

		final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer));

		final long fence = Vulkan.createDefaultFence(false); // TODO: reuse fences
		queue.submit(submitInfo, fence);

		vkDestroyFence(Vulkan.device, fence, null);

		vkFreeCommandBuffers(Vulkan.device, commandPoolHandle, commandBuffer);
	}

	public static void endSingleTimeCommands(VkCommandBuffer commandBuffer, long commandPoolHandle, long fence, VulkanQueue queue, MemoryStack stack) {
		vkEndCommandBuffer(commandBuffer);

		final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer));

		vkResetFences(Vulkan.device, fence);
		queue.submit(submitInfo, fence);

		vkFreeCommandBuffers(Vulkan.device, commandPoolHandle, commandBuffer);
	}

	public static VkCommandBuffer beginSingleTimeCommands(long commandPoolHandle, MemoryStack stack) {
		final VkCommandBuffer commandBuffer = createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, commandPoolHandle);

		final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
				.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

		vkBeginCommandBuffer(commandBuffer, beginInfo);

		return commandBuffer;
	}

	public static VkCommandBuffer[] createCommandBuffers(int level, int count, long commandPoolHandle) {
		// TODO: change commandPoolHandle to commandPool and use CommandPool class
		try (MemoryStack stack = stackPush()) {
			final VkCommandBuffer[] commandBuffers = new VkCommandBuffer[count];

			final PointerBuffer pCommandBuffer = stack.mallocPointer(commandBuffers.length);

			final VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
					.commandPool(commandPoolHandle)
					.level(level)
					.commandBufferCount(commandBuffers.length);

			VkCheck(vkAllocateCommandBuffers(Vulkan.device, allocInfo, pCommandBuffer), "Failed to allocate command buffers");

			for (int i = 0; i < commandBuffers.length; i++) {
				commandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.device);
			}

			return commandBuffers;
		}
	}

	public static VkCommandBuffer createCommandBuffer(int level, long commandPoolHandle) {
		try (MemoryStack stack = stackPush()) {
			final PointerBuffer pCommandBuffer = stack.mallocPointer(1);

			final VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
					.commandPool(commandPoolHandle)
					.level(level)
					.commandBufferCount(1);

			VkCheck(vkAllocateCommandBuffers(Vulkan.device, allocInfo, pCommandBuffer), "Failed to allocate command buffers");

			return new VkCommandBuffer(pCommandBuffer.get(0), Vulkan.device);
		}
	}
}
