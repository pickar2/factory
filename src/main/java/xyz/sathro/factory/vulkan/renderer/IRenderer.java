package xyz.sathro.factory.vulkan.renderer;

import org.lwjgl.vulkan.VkCommandBuffer;

public interface IRenderer {
	void init();

	VkCommandBuffer[] getCommandBuffers(int imageIndex);

	boolean commandBuffersChanged();

	void beforeFrame(int imageIndex);

	void afterFrame(int imageIndex);

	void createSwapChain();

	void cleanupSwapChain();

	void dispose();
}
