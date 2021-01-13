package xyz.sathro.factory.vulkan.models;

import org.lwjgl.vulkan.*;
import xyz.sathro.factory.vulkan.Vulkan;
import xyz.sathro.factory.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.factory.vulkan.vertex.IVertex;

import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.*;

// TODO: Move to VulkanPipeline class?
public class VulkanPipelineBuilder {
	private final long[] vertShaders, fragShaders;
	private final IVertex vertex;
	private final DescriptorSetLayout descriptorSetLayout;
	private int topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
	private int polygonMode = VK_POLYGON_MODE_FILL;
	private float lineWidth = 1.0f;
	private int cullMode = VK_CULL_MODE_NONE;
	private VkPipelineRasterizationStateCreateInfo rasterizer = null;

	// TODO: change vertexInstance to some IVertexFabric?
	public VulkanPipelineBuilder(String[] vertShaderPaths, String[] fragShaderPaths, IVertex vertexInstance, DescriptorSetLayout descriptorSetLayout) {
		vertShaders = new long[vertShaderPaths.length];
		for (int i = 0; i < vertShaderPaths.length; i++) {
			vertShaders[i] = Vulkan.createShaderModule(vertShaderPaths[i], VK_SHADER_STAGE_VERTEX_BIT);
		}

		fragShaders = new long[fragShaderPaths.length];
		for (int i = 0; i < fragShaderPaths.length; i++) {
			fragShaders[i] = Vulkan.createShaderModule(fragShaderPaths[i], VK_SHADER_STAGE_FRAGMENT_BIT);
		}

		this.vertex = vertexInstance;
		this.descriptorSetLayout = descriptorSetLayout;
	}

	public VulkanPipelineBuilder setTopology(int topology) {
		this.topology = topology;
		return this;
	}

	public VulkanPipelineBuilder setPolygonMode(int polygonMode) {
		this.polygonMode = polygonMode;
		if (rasterizer != null) {
			rasterizer.polygonMode(polygonMode);
		}

		return this;
	}

	public VulkanPipelineBuilder setLineWidth(float lineWidth) {
		this.lineWidth = lineWidth;
		if (rasterizer != null) {
			rasterizer.lineWidth(lineWidth);
		}

		return this;
	}

	public VulkanPipelineBuilder setCullMode(int cullMode) {
		this.cullMode = cullMode;
		if (rasterizer != null) {
			rasterizer.cullMode(cullMode);
		}

		return this;
	}

	// TODO: This just looks bad IMO.
	public VulkanPipelineBuilder setRasterizer(Consumer<VkPipelineRasterizationStateCreateInfo> rasterizerConsumer) {
		rasterizer = Vulkan.createRasterizer(polygonMode, lineWidth, cullMode);
		rasterizerConsumer.accept(rasterizer);

		return this;
	}

	// TODO: Use one MemoryStack.
	// TODO: Move defaults in one place.
	// TODO: Provide ability to change all required structures.
	// TODO: By default it should create just one pipeline without depth stencil.
	public VulkanPipeline[] build() {
		VulkanPipeline[] pipelines = new VulkanPipeline[2];

		VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(vertShaders, fragShaders);

		VkPipelineVertexInputStateCreateInfo vertexInputInfo = Vulkan.createVertexInputInfo(vertex);
		VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(topology)
				.primitiveRestartEnable(false);

		VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		if (rasterizer == null) {
			rasterizer = Vulkan.createRasterizer(polygonMode, lineWidth, cullMode)
					.depthClampEnable(false)
					.rasterizerDiscardEnable(false)
					.depthBiasEnable(false);
		}

		VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
				.sampleShadingEnable(false)
				.minSampleShading(0.2f)
				.rasterizationSamples(Vulkan.msaaSamples);

		VkPipelineColorBlendStateCreateInfo colorBlendingDepth = Vulkan.createColorBlending(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
		VkPipelineColorBlendStateCreateInfo colorBlendingColor = Vulkan.createColorBlending(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

		VkPipelineLayoutCreateInfo pipelineLayout = Vulkan.createPipelineLayout(descriptorSetLayout.getHandle(), null);

		VkPipelineDepthStencilStateCreateInfo depthStencilDepth = Vulkan.createDepthStencil(VK_COMPARE_OP_LESS, true);
		VkPipelineDepthStencilStateCreateInfo depthStencilColor = Vulkan.createDepthStencil(VK_COMPARE_OP_EQUAL, false);

		pipelines[0] = Vulkan.createGraphicsPipeline(vertex.getClass(), shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingDepth, pipelineLayout, depthStencilDepth); //depth write pipeline
		pipelines[1] = Vulkan.createGraphicsPipeline(vertex.getClass(), shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		for (long vertShader : vertShaders) {
			vkDestroyShaderModule(Vulkan.device, vertShader, null);
		}
		for (long fragShader : fragShaders) {
			vkDestroyShaderModule(Vulkan.device, fragShader, null);
		}

		return pipelines;
	}
}
