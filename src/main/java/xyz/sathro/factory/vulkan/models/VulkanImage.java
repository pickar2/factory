package xyz.sathro.factory.vulkan.models;

import xyz.sathro.factory.vulkan.Vulkan;

import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;

public class VulkanImage {
	public int width;
	public int height;
	public long image;
	public long imageView;
	public long allocation;
	public int mipLevels;
	private boolean disposed = false;

	public void dispose() {
		if (!disposed) {
			disposed = true;
			vkDestroyImageView(Vulkan.device, imageView, null);
			vmaDestroyImage(Vulkan.vmaAllocator, image, allocation);
		}
	}

//	public void markForDisposal() {
//		VulkanLayout.disposalQueue.add(this);
//	}
}