package xyz.sathro.vulkan.renderer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.Engine;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.test.xpbd.PhysicsCompute;
import xyz.sathro.factory.test.xpbd.PhysicsController;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.factory.util.Timer;
import xyz.sathro.factory.window.Window;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.events.DrawFrameEvent;
import xyz.sathro.vulkan.models.Frame;
import xyz.sathro.vulkan.utils.CommandBuffers;
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

@Log4j2
public class MainRenderer {
	public static final int FRAMES_PER_SECOND = 60;
	public static final double MS_PER_UPDATE = 1000.0 / FRAMES_PER_SECOND;
	public static final double MS_PER_UPDATE_INV = 1 / MS_PER_UPDATE;

	// TODO: support more than 2 max frames
	public static final int MAX_FRAMES_IN_FLIGHT = 2;

	private static final List<Frame> inFlightFrames = new ObjectArrayList<>(MAX_FRAMES_IN_FLIGHT);
	private static final List<IRenderer> renderers = new ObjectArrayList<>();
	private static final IntBuffer pImageIndex = MemoryUtil.memAllocInt(1);
	private static Int2ObjectMap<Frame> imagesInFlight;
	@Getter private static int currentFrameIndex;
	private static VkCommandBuffer[] primaryCommandBuffers;
	public static boolean framebufferResize = false;

	public static void init(List<IRenderer> newRenderers) {
		imagesInFlight = new Int2ObjectOpenHashMap<>(Vulkan.swapChainImageCount);

		renderers.addAll(newRenderers);
		renderers.forEach(IRenderer::init);

		primaryCommandBuffers = CommandBuffers.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, Vulkan.swapChainImageCount, commandPool);
		createSyncObjects();
	}

	public static void renderLoop() {
		double lag = 0.0;
		long time;
		final Timer timer = new Timer();

		while (!Window.shouldClose) {

			lag += timer.getElapsedTimeAndReset();
			if (lag >= MS_PER_UPDATE) {
				Time.updateTimer();

				Time.lastDeltaTime = lag * MS_PER_UPDATE_INV;
				Time.lastUnscaledDeltaTime = lag;

				time = System.nanoTime();
				EventManager.callEvent(new DrawFrameEvent(Time.lastDeltaTime));

				drawFrame(Time.lastDeltaTime);
				final double fps = Maths.round(1000 / lag, 1);
				final double frameTime = Maths.round((System.nanoTime() - time) / 1_000_000d, 2);
				Engine.submitTask(() -> GLFW.glfwSetWindowTitle(Window.handle,
				                                                Window.title +
				                                                " FPS: " + Maths.fixedPrecision(fps, 1) +
				                                                " Frame time: " + Maths.fixedNumberSize(Maths.fixedPrecision(frameTime, 2), 4) + "ms" +
				                                                " Compute: " + Maths.fixedPrecision(PhysicsCompute.getComputeTime(), 2) + "ms" +
				                                                " Physics: " + Maths.fixedPrecision(PhysicsController.getPhysicsTime(), 2) + "ms"
				                  )
				);

				lag = 0;
			}
		}

		vkDeviceWaitIdle(device);
	}

	// TODO: next frame can start writing commands before this frame finishes being presented ?
	// TODO: different renderers (beforeFrame, writeCommandBuffers, afterFrame) can, and should, be processed by multiple threads
	// TODO: vkQueueSubmits must be limited/batched (record all required transfers and do them all in one pass?)
	// TODO: big renderers can split work into multiple commandBuffers which can be recorded in different threads
	// TODO: if commandBuffer will most likely change every frame, it should be VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT, because caching commands on GPU side can be inefficient
	// TODO: model and texture loading should be done off thread and renderer should know how to deal with not yet loaded resources
	private static void drawFrame(double lag) {
		try (final MemoryStack stack = stackPush()) {
			final Frame currentFrame = inFlightFrames.get(currentFrameIndex);

			vkWaitForFences(device, currentFrame.pFence(), true, UINT64_MAX);

			if (framebufferResize) {
				framebufferResize = false;
				recreateSwapChain();
			}

			int vkResult = vkAcquireNextImageKHR(device, swapChainHandle, UINT64_MAX, currentFrame.imageAvailableSemaphore(), VK_NULL_HANDLE, pImageIndex);
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
				synchronized (queues.present) {
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
				for (int i = 0; i < swapChainImageCount; i++) {
					recordPrimaryCommandBuffers(i);
				}
			}

			imagesInFlight.put(imageIndex, currentFrame);

			final VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.waitSemaphoreCount(1)
					.pWaitSemaphores(currentFrame.pImageAvailableSemaphore())
					.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
					.pSignalSemaphores(currentFrame.pRenderFinishedSemaphore())
					.pCommandBuffers(stack.pointers(primaryCommandBuffers[imageIndex]));

			synchronized (PhysicsCompute.copyCommandPool) {
				vkWaitForFences(device, PhysicsCompute.copyCommandPool.fence, true, UINT64_MAX);
			}
			vkResetFences(device, currentFrame.pFence());
			synchronized (queues.graphics) {
				vkResult = vkQueueSubmit(queues.graphics.queue, submitInfo, currentFrame.fence());
			}
			if (vkResult != VK_SUCCESS) {
				vkResetFences(device, currentFrame.pFence());
				throw new RuntimeException("Failed to submit draw command buffer: " + VKReturnCode.getByCode(vkResult));
			}

			final VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
					.pWaitSemaphores(currentFrame.pRenderFinishedSemaphore())
					.swapchainCount(1)
					.pSwapchains(stack.longs(swapChainHandle))
					.pImageIndices(pImageIndex);

			synchronized (queues.present) {
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
		primaryCommandBuffers = CommandBuffers.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, Vulkan.swapChainImageCount, commandPool);

		renderers.forEach(IRenderer::createSwapChain);
	}

	private static void createSyncObjects() {
		try (final MemoryStack stack = stackPush()) {
			final VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

			final VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
					.flags(VK_FENCE_CREATE_SIGNALED_BIT);

			final LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
			final LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
			final LongBuffer pFence = stack.mallocLong(1);

			for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
				if (vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS ||
				    vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS ||
				    vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
				}

				inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
			}
		}
	}

	public static void recordPrimaryCommandBuffers(int imageIndex) {
		try (final MemoryStack stack = stackPush()) {
			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			final VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
			clearValues.get(0).color()
					.float32(0, 100 / 255.0f)
					.float32(1, 149 / 255.0f)
					.float32(2, 237 / 255.0f)
					.float32(3, 1.0f);
			clearValues.get(1).depthStencil()
					.set(1, 0);

			final VkRect2D renderArea = VkRect2D.calloc(stack)
					.offset(offset -> offset.set(0, 0))
					.extent(swapChainExtent);

			final VkCommandBuffer commandBuffer = primaryCommandBuffers[imageIndex];

			final VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
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

			VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to finish recording command buffer");
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
