package xyz.sathro.vulkan.renderer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.window.MouseInput;
import xyz.sathro.factory.window.Window;
import xyz.sathro.vulkan.models.Frame;
import xyz.sathro.vulkan.utils.DisposalQueue;
import xyz.sathro.vulkan.utils.VKReturnCode;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.*;
import static xyz.sathro.vulkan.utils.BufferUtils.asPointerBuffer;
import static xyz.sathro.vulkan.utils.VulkanUtils.VkCheck;

public class MainRenderer {
	public static final int MAX_FRAMES_IN_FLIGHT = 2;

	private static final List<Frame> inFlightFrames = new ObjectArrayList<>(MAX_FRAMES_IN_FLIGHT);
	private static final List<IRenderer> renderers = new ObjectArrayList<>();
	private static final IntBuffer pImageIndex = MemoryUtil.memAllocInt(1);
	private static Int2ObjectMap<Frame> imagesInFlight;
	@Getter private static int currentFrameIndex;
	private static VkCommandBuffer[] primaryCommandBuffers;

	public static void init(List<IRenderer> newRenderers) {
		imagesInFlight = new Int2ObjectOpenHashMap<>(swapChainImages.size());

		renderers.addAll(newRenderers);
		renderers.forEach(IRenderer::init);

		primaryCommandBuffers = createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, swapChainImages.size(), commandPool);
		createSyncObjects();
	}

	public static void mainLoop() {
		while (!Window.shouldClose) {
			Window.update();
			GLFW.glfwPollEvents();
			MouseInput.input();

			drawFrame();
		}

		vkDeviceWaitIdle(device);
	}

	private static void drawFrame() {
		try (final MemoryStack stack = stackPush()) {
			final Frame currentFrame = inFlightFrames.get(currentFrameIndex);

			vkWaitForFences(device, currentFrame.pFence(), true, UINT64_MAX);

			if (framebufferResize) {
				framebufferResize = false;
				recreateSwapChain();
			}

			int vkResult = vkAcquireNextImageKHR(device, swapChain, UINT64_MAX, currentFrame.imageAvailableSemaphore(), VK_NULL_HANDLE, pImageIndex);
			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
				recreateSwapChain();
				return;
			}

			if (vkResult != VK_SUCCESS && vkResult != VK_SUBOPTIMAL_KHR) {
				throw new RuntimeException("Cannot get image : " + VKReturnCode.getByCode(vkResult));
			}

			final int imageIndex = pImageIndex.get(0);

			if (imagesInFlight.containsKey(imageIndex)) {
				vkWaitForFences(device, imagesInFlight.get(imageIndex).fence(), true, UINT64_MAX);
			} else {
				synchronized (queues.present.index) {
					vkQueueWaitIdle(queues.present.queue);
				}
			}

			boolean dirty = false;
			for (IRenderer renderer : renderers) {
				renderer.beforeFrame(imageIndex);
				if (renderer.commandBuffersChanged()) {
					dirty = true;
				}
			}

			if (dirty) {
				vkResetCommandPool(device, commandPool, 0);
				for (int i = 0; i < swapChainImages.size(); i++) {
					recordPrimaryCommandBuffers(i);
				}
			}

			imagesInFlight.put(imageIndex, currentFrame);

			final VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.waitSemaphoreCount(1)
					.pWaitSemaphores(currentFrame.pImageAvailableSemaphore())
					.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
					.pSignalSemaphores(currentFrame.pRenderFinishedSemaphore())
					.pCommandBuffers(stack.pointers(primaryCommandBuffers[imageIndex]));

			vkResetFences(device, currentFrame.pFence());
			synchronized (queues.graphics.index) {
				vkResult = vkQueueSubmit(queues.graphics.queue, submitInfo, currentFrame.fence());
			}
			if (vkResult != VK_SUCCESS) {
				vkResetFences(device, currentFrame.pFence());
				throw new RuntimeException("Failed to submit draw command buffer: " + VKReturnCode.getByCode(vkResult));
			}

			final VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
					.pWaitSemaphores(currentFrame.pRenderFinishedSemaphore())
					.swapchainCount(1)
					.pSwapchains(stack.longs(swapChain))
					.pImageIndices(pImageIndex);

			synchronized (queues.present.index) {
				vkResult = vkQueuePresentKHR(queues.present.queue, presentInfo);
			}
			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR) {
				recreateSwapChain();
			} else if (vkResult != VK_SUCCESS) {
				throw new RuntimeException("Failed to present swap chain image");
			}

			currentFrameIndex = (currentFrameIndex + 1) % MAX_FRAMES_IN_FLIGHT;

			for (IRenderer renderer : renderers) {
				renderer.afterFrame(imageIndex);
			}
			DisposalQueue.dispose();
		}
	}

	public static void createSwapChainObjects() {
		primaryCommandBuffers = createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, swapChainImages.size(), commandPool);

		renderers.forEach(IRenderer::createSwapChain);
	}

	private static void createSyncObjects() {
		try (final MemoryStack stack = stackPush()) {
			final VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

			final VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
					.flags(VK_FENCE_CREATE_SIGNALED_BIT);

			final LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
			final LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
			final LongBuffer pFence = stack.mallocLong(1);

			for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
				if (vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS || vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
				}

				inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
			}
		}
	}

	public static void recordPrimaryCommandBuffers(int imageIndex) {
		try (final MemoryStack stack = stackPush()) {
			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			final VkClearValue.Buffer clearValues = VkClearValue.callocStack(2, stack);
			clearValues.get(0).color()
					.float32(0, 100 / 255.0f)
					.float32(1, 149 / 255.0f)
					.float32(2, 237 / 255.0f)
					.float32(3, 1.0f);
			clearValues.get(1).depthStencil()
					.set(1, 0);

			final VkRect2D renderArea = VkRect2D.callocStack(stack)
					.offset(offset -> offset.set(0, 0))
					.extent(swapChainExtent);

			final VkCommandBuffer commandBuffer = primaryCommandBuffers[imageIndex];

			final VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
					.renderPass(renderPass)
					.framebuffer(swapChainFramebuffers.getLong(imageIndex))
					.renderArea(renderArea)
					.pClearValues(clearValues);

			VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");
			vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS);

			final List<VkCommandBuffer> commandBuffers = new ArrayList<>();

			for (IRenderer renderer : renderers) {
				commandBuffers.addAll(Arrays.asList(renderer.getCommandBuffers(imageIndex)));
			}

			vkCmdExecuteCommands(commandBuffer, asPointerBuffer(commandBuffers));

			vkCmdEndRenderPass(commandBuffer);

			VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to record command buffer");
		}
	}

	public static void cleanupSwapChain() {
		renderers.forEach(IRenderer::cleanupSwapChain);
		imagesInFlight.clear();
	}

	public static void cleanup() {
		renderers.forEach(IRenderer::dispose);

		inFlightFrames.forEach(Frame::dispose);
		inFlightFrames.clear();

		MemoryUtil.memFree(pImageIndex);
	}
}
