package xyz.sathro.factory.vulkan.vertex;

import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;

public interface IVertex {
	VkVertexInputBindingDescription.Buffer getBindingDescription();

	VkVertexInputAttributeDescription.Buffer getAttributeDescriptions();

	void get(ByteBuffer buffer);

	default void dispose() {
		getBindingDescription().free();
		getAttributeDescriptions().free();
	}

	int sizeof();

	IVertex copy();
}
