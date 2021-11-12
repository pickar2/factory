package xyz.sathro.vulkan.models;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;

public class VulkanVertexInfo implements IDisposable {
	@Getter private final VkVertexInputAttributeDescription.Buffer attributeDescriptions;
	@Getter private final VkVertexInputBindingDescription.Buffer bindingDescription;

	@Getter private final VkPipelineVertexInputStateCreateInfo vertexCreateInfo;

	public VulkanVertexInfo(VkVertexInputAttributeDescription.Buffer attributeDescriptions, VkVertexInputBindingDescription.Buffer bindingDescription) {
		this.attributeDescriptions = attributeDescriptions;
		this.bindingDescription = bindingDescription;

		this.vertexCreateInfo = VkPipelineVertexInputStateCreateInfo.calloc()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
				.pVertexAttributeDescriptions(getAttributeDescriptions())
				.pVertexBindingDescriptions(getBindingDescription());
	}

	public static Builder builder() {
		return new Builder();
	}

	public void dispose() {
		vertexCreateInfo.free();
		bindingDescription.free();
		attributeDescriptions.free();
	}

	public static class Builder {
		private final Map<Binding, ObjectList<Attribute>> map = new Object2ObjectOpenHashMap<>();

		private Builder() { }

		public Binding startBinding(int binding, int stride, int inputRate) {
			return new Binding(binding, stride, inputRate);
		}

		public VulkanVertexInfo build() {
			final VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(map.keySet().size());
			final VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(map.values().stream().mapToInt(List::size).reduce(0, Integer::sum));

			for (Map.Entry<Binding, ObjectList<Attribute>> entry : map.entrySet()) {
				final Binding binding = entry.getKey();
				bindingDescription.get().set(binding.binding, binding.stride, binding.inputRate);

				final ObjectList<Attribute> attributes = entry.getValue();
				for (Attribute attribute : attributes) {
					attributeDescriptions.get().set(attribute.location, attribute.binding, attribute.format, attribute.offset);
				}
			}

			bindingDescription.flip();
			attributeDescriptions.flip();

			return new VulkanVertexInfo(attributeDescriptions, bindingDescription);
		}

		@AllArgsConstructor
		public class Binding {
			private final int binding;
			private final int stride;
			private final int inputRate;

			private final ObjectList<Attribute> attributes = new ObjectArrayList<>();

			public Binding addAttribute(int location, int format, int offset) {
				attributes.add(new Attribute(binding, location, format, offset));
				return this;
			}

			public Builder endBinding() {
				Builder.this.map.put(this, attributes);
				return Builder.this;
			}
		}

		@AllArgsConstructor
		private static class Attribute {
			private final int binding;
			private final int location;
			private final int format;
			private final int offset;
		}
	}
}
