package xyz.sathro.factory.vulkan.renderers;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.vulkan.vertices.HouseVertex;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.*;
import xyz.sathro.vulkan.renderer.IRenderer;
import xyz.sathro.vulkan.utils.CommandBuffers;
import xyz.sathro.vulkan.utils.ModelLoader;
import xyz.sathro.vulkan.utils.VulkanUtils;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;
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

//	private long commandPoolHandle;
	private CommandPool commandPool;
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
		VulkanBuffer[] uniformBuffers = new VulkanBuffer[Vulkan.swapChainImageCount];

		for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
			uniformBuffers[i] = Vulkan.createBuffer(UBO.SIZEOF, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		}

		return uniformBuffers;
	}

	private void prepareCommandBuffers() {
		cbChanged = true;
		try (MemoryStack stack = stackPush()) {
			final LongBuffer vertexBuffer = stack.longs(this.vertexBuffer.buffer);
			final LongBuffer offsets = stack.longs(0);

			for (int i = 0; i < commandBuffers.length; i++) {
				final VkCommandBuffer commandBuffer = commandBuffers[i][0];

				final VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.callocStack(stack)
						.renderPass(Vulkan.renderPass)
						.framebuffer(Vulkan.swapChainFramebuffers.getLong(i))
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);

				final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
						.pInheritanceInfo(inheritanceInfo)
						.flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

				VulkanUtils.VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");

				for (VulkanPipeline graphicPipeline : graphicPipelines) {
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.handle);

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
		VkCommandBuffer[] buffers = CommandBuffers.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_SECONDARY, Vulkan.swapChainImageCount, commandPool.handle);

		VkCommandBuffer[][] commandBuffers = new VkCommandBuffer[Vulkan.swapChainImageCount][];
		for (int i = 0; i < commandBuffers.length; i++) {
			commandBuffers[i] = new VkCommandBuffer[] { buffers[i] };
		}

		return commandBuffers;
	}

	private DescriptorPool createDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImageCount)
				.setTypeSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, Vulkan.swapChainImageCount)
				.setMaxSets(Vulkan.swapChainImageCount)
				.build();
	}

	private DescriptorSetLayout createDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
				.build();
	}

	private List<DescriptorSet> createDescriptorSets() {
		final List<DescriptorSet> descriptorSets = descriptorPool.createDescriptorSets(descriptorSetLayout, Vulkan.swapChainImageCount);

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
						.write(0).pBufferInfo(bufferInfo).add()
						.write(1).pImageInfo(imageInfo).add()
						.update();
			}
		}

		return descriptorSets;
	}

	private VulkanPipeline[] createPipelines() {
		final VulkanPipeline[] pipelines = new VulkanPipeline[2];

		final long vertShaderModule = Vulkan.createShaderModule("shaders/house_shader.vert", VK_SHADER_STAGE_VERTEX_BIT);
		final long fragShaderModule = Vulkan.createShaderModule("shaders/house_shader.frag", VK_SHADER_STAGE_FRAGMENT_BIT);

		final VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(new long[] { vertShaderModule }, new long[] { fragShaderModule });

		final VkPipelineVertexInputStateCreateInfo vertexInputInfo = HouseVertex.vertexInfo.getVertexCreateInfo();
		final VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
				.primitiveRestartEnable(false);

		final VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		final VkPipelineRasterizationStateCreateInfo rasterizer = Vulkan.createRasterizer(VK_POLYGON_MODE_FILL, 1.0f, VK_CULL_MODE_BACK_BIT)
				.depthClampEnable(false)
				.rasterizerDiscardEnable(false)
				.depthBiasEnable(false);

		final VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
				.sampleShadingEnable(false)
				.minSampleShading(0.2f)
				.rasterizationSamples(Vulkan.msaaSamples);

		final VkPipelineColorBlendStateCreateInfo colorBlendingDepth = Vulkan.createColorBlending(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
		final VkPipelineColorBlendStateCreateInfo colorBlendingColor = Vulkan.createColorBlending(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

		final VkPipelineLayoutCreateInfo pipelineLayout = Vulkan.createPipelineLayout(descriptorSetLayout.getHandle(), null);

		final VkPipelineDepthStencilStateCreateInfo depthStencilDepth = Vulkan.createDepthStencil(VK_COMPARE_OP_LESS, true);
		final VkPipelineDepthStencilStateCreateInfo depthStencilColor = Vulkan.createDepthStencil(VK_COMPARE_OP_EQUAL, false);

		pipelines[0] = Vulkan.createGraphicsPipeline(HouseVertex.class, shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingDepth, pipelineLayout, depthStencilDepth); //depth write pipeline
		pipelines[1] = Vulkan.createGraphicsPipeline(HouseVertex.class, shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		vkDestroyShaderModule(Vulkan.device, vertShaderModule, null);
		vkDestroyShaderModule(Vulkan.device, fragShaderModule, null);

		return pipelines;
	}

	private void loadModel() {
		final ModelLoader.Model model = ModelLoader.loadModel("models/chalet.obj", aiProcess_FlipUVs | aiProcess_DropNormals | aiProcess_OptimizeGraph | aiProcess_OptimizeMeshes);

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
		commandPool = CommandPool.newDefault(0, Vulkan.queues.graphics);
		descriptorSetLayout = createDescriptorSetLayout();
		textureImage = Vulkan.createTextureImage("textures/chalet.jpg");
		textureSampler = Vulkan.createTextureSampler(16);
		loadModel();
		vertexBuffer = Vulkan.createVertexBuffer(Arrays.asList(vertices));
		indexBuffer = Vulkan.createIndexBuffer(indices);

		commandBuffers = createCommandBuffers();

		uniformBuffers = createUniformBuffers();

		descriptorPool = createDescriptorPool();
		descriptorSets = createDescriptorSets();
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
			vkResetCommandPool(Vulkan.device, commandPool.handle, 0);
			prepareCommandBuffers();
		}
	}

	@Override
	public void afterFrame(int imageIndex) {
		//void
	}

	@Override
	public void createSwapChain() {
		graphicPipelines = createPipelines();
		this.dirty = true;
	}

	@Override
	public void cleanupSwapChain() {
		Arrays.stream(graphicPipelines).forEach(VulkanPipeline::dispose);
	}

	@Override
	public void dispose() {
		Arrays.stream(uniformBuffers).forEach(VulkanBuffer::dispose);
		descriptorSetLayout.dispose();
		descriptorPool.dispose();
		textureImage.dispose();
		vkDestroySampler(Vulkan.device, textureSampler, null);
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool.handle, commandBuffer[0]);
		}
		commandPool.dispose();
		vertexBuffer.dispose();
		indexBuffer.dispose();
	}

	private static class UBO implements IBufferObject {
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
