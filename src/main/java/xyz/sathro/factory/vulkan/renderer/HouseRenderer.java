package xyz.sathro.factory.vulkan.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.vulkan.Vulkan;
import xyz.sathro.factory.vulkan.descriptors.DescriptorPool;
import xyz.sathro.factory.vulkan.descriptors.DescriptorSet;
import xyz.sathro.factory.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.factory.vulkan.models.IUniformBufferObject;
import xyz.sathro.factory.vulkan.models.VulkanBuffer;
import xyz.sathro.factory.vulkan.models.VulkanImage;
import xyz.sathro.factory.vulkan.models.VulkanPipeline;
import xyz.sathro.factory.vulkan.utils.ModelLoader;
import xyz.sathro.factory.vulkan.utils.VulkanUtils;
import xyz.sathro.factory.vulkan.vertex.HouseVertex;
import xyz.sathro.factory.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.assimp.Assimp.aiProcess_DropNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_FlipUVs;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class HouseRenderer implements IRenderer {
	public static final HouseRenderer INSTANCE = new HouseRenderer();

	private boolean dirty;

	private VulkanPipeline[] graphicPipelines;

	private DescriptorPool descriptorPool;
	private DescriptorSetLayout descriptorSetLayout;
	private List<DescriptorSet> descriptorSets;

	private long commandPool;
	private VkCommandBuffer[][] commandBuffers;

	public VulkanBuffer vertexBuffer;
	public VulkanBuffer indexBuffer;
	private VulkanBuffer[] uniformBuffers;

	private IVertex[] vertices;
	private int[] indices;

	private long textureSampler;
	private VulkanImage textureImage;
	private boolean cbChanged = false;

	private HouseRenderer() { }

	private VulkanBuffer[] createUniformBuffers() {
		VulkanBuffer[] uniformBuffers = new VulkanBuffer[Vulkan.swapChainImages.size()];

		for (int i = 0; i < Vulkan.swapChainImages.size(); i++) {
			uniformBuffers[i] = Vulkan.createBuffer(UBO.SIZEOF, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		}

		return uniformBuffers;
	}

	private void prepareCommandBuffers() {
		cbChanged = true;
		try (MemoryStack stack = stackPush()) {
			LongBuffer vertexBuffer = stack.longs(this.vertexBuffer.buffer);
			LongBuffer offsets = stack.longs(0);

			for (int i = 0; i < commandBuffers.length; i++) {
				VkCommandBuffer commandBuffer = commandBuffers[i][0];

				VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.callocStack(stack)
						.renderPass(Vulkan.renderPass)
						.framebuffer(Vulkan.swapChainFramebuffers.get(i))
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);

				VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
						.pInheritanceInfo(inheritanceInfo)
						.flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

				VulkanUtils.VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");

				for (VulkanPipeline graphicPipeline : graphicPipelines) {
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.pipeline);

					vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffer, offsets);
					vkCmdBindIndexBuffer(commandBuffer, indexBuffer.buffer, 0, VK_INDEX_TYPE_UINT32);

					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 0, stack.longs(descriptorSets.get(i).getHandle()), null);

					vkCmdDrawIndexed(commandBuffer, indices.length, 1, 0, 0, 0);
				}

				VulkanUtils.VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to record command buffer");
			}
		}
	}

	private VkCommandBuffer[][] createCommandBuffers() {
		VkCommandBuffer[] buffers = Vulkan.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_SECONDARY, Vulkan.swapChainImages.size(), commandPool);

		VkCommandBuffer[][] commandBuffers = new VkCommandBuffer[Vulkan.swapChainImages.size()][];
		for (int i = 0; i < commandBuffers.length; i++) {
			commandBuffers[i] = new VkCommandBuffer[] { buffers[i] };
		}

		return commandBuffers;
	}

	private DescriptorPool createDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImages.size())
				.setTypeSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, Vulkan.swapChainImages.size())
				.setMaxSets(Vulkan.swapChainImages.size())
				.build();
	}

	private DescriptorSetLayout createDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
				.build();
	}

	private List<DescriptorSet> createDescriptorSets() {
		final List<DescriptorSet> descriptorSets = descriptorPool.createDescriptorSets(descriptorSetLayout, Vulkan.swapChainImages.size());

		try (MemoryStack stack = stackPush()) {
			VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.offset(0)
					.range(UBO.SIZEOF);

			VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
					.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
					.imageView(textureImage.imageView)
					.sampler(textureSampler);

			for (int i = 0; i < descriptorSets.size(); i++) {
				DescriptorSet descriptorSet = descriptorSets.get(i);

				bufferInfo.buffer(uniformBuffers[i].buffer);

				descriptorSet.updateBuilder()
						.addWrite(0, 1, 0).pBufferInfo(bufferInfo).add()
						.addWrite(0, 1, 1).pImageInfo(imageInfo).add()
						.update();
			}
		}

		return descriptorSets;
	}

	private VulkanPipeline[] createPipelines() {
		VulkanPipeline[] pipelines = new VulkanPipeline[2];

		long vertShaderModule = Vulkan.createShaderModule("shaders/26_shader_depth.vert", VK_SHADER_STAGE_VERTEX_BIT);
		long fragShaderModule = Vulkan.createShaderModule("shaders/26_shader_depth.frag", VK_SHADER_STAGE_FRAGMENT_BIT);

		VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(new long[] { vertShaderModule }, new long[] { fragShaderModule });

		VkPipelineVertexInputStateCreateInfo vertexInputInfo = Vulkan.createVertexInputInfo(new HouseVertex());
		VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
				.primitiveRestartEnable(false);

		VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		VkPipelineRasterizationStateCreateInfo rasterizer = Vulkan.createRasterizer(VK_POLYGON_MODE_FILL, 1.0f, VK_CULL_MODE_BACK_BIT)
				.depthClampEnable(false)
				.rasterizerDiscardEnable(false)
				.depthBiasEnable(false);

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

		pipelines[0] = Vulkan.createGraphicsPipeline(HouseVertex.class, shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingDepth, pipelineLayout, depthStencilDepth); //depth write pipeline
		pipelines[1] = Vulkan.createGraphicsPipeline(HouseVertex.class, shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		vkDestroyShaderModule(Vulkan.device, vertShaderModule, null);
		vkDestroyShaderModule(Vulkan.device, fragShaderModule, null);

		return pipelines;
	}

	private void loadModel() {
		ModelLoader.Model model = ModelLoader.loadModel("models/chalet.obj", aiProcess_FlipUVs | aiProcess_DropNormals);

		final int vertexCount = model.positions.size();

		vertices = new HouseVertex[vertexCount];

		final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

		for (int i = 0; i < vertexCount; i++) {
			vertices[i] = new HouseVertex(model.positions.get(i), color, model.texCoords.get(i));
		}

		indices = model.indices.toIntArray();
	}

	@Override
	public void init() {
		commandPool = Vulkan.createCommandPool(0, Vulkan.queues.graphics);
		descriptorSetLayout = createDescriptorSetLayout();
		textureImage = Vulkan.createTextureImage("textures/chalet.jpg");
		textureSampler = Vulkan.createTextureSampler();
		loadModel();
		vertexBuffer = Vulkan.createVertexBuffer(Arrays.asList(vertices));
		indexBuffer = Vulkan.createIndexBuffer(indices);

		createSwapChain();
	}

	@Override
	public VkCommandBuffer[] getCommandBuffers(int imageIndex) {
		return commandBuffers[imageIndex];
	}

	@Override
	public boolean commandBuffersChanged() {
		if (cbChanged) {
			cbChanged = false;
			return true;
		}

		return false;
	}

	@Override
	public void beforeFrame(int imageIndex) {
		try (final MemoryStack stack = stackPush()) {
			final UBO ubo = new UBO();

			ubo.model.rotate((float) (glfwGetTime() * Math.toRadians(90)), 0.0f, 0.0f, 1.0f);
			ubo.view.lookAt(2.0f, 2.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
			ubo.proj.perspective((float) Math.toRadians(45), (float) Vulkan.swapChainExtent.width() / (float) Vulkan.swapChainExtent.height(), 0.1f, 10.0f);
			ubo.proj.m11(ubo.proj.m11() * -1);

			final PointerBuffer data = stack.mallocPointer(1);
			vmaMapMemory(Vulkan.vmaAllocator, uniformBuffers[imageIndex].allocation, data);
			final ByteBuffer buffer = data.getByteBuffer(0, ubo.sizeof());
			ubo.get(0, buffer);
			vmaUnmapMemory(Vulkan.vmaAllocator, uniformBuffers[imageIndex].allocation);
		}
		if (dirty) {
			dirty = false;
			vkResetCommandPool(Vulkan.device, commandPool, 0);
			prepareCommandBuffers();
		}
	}

	@Override
	public void afterFrame(int imageIndex) {
		//void
	}

	@Override
	public void createSwapChain() {
		commandBuffers = createCommandBuffers();

		uniformBuffers = createUniformBuffers();

		descriptorPool = createDescriptorPool();
		descriptorSets = createDescriptorSets();

		graphicPipelines = createPipelines();
		this.dirty = true;
	}

	@Override
	public void cleanupSwapChain() {
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool, commandBuffer[0]);
		}

		Arrays.stream(uniformBuffers).forEach(VulkanBuffer::dispose);

//		descriptorSets.forEach(DescriptorSet::dispose);
		descriptorPool.dispose();

		Arrays.stream(graphicPipelines).forEach(VulkanPipeline::dispose);
	}

	@Override
	public void dispose() {
		descriptorSetLayout.dispose();
		textureImage.dispose();
		vkDestroySampler(Vulkan.device, textureSampler, null);
		vkDestroyCommandPool(Vulkan.device, commandPool, null);
		vertexBuffer.dispose();
		indexBuffer.dispose();
	}

	private static class UBO implements IUniformBufferObject {
		public static final int SIZEOF = 3 * 16 * Float.BYTES;
		public static final int MATRIX_OFFSET = 16 * Float.BYTES;

		public final Matrix4f model = new Matrix4f();
		public final Matrix4f view = new Matrix4f();
		public final Matrix4f proj = new Matrix4f();

		@Override
		public int sizeof() {
			return SIZEOF;
		}

		@Override
		public void get(int index, ByteBuffer buffer) {
			model.get(index, buffer);
			view.get(index + MATRIX_OFFSET, buffer);
			proj.get(index + MATRIX_OFFSET * 2, buffer);
		}
	}
}
