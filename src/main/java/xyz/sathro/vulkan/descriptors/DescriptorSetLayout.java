package xyz.sathro.vulkan.descriptors;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.utils.VulkanUtils;

import java.nio.LongBuffer;
import java.util.Collection;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorSetLayout implements IDisposable {
	private final Int2ObjectMap<Binding> bindings = new Int2ObjectOpenHashMap<>();
	@Getter private long handle;

	private DescriptorSetLayout() { }

	public static Builder builder() {
		return new DescriptorSetLayout().new Builder();
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

	@EqualsAndHashCode
	public static class Binding {
		public final int index;
		public final int descriptorCount;
		public final int descriptorType;
		public final int stageFlags;

		public Binding(int index, int descriptorCount, int descriptorType, int stageFlags) {
			this.index = index;
			this.descriptorCount = descriptorCount;
			this.descriptorType = descriptorType;
			this.stageFlags = stageFlags;
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

		public Builder addBinding(int index, int descriptorCount, int descriptorType, int stageFlags) {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			bindings.put(index, new DescriptorSetLayout.Binding(index, descriptorCount, descriptorType, stageFlags));

			return this;
		}

		public DescriptorSetLayout build() {
			if (complete) {
				throw new IllegalStateException("Layout is already built");
			}
			complete = true;

			try (MemoryStack stack = stackPush()) {
				final VkDescriptorSetLayoutBinding.Buffer vkBindings = VkDescriptorSetLayoutBinding.calloc(bindings.size(), stack);
				int i = 0;
				for (DescriptorSetLayout.Binding binding : bindings.values()) {
					vkBindings.get(i++).set(binding.index, binding.descriptorType, binding.descriptorCount, binding.stageFlags, null);
				}

				final VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
						.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
						.pBindings(vkBindings);

				final LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

				VulkanUtils.VkCheck(vkCreateDescriptorSetLayout(Vulkan.device, layoutInfo, null, pDescriptorSetLayout), "Failed to create descriptor set layout");

				handle = pDescriptorSetLayout.get(0);

				return DescriptorSetLayout.this;
			}
		}
	}
}
