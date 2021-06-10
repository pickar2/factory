package xyz.sathro.vulkan.models;

import xyz.sathro.vulkan.utils.DisposalQueue;

public interface IDisposable {
	void dispose();

	default void registerToDisposal() {
		DisposalQueue.registerToDisposal(this);
	}

	default void registerToLateDisposal() {
		DisposalQueue.registerToLateDisposal(this);
	}
}
