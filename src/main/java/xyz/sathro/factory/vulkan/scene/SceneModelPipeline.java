package xyz.sathro.factory.vulkan.scene;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.VulkanPipelineBuilder;

import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class SceneModelPipeline {
	public DescriptorPool descriptorPool;
	public DescriptorSetLayout descriptorSetLayout;
	public List<DescriptorSet> descriptorSets;

	public VulkanPipeline[] pipelines;

	public void createDescriptorPool() {
		descriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImageCount)
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, Vulkan.swapChainImageCount)
				.setMaxSets(Vulkan.swapChainImageCount)
				.build();
	}

	public void createDescriptorSetLayout() {
		descriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, VK_SHADER_STAGE_VERTEX_BIT)
				.build();
	}

	public void createDescriptorSets(VulkanBuffer[] cameraBuffers, VulkanBuffer[] modelBuffers) {
		final List<DescriptorSet> descriptorSets = descriptorPool.createDescriptorSets(descriptorSetLayout, Vulkan.swapChainImageCount);

		try (MemoryStack stack = stackPush()) {
			final VkDescriptorBufferInfo.Buffer cameraBufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			final VkDescriptorBufferInfo.Buffer modelBufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
				final DescriptorSet descriptorSet = descriptorSets.get(i);

				cameraBufferInfo.buffer(cameraBuffers[i].handle);
				modelBufferInfo.buffer(modelBuffers[i].handle);

				descriptorSet.updateBuilder()
						.write(0).pBufferInfo(cameraBufferInfo).add()
						.write(1).pBufferInfo(modelBufferInfo).add()
						.update();
			}
		}

		this.descriptorSets = descriptorSets;
	}

	public void createPipelines() {
		pipelines = new VulkanPipelineBuilder(new String[] { "shaders/physics/model_shader.vert" }, new String[] { "shaders/physics/model_shader.frag" }, new SceneModelVertex(), new DescriptorSetLayout[] { descriptorSetLayout })
				.setCullMode(VK_CULL_MODE_BACK_BIT)
				.setPolygonMode(VK_POLYGON_MODE_FILL)
				.setTopology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
				.build();
	}
}
