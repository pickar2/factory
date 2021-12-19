package xyz.sathro.vulkan.descriptors;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.utils.VulkanUtils;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorPool implements IDisposable {
	private final Int2ObjectMap<PoolType> types = new Int2ObjectOpenHashMap<>();
	@Getter private int maxSets = 1;
	@Getter private long handle;

	private DescriptorPool() { }

	public static Builder builder() {
		return new DescriptorPool().new Builder();
	}

	public List<DescriptorSet> createDescriptorSets(DescriptorSetLayout layout, int count) {
		final DescriptorSetLayout[] layouts = new DescriptorSetLayout[count];
		for (int i = 0; i < count; i++) {
			layouts[i] = layout;
		}

		return createDescriptorSets(layouts);
	}

	public List<DescriptorSet> createDescriptorSets(DescriptorSetLayout... layouts) {
		final Int2IntMap typesToTake = new Int2IntOpenHashMap();

		for (DescriptorSetLayout layout : layouts) {
			for (DescriptorSetLayout.Binding binding : layout.getBindings()) {
				if (!typesToTake.containsKey(binding.descriptorType)) {
					typesToTake.put(binding.descriptorType, binding.descriptorCount);
				} else {
					typesToTake.put(binding.descriptorType, typesToTake.get(binding.descriptorType) + binding.descriptorCount);
				}
			}
		}

		for (int type : typesToTake.keySet()) {
			final PoolType poolType = types.get(type);
			if (poolType == null) {
				throw new IllegalArgumentException("This pool doesn't accept " + DescriptorType.getByCode(type));
			}
			if (poolType.amountLeft < typesToTake.get(type)) {
				throw new IllegalStateException("This pool doesn't have enough space for " + DescriptorType.getByCode(type));
			}
			poolType.amountLeft -= typesToTake.get(type);
		}

		final List<DescriptorSet> descriptorSets = new ObjectArrayList<>();
		try (MemoryStack stack = stackPush()) {
			final LongBuffer layoutsBuf = stack.mallocLong(layouts.length);
			for (DescriptorSetLayout layout : layouts) {
				layoutsBuf.put(layout.getHandle());
			}
			layoutsBuf.flip();

			final VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
					.descriptorPool(handle)
					.pSetLayouts(layoutsBuf);

			final LongBuffer setsBuf = stack.mallocLong(layouts.length);

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
		final Int2IntMap typesToTake = new Int2IntOpenHashMap();

		for (DescriptorSetLayout.Binding binding : set.layout.getBindings()) {
			if (!typesToTake.containsKey(binding.descriptorType)) {
				typesToTake.put(binding.descriptorType, binding.descriptorCount);
			} else {
				typesToTake.put(binding.descriptorType, typesToTake.get(binding.descriptorType) + binding.descriptorCount);
			}
		}

		for (int type : typesToTake.values()) {
			types.get(type).amountLeft += typesToTake.get(type);
		}

		vkFreeDescriptorSets(Vulkan.device, handle, set.getHandle());
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
				final VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(types.size(), stack);
				int i = 0;
				for (PoolType poolType : types.values()) {
					poolSize.get(i++).set(poolType.type, poolType.poolSize);
				}

				final VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
						.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
						.pPoolSizes(poolSize)
						.maxSets(maxSets);

				final LongBuffer longBuf = stack.mallocLong(1);
				VulkanUtils.VkCheck(vkCreateDescriptorPool(Vulkan.device, poolInfo, null, longBuf), "Failed to create descriptor pool");
				handle = longBuf.get(0);
			}

			return DescriptorPool.this;
		}
	}
}
