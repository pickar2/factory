package xyz.sathro.vulkan.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import xyz.sathro.vulkan.models.CommandPool;

import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
import static xyz.sathro.vulkan.Vulkan.queues;

public class DefaultCommandPools {
	private static final ObjectList<CommandPool> transferCommandPools = new ObjectArrayList<>();

	private DefaultCommandPools() { }

	public static CommandPool takeTransferPool() {
		synchronized (transferCommandPools) {
			if (!transferCommandPools.isEmpty()) {
				return transferCommandPools.remove(transferCommandPools.size() - 1);
			}
		}

		return CommandPool.newDefault(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, queues.transfer);
	}

	public static void returnTransferPool(CommandPool commandPool) {
		synchronized (transferCommandPools) {
			transferCommandPools.add(commandPool);
		}
	}

	public static void dispose() {
		transferCommandPools.forEach(CommandPool::dispose);
	}
}
