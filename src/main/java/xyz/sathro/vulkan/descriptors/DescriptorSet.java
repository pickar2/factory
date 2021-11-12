package xyz.sathro.vulkan.descriptors;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCopyDescriptorSet;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import xyz.sathro.vulkan.Vulkan;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorSet {
	public final DescriptorSetLayout layout;
	public final DescriptorPool pool;
	private final long handle;

	protected DescriptorSet(DescriptorSetLayout layout, long handle, DescriptorPool pool) {
		this.layout = layout;
		this.handle = handle;
		this.pool = pool;
	}

	public UpdateBuilder updateBuilder() {
		return new UpdateBuilder();
	}

	public void dispose() {
		pool.disposeDescriptorSet(this);
	}

	public long getHandle() {
		return handle;
	}

	public class UpdateBuilder {
		private final HashMap<Integer, DescriptorWrite> writes = new HashMap<>();
		private final List<DescriptorCopy> copies = new ArrayList<>();

		private UpdateBuilder() { }

		public DescriptorWrite write(int dstArrayElement, int descriptorCount, int bindingIndex) {
			return new DescriptorWrite(dstArrayElement, descriptorCount, bindingIndex);
		}

		public DescriptorWrite write(int bindingIndex) {
			return new DescriptorWrite(bindingIndex);
		}

		public UpdateBuilder copy(int srcBindingIndex, int srcArrayElement, DescriptorSet srcSet, int dstBindingIndex, int dstArrayElement, DescriptorSet dstSet, int descriptorCount) {
			copies.add(new DescriptorCopy(srcBindingIndex, srcArrayElement, srcSet, dstBindingIndex, dstArrayElement, dstSet, descriptorCount));
			return this;
		}

		public void update() {
			try (MemoryStack stack = stackPush()) {
				VkWriteDescriptorSet.Buffer descriptorWrites = null;
				if (writes.size() > 0) {
					descriptorWrites = VkWriteDescriptorSet.callocStack(writes.size(), stack);
					int i = 0;
					for (DescriptorWrite write : writes.values()) {
						descriptorWrites.get(i++).set(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, write.pNext, handle,
						                              write.bindingIndex, write.dstArrayElement, write.descriptorCount,
						                              layout.getBinding(write.bindingIndex).descriptorType,
						                              write.pImageInfo, write.pBufferInfo, write.pTexelBufferView);
					}
				}

				VkCopyDescriptorSet.Buffer descriptorCopies = null;
				if (!copies.isEmpty()) {
					descriptorCopies = VkCopyDescriptorSet.callocStack(copies.size(), stack);
					int i = 0;
					for (DescriptorCopy copy : copies) {
						descriptorCopies.get(i++).set(VK_STRUCTURE_TYPE_COPY_DESCRIPTOR_SET, VK_NULL_HANDLE,
						                              copy.srcSet.handle, copy.srcBinding, copy.srcArrayElement,
						                              copy.dstSet.handle, copy.dstBinding, copy.dstArrayElement,
						                              copy.descriptorCount);
					}
				}

				vkUpdateDescriptorSets(Vulkan.device, descriptorWrites, descriptorCopies);
			}
		}

		public class DescriptorWrite {
			public final int bindingIndex;
			public int dstArrayElement;
			public int descriptorCount;

			public VkDescriptorImageInfo.Buffer pImageInfo;
			public VkDescriptorBufferInfo.Buffer pBufferInfo;
			public LongBuffer pTexelBufferView;
			public long pNext = VK_NULL_HANDLE;

			private DescriptorWrite(int dstArrayElement, int descriptorCount, int bindingIndex) {
				this.dstArrayElement = dstArrayElement;
				this.descriptorCount = descriptorCount;
				this.bindingIndex = bindingIndex;
			}

			private DescriptorWrite(int bindingIndex) {
				this.dstArrayElement = 0;
				this.descriptorCount = -1;
				this.bindingIndex = bindingIndex;
			}

			public DescriptorWrite descriptorCount(int descriptorCount) {
				this.descriptorCount = descriptorCount;
				return this;
			}

			public DescriptorWrite dstArrayElement(int dstArrayElement) {
				this.dstArrayElement = dstArrayElement;
				return this;
			}

			public DescriptorWrite pImageInfo(VkDescriptorImageInfo.Buffer pImageInfo) {
				this.pImageInfo = pImageInfo;
				if (descriptorCount == -1) {
					descriptorCount = pImageInfo.remaining();
				}
				return this;
			}

			public DescriptorWrite pBufferInfo(VkDescriptorBufferInfo.Buffer pBufferInfo) {
				this.pBufferInfo = pBufferInfo;
				if (descriptorCount == -1) {
					descriptorCount = pBufferInfo.remaining();
				}
				return this;
			}

			public DescriptorWrite pTexelBufferView(LongBuffer pTexelBufferView) {
				this.pTexelBufferView = pTexelBufferView;
				if (descriptorCount == -1) {
					descriptorCount = pTexelBufferView.remaining();
				}
				return this;
			}

			public DescriptorWrite pNext(long pNext) {
				this.pNext = pNext;
				return this;
			}

			public UpdateBuilder add() {
				writes.put(bindingIndex, this);
				return UpdateBuilder.this;
			}
		}

		public class DescriptorCopy {
			public final int srcBinding;
			public final int srcArrayElement;
			public final DescriptorSet srcSet;
			public final int dstBinding;
			public final int dstArrayElement;
			public final DescriptorSet dstSet;
			public final int descriptorCount;

			private DescriptorCopy(int srcBinding, int srcArrayElement, DescriptorSet srcSet, int dstBinding, int dstArrayElement, DescriptorSet dstSet, int descriptorCount) {
				this.srcBinding = srcBinding;
				this.srcArrayElement = srcArrayElement;
				this.srcSet = srcSet;
				this.dstBinding = dstBinding;
				this.dstArrayElement = dstArrayElement;
				this.dstSet = dstSet;
				this.descriptorCount = descriptorCount;
			}
		}
	}
}
