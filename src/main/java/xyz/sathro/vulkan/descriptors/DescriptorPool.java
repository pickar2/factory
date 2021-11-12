package xyz.sathro.vulkan.descriptors;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.utils.VulkanUtils;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorPool implements IDisposable {
	private final HashMap<Integer, PoolType> types = new HashMap<>();
	private int maxSets = 1;
	private long handle;

	private DescriptorPool() { }

	public static Builder builder() {
		return new DescriptorPool().new Builder();
	}

	public List<DescriptorSet> createDescriptorSets(DescriptorSetLayout layout, int count) {
		DescriptorSetLayout[] layouts = new DescriptorSetLayout[count];
		for (int i = 0; i < count; i++) {
			layouts[i] = layout;
		}
		return createDescriptorSets(layouts);
	}

	public List<DescriptorSet> createDescriptorSets(DescriptorSetLayout... layouts) {
		final HashMap<Integer, Integer> typesToTake = new HashMap<>();

		for (DescriptorSetLayout layout : layouts) {
			for (DescriptorSetLayout.Binding binding : layout.getBindings()) {
				if (!typesToTake.containsKey(binding.descriptorType)) {
					typesToTake.put(binding.descriptorType, binding.descriptorCount);
				} else {
					typesToTake.put(binding.descriptorType, typesToTake.get(binding.descriptorType) + binding.descriptorCount);
				}
			}
		}

		for (Integer type : typesToTake.keySet()) {
			PoolType poolType = types.get(type);
			if (poolType == null) {
				throw new IllegalArgumentException("This pool doesn't accept " + DescriptorType.getByCode(type));
			}
			if (poolType.amountLeft < typesToTake.get(type)) {
				throw new IllegalStateException("This pool doesn't have enough space for " + DescriptorType.getByCode(type));
			}
			poolType.amountLeft -= typesToTake.get(type);
		}

		final List<DescriptorSet> descriptorSets = new ArrayList<>();
		try (MemoryStack stack = stackPush()) {
			LongBuffer layoutsBuf = stack.mallocLong(layouts.length);
			for (DescriptorSetLayout layout : layouts) {
				layoutsBuf.put(layout.getHandle());
			}
			layoutsBuf.flip();

			VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
					.descriptorPool(handle)
					.pSetLayouts(layoutsBuf);

			LongBuffer setsBuf = stack.mallocLong(layouts.length);

			VulkanUtils.VkCheck(VK10.vkAllocateDescriptorSets(Vulkan.device, allocInfo, setsBuf), "Failed to allocate descriptor sets");

			for (int i = 0; i < layouts.length; i++) {
				descriptorSets.add(new DescriptorSet(layouts[i], setsBuf.get(i), this));
			}
		}

		return descriptorSets;
	}

	public void dispose() {
		vkDestroyDescriptorPool(Vulkan.device, handle, null);
	}

	public void disposeDescriptorSet(DescriptorSet set) {
		final HashMap<Integer, Integer> typesToTake = new HashMap<>();

		for (DescriptorSetLayout.Binding binding : set.layout.getBindings()) {
			if (!typesToTake.containsKey(binding.descriptorType)) {
				typesToTake.put(binding.descriptorType, binding.descriptorCount);
			} else {
				typesToTake.put(binding.descriptorType, typesToTake.get(binding.descriptorType) + binding.descriptorCount);
			}
		}

		for (Integer type : typesToTake.values()) {
			types.get(type).amountLeft += typesToTake.get(type);
		}

		vkFreeDescriptorSets(Vulkan.device, handle, set.getHandle());
	}

	public long getHandle() {
		return handle;
	}

	private static class PoolType {
		private final int type;
		private final int poolSize;
		private int amountLeft;

		public PoolType(int type, int poolSize) {
			this.type = type;
			this.poolSize = poolSize;
			this.amountLeft = poolSize;
		}
	}

	public class Builder {
		private Builder() { }

		public Builder setMaxSets(int count) {
			maxSets = count;
			return this;
		}

		public Builder setTypeSize(int type, int size) {
			types.put(type, new PoolType(type, size));
			return this;
		}

		public DescriptorPool build() {
			try (MemoryStack stack = stackPush()) {
				VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.callocStack(types.size(), stack);
				int i = 0;
				for (PoolType poolType : types.values()) {
					poolSize.get(i++).set(poolType.type, poolType.poolSize);
				}

				VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
						.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
						.pPoolSizes(poolSize)
						.maxSets(maxSets);

				LongBuffer longBuf = stack.mallocLong(1);
				VulkanUtils.VkCheck(vkCreateDescriptorPool(Vulkan.device, poolInfo, null, longBuf), "Failed to create descriptor pool");
				handle = longBuf.get(0);
			}

			return DescriptorPool.this;
		}
	}
}
