package xyz.sathro.vulkan.models;

import xyz.sathro.vulkan.utils.DefaultCommandPools;

public class DefaultCommandPool extends CommandPool implements AutoCloseable {
	public DefaultCommandPool(long handle, long fence) {
		super(handle, fence);
	}

	@Override
	public void close() {
		DefaultCommandPools.returnTransferPool(this);
	}
}
