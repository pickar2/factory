package xyz.sathro.vulkan.buffers;

import lombok.Getter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.models.VulkanBuffer;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static xyz.sathro.vulkan.Vulkan.vmaAllocator;

public class StagedBuffer implements IDisposable {
	private ByteBuffer byteBuffer;

	@Getter private VulkanBuffer CPUBuffer;
	@Getter private VulkanBuffer GPUBuffer;

	@Getter private int bufferSize;
	@Getter private boolean persistent = false;

	public StagedBuffer(int bufferSize, int usage, boolean persistent, boolean CPUToGPUCopy, boolean GPUToCPUCopy) {
		this.bufferSize = bufferSize;
		createBuffers(usage, CPUToGPUCopy, GPUToCPUCopy);

		setPersistent(persistent);
	}

	public void reallocate(int bufferSize, int usage, boolean CPUToGPUCopy, boolean GPUToCPUCopy) {
		if (this.bufferSize == bufferSize) { return; }

		if (persistent) {
			vmaUnmapMemory(vmaAllocator, CPUBuffer.allocation);
			byteBuffer = null;
		}

		CPUBuffer.registerToDisposal();
		GPUBuffer.registerToDisposal();

		this.bufferSize = bufferSize;
		createBuffers(usage, CPUToGPUCopy, GPUToCPUCopy);

		if (persistent) {
			final PointerBuffer pointer = MemoryStack.stackMallocPointer(1);
			vmaMapMemory(vmaAllocator, CPUBuffer.allocation, pointer);
			byteBuffer = MemoryUtil.memByteBuffer(pointer.get(0), bufferSize);
		}
	}

	private void createBuffers(int usage, boolean CPUToGPUCopy, boolean GPUToCPUCopy) {
		CPUBuffer = Vulkan.createBuffer(bufferSize,
		                                (CPUToGPUCopy ? VK_BUFFER_USAGE_TRANSFER_SRC_BIT : 0) |
		                                (GPUToCPUCopy ? VK_BUFFER_USAGE_TRANSFER_DST_BIT : 0),
		                                VMA_MEMORY_USAGE_CPU_ONLY
		);
		GPUBuffer = Vulkan.createBuffer(bufferSize,
		                                usage |
		                                (GPUToCPUCopy ? VK_BUFFER_USAGE_TRANSFER_SRC_BIT : 0) |
		                                (CPUToGPUCopy ? VK_BUFFER_USAGE_TRANSFER_DST_BIT : 0),
		                                VMA_MEMORY_USAGE_GPU_ONLY
		);
	}

	public void updateBuffer(Consumer<ByteBuffer> consumer) {
		if (persistent) {
			consumer.accept(byteBuffer);
		} else {
			final PointerBuffer pointer = MemoryStack.stackMallocPointer(1);
			vmaMapMemory(vmaAllocator, CPUBuffer.allocation, pointer);
			consumer.accept(MemoryUtil.memByteBuffer(pointer.get(0), bufferSize));
			vmaUnmapMemory(vmaAllocator, CPUBuffer.allocation);
		}
	}

	public ByteBuffer getByteBuffer() {
		if (!persistent) { throw new RuntimeException("Trying to get ByteBuffer of non persistent StagedBuffer"); }

		return byteBuffer;
	}

	public void copyToGPU() {
		Vulkan.copyBuffer(CPUBuffer, GPUBuffer, bufferSize);
	}

	public void copyToCPU() {
		Vulkan.copyBuffer(GPUBuffer, CPUBuffer, bufferSize);
	}

	public void setPersistent(boolean value) {
		if (value && !persistent) {
			final PointerBuffer pointer = MemoryStack.stackMallocPointer(1);
			vmaMapMemory(vmaAllocator, CPUBuffer.allocation, pointer);
			byteBuffer = MemoryUtil.memByteBuffer(pointer.get(0), bufferSize);
		} else if (!value && persistent) {
			vmaUnmapMemory(vmaAllocator, CPUBuffer.allocation);
			byteBuffer = null;
		}
		persistent = value;
	}

	@Override
	public void dispose() {
		if (persistent) {
			vmaUnmapMemory(vmaAllocator, CPUBuffer.allocation);
		}
		CPUBuffer.dispose();
		GPUBuffer.dispose();
	}
}
