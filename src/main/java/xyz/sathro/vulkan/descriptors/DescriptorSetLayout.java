package xyz.sathro.vulkan.descriptors;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.utils.VulkanUtils;

import java.nio.LongBuffer;
import java.util.Collection;
import java.util.HashMap;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorSetLayout implements IDisposable {
	private final HashMap<Integer, Binding> bindings = new HashMap<>();
	private long handle;

	private DescriptorSetLayout() { }

	public static Builder builder() {
		return new DescriptorSetLayout().new Builder();
	}

	public long getHandle() {
		return handle;
	}

	public Collection<Binding> getBindings() {
		return bindings.values();
	}

	public Binding getBinding(int index) {
		return bindings.get(index);
	}

	public void dispose() {
		vkDestroyDescriptorSetLayout(Vulkan.device, handle, null);
	}

	public static class Binding {
		public final int index;
		public final int descriptorCount;
		public final int descriptorType;
		public final int stageFlags;
		public final LongBuffer pImmutableSamplers;

		public Binding(int index, int descriptorCount, int descriptorType, int stageFlags, LongBuffer pImmutableSamplers) {
			this.index = index;
			this.descriptorCount = descriptorCount;
			this.descriptorType = descriptorType;
			this.stageFlags = stageFlags;
			this.pImmutableSamplers = pImmutableSamplers;
		}
	}

	public class Builder {
		private boolean complete = false;

		private Builder() { }

		public Builder addBinding(DescriptorSetLayout.Binding binding) {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			bindings.put(binding.index, binding);

			return this;
		}

		public Builder addBinding(int index, int descriptorCount, int descriptorType, int stageFlags, LongBuffer pImmutableSamplers) {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			bindings.put(index, new DescriptorSetLayout.Binding(index, descriptorCount, descriptorType, stageFlags, pImmutableSamplers));

			return this;
		}

		public Builder addBinding(int index, int descriptorCount, int descriptorType, int stageFlags) {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			bindings.put(index, new DescriptorSetLayout.Binding(index, descriptorCount, descriptorType, stageFlags, null));

			return this;
		}

		public DescriptorSetLayout build() {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			complete = true;

			try (MemoryStack stack = stackPush()) {
				VkDescriptorSetLayoutBinding.Buffer vkBindings = VkDescriptorSetLayoutBinding.callocStack(bindings.size(), stack);
				int i = 0;
				for (DescriptorSetLayout.Binding binding : bindings.values()) {
					vkBindings.get(i++).set(binding.index, binding.descriptorType, binding.descriptorCount, binding.stageFlags, binding.pImmutableSamplers);
				}

				VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
						.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
						.pBindings(vkBindings);

				LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

				VulkanUtils.VkCheck(vkCreateDescriptorSetLayout(Vulkan.device, layoutInfo, null, pDescriptorSetLayout), "Failed to create descriptor set layout");

				handle = pDescriptorSetLayout.get(0);

				return DescriptorSetLayout.this;
			}
		}
	}
}
