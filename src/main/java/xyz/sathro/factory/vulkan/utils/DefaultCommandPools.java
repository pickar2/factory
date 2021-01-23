package xyz.sathro.factory.vulkan.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import xyz.sathro.factory.vulkan.models.CommandPool;

import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
import static xyz.sathro.factory.vulkan.Vulkan.queues;

public class DefaultCommandPools {
	private static final List<CommandPool> transferCommandPools = new ObjectArrayList<>();

	private DefaultCommandPools() { }

	public static CommandPool takeTransferPool() {
		synchronized (transferCommandPools) {
			if (!transferCommandPools.isEmpty()) {
				return transferCommandPools.remove(0);
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
