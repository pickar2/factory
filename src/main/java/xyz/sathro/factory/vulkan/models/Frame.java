package xyz.sathro.factory.vulkan.models;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackGet;

public class Frame {
	private final long imageAvailableSemaphore;
	private final long renderFinishedSemaphore;
	private final long fence;

	public Frame(long imageAvailableSemaphore, long renderFinishedSemaphore, long fence) {
		this.imageAvailableSemaphore = imageAvailableSemaphore;
		this.renderFinishedSemaphore = renderFinishedSemaphore;
		this.fence = fence;
	}

	public long imageAvailableSemaphore() {
		return imageAvailableSemaphore;
	}

	public LongBuffer pImageAvailableSemaphore() {
		return stackGet().longs(imageAvailableSemaphore);
	}

	public long renderFinishedSemaphore() {
		return renderFinishedSemaphore;
	}

	public LongBuffer pRenderFinishedSemaphore() {
		return stackGet().longs(renderFinishedSemaphore);
	}

	public long fence() {
		return fence;
	}

	public LongBuffer pFence() {
		return stackGet().longs(fence);
	}
}
