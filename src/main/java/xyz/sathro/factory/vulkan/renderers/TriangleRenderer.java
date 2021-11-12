package xyz.sathro.factory.vulkan.renderers;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.vulkan.vertices.HouseVertex;
import xyz.sathro.factory.vulkan.vertices.TriangleVertex;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.IBufferObject;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.renderer.IRenderer;
import xyz.sathro.vulkan.utils.CommandBuffers;
import xyz.sathro.vulkan.utils.VulkanUtils;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class TriangleRenderer implements IRenderer {
	public static final TriangleRenderer INSTANCE = new TriangleRenderer();

	private boolean dirty;

	private VulkanPipeline[] graphicPipelines;

	private long descriptorPoolHandle;
	private long descriptorSetLayout;
	private List<Long> descriptorSets;

	private CommandPool commandPool;
	private VkCommandBuffer[][] commandBuffers;

	public VulkanBuffer vertexBuffer;
	private VulkanBuffer[] uniformBuffers;

	private boolean cbChanged = false;

	private TriangleRenderer() { }

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

					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 0, stack.longs(descriptorSets.get(i)), null);

					vkCmdDraw(commandBuffer, 3, 1, 0, 0);
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

	private long createDescriptorPool() {
		try (MemoryStack stack = stackPush()) {
			VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.callocStack(2, stack);

			poolSize.get(0).set(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImageCount);
			poolSize.get(1).set(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, Vulkan.swapChainImageCount);

			VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
					.pPoolSizes(poolSize)
					.maxSets(Vulkan.swapChainImageCount);

			LongBuffer longBuf = stack.mallocLong(1);
			VulkanUtils.VkCheck(vkCreateDescriptorPool(Vulkan.device, poolInfo, null, longBuf), "Failed to create descriptor pool");

			return longBuf.get(0);
		}
	}

	private long createDescriptorSetLayout() {
		try (MemoryStack stack = stackPush()) {
			VkDescriptorSetLayoutBinding.Buffer vkBindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);

			vkBindings.get(0).set(0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, VK_SHADER_STAGE_VERTEX_BIT, null);

			VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
					.pBindings(vkBindings);

			LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

			VulkanUtils.VkCheck(vkCreateDescriptorSetLayout(Vulkan.device, layoutInfo, null, pDescriptorSetLayout), "Failed to create descriptor set layout");

			return pDescriptorSetLayout.get(0);
		}
	}

	private List<Long> createDescriptorSets() {
		try (MemoryStack stack = stackPush()) {
			LongBuffer layoutsBuf = stack.mallocLong(Vulkan.swapChainImageCount);
			for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
				layoutsBuf.put(i, descriptorSetLayout);
			}

			VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
					.descriptorPool(descriptorPoolHandle)
					.pSetLayouts(layoutsBuf);

			LongBuffer setsBuf = stack.mallocLong(Vulkan.swapChainImageCount);

			VulkanUtils.VkCheck(VK10.vkAllocateDescriptorSets(Vulkan.device, allocInfo, setsBuf), "Failed to allocate descriptor sets");

			List<Long> descriptorSets = new ArrayList<>(Vulkan.swapChainImageCount);
			for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
				descriptorSets.add(i, setsBuf.get(i));
			}

			VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.offset(0)
					.range(UBO.SIZEOF);

			VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.callocStack(1, stack)
					.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
					.descriptorCount(1)
					.dstArrayElement(0)
					.pBufferInfo(bufferInfo)
					.dstBinding(0)
					.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

			for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
				descriptorWrites.dstSet(descriptorSets.get(i));
				bufferInfo.buffer(uniformBuffers[i].buffer);
				vkUpdateDescriptorSets(Vulkan.device, descriptorWrites, null);
			}

			return descriptorSets;
		}
	}

	private VulkanPipeline[] createPipelines() {
		final VulkanPipeline[] pipelines = new VulkanPipeline[1];

		final long vertShaderModule = Vulkan.createShaderModule("shaders/triangle.vert", VK_SHADER_STAGE_VERTEX_BIT);
		final long fragShaderModule = Vulkan.createShaderModule("shaders/triangle.frag", VK_SHADER_STAGE_FRAGMENT_BIT);

		final VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(new long[] { vertShaderModule }, new long[] { fragShaderModule });

		final VkPipelineVertexInputStateCreateInfo vertexInputInfo = TriangleVertex.vertexInfo.getVertexCreateInfo();
		final VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
				.primitiveRestartEnable(false);

		final VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		final VkPipelineRasterizationStateCreateInfo rasterizer = Vulkan.createRasterizer(VK_POLYGON_MODE_FILL, 1.0f, VK_CULL_MODE_NONE)
				.depthClampEnable(false)
				.rasterizerDiscardEnable(false)
				.depthBiasEnable(false);

		final VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
				.sampleShadingEnable(false)
				.minSampleShading(0.2f)
				.rasterizationSamples(Vulkan.msaaSamples);

		final VkPipelineColorBlendStateCreateInfo colorBlendingColor = Vulkan.createColorBlending(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

		final VkPipelineLayoutCreateInfo pipelineLayout = Vulkan.createPipelineLayout(descriptorSetLayout, null);

		final VkPipelineDepthStencilStateCreateInfo depthStencilColor = Vulkan.createDepthStencil(VK_COMPARE_OP_ALWAYS, false);

		pipelines[0] = Vulkan.createGraphicsPipeline(HouseVertex.class, shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		vkDestroyShaderModule(Vulkan.device, vertShaderModule, null);
		vkDestroyShaderModule(Vulkan.device, fragShaderModule, null);

		return pipelines;
	}

	@Override
	public void init() {
		commandPool = CommandPool.newDefault(0, Vulkan.queues.graphics);
		descriptorSetLayout = createDescriptorSetLayout();

		final IVertex[] vertices = new TriangleVertex[3];
		vertices[0] = new TriangleVertex(new Vector3f(0, -0.5f, -0.5f));
		vertices[1] = new TriangleVertex(new Vector3f(0, .5f, 0));
		vertices[2] = new TriangleVertex(new Vector3f(0, -0.5f, 0.5f));

		vertexBuffer = Vulkan.createVertexBuffer(Arrays.asList(vertices));

		commandBuffers = createCommandBuffers();

		uniformBuffers = createUniformBuffers();

		descriptorPoolHandle = createDescriptorPool();
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

			ubo.model.rotate((float) (glfwGetTime() * Math.toRadians(90)), 1.2f, 1.0f, 0f);
			ubo.view.lookAt(-3f, 0f, 0, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
			ubo.proj.perspective((float) Math.toRadians(45), (float) Vulkan.swapChainExtent.width() / (float) Vulkan.swapChainExtent.height(), 0.1f, 10.0f);
			ubo.proj.m11(ubo.proj.m11() * -1);

			ubo.time = glfwGetTime();

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
//		descriptorSetLayout.dispose();
//		descriptorPool.dispose();
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool.handle, commandBuffer[0]);
		}
		commandPool.dispose();
		vertexBuffer.dispose();
	}

	private static class UBO implements IBufferObject {
		public static final int SIZEOF = 3 * 16 * Float.BYTES + Float.BYTES;
		public static final int MATRIX_OFFSET = 16 * Float.BYTES;
		public static final int TIME_OFFSET = 3 * 16 * Float.BYTES;

		public final Matrix4f model = new Matrix4f();
		public final Matrix4f view = new Matrix4f();
		public final Matrix4f proj = new Matrix4f();
		public double time = 0;

		@Override
		public int sizeof() {
			return SIZEOF;
		}

		@Override
		public void get(int index, ByteBuffer buffer) {
			model.get(index, buffer);
			view.get(index + MATRIX_OFFSET, buffer);
			proj.get(index + MATRIX_OFFSET * 2, buffer);
			buffer.putFloat(index + TIME_OFFSET, (float) time);
		}
	}
}
