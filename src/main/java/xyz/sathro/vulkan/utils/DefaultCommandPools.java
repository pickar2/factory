package xyz.sathro.vulkan.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.DefaultCommandPool;

import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
import static xyz.sathro.vulkan.Vulkan.*;

public class DefaultCommandPools {
	private static final ObjectList<DefaultCommandPool> transferCommandPools = new ObjectArrayList<>();

	private DefaultCommandPools() { }

	public static DefaultCommandPool takeTransferPool() {
		synchronized (transferCommandPools) {
			if (!transferCommandPools.isEmpty()) {
				return transferCommandPools.remove(transferCommandPools.size() - 1);
			}
		}

		return new DefaultCommandPool(createCommandPool(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, queues.transfer), createDefaultFence(true));
	}

	public static void returnTransferPool(DefaultCommandPool commandPool) {
		synchronized (transferCommandPools) {
			transferCommandPools.add(commandPool);
		}
	}

	public static void dispose() {
		transferCommandPools.forEach(CommandPool::dispose);
	}
}
