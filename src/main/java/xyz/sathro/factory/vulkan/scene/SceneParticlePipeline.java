package xyz.sathro.factory.vulkan.scene;

import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.VulkanPipelineBuilder;

import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class SceneParticlePipeline {
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

	public void createDescriptorSets(VulkanBuffer[] cameraBuffers, VulkanBuffer[] particleBuffers) {
		final List<DescriptorSet> descriptorSets = descriptorPool.createDescriptorSets(descriptorSetLayout, Vulkan.swapChainImageCount);

		for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
			descriptorSets.get(i).updateBuilder()
					.write(0).buffer(cameraBuffers[i]).add()
					.write(1).buffer(particleBuffers[i]).add()
					.update();
		}

		this.descriptorSets = descriptorSets;
	}

	public void createPipelines() {
		pipelines = new VulkanPipelineBuilder(new String[] { "shaders/physics/particle_shader.vert" }, new String[] { "shaders/physics/particle_shader.frag" }, new SceneParticleVertex(), new DescriptorSetLayout[] { descriptorSetLayout })
				.setCullMode(VK_CULL_MODE_BACK_BIT)
				.setPolygonMode(VK_POLYGON_MODE_POINT)
				.setTopology(VK_PRIMITIVE_TOPOLOGY_POINT_LIST)
				.build();
	}
}
