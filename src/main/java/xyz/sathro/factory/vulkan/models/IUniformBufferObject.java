package xyz.sathro.factory.vulkan.models;

import java.nio.ByteBuffer;

public interface IUniformBufferObject {
	int sizeof();

	void get(int index, ByteBuffer buffer);
}
