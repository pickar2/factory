package xyz.sathro.factory.vulkan.renderer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.collision.Octree;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.Pose;
import xyz.sathro.factory.physics.XPBD;
import xyz.sathro.factory.physics.constraints.Constraint;
import xyz.sathro.factory.physics.constraints.joints.SphericalJoint;
import xyz.sathro.factory.util.Side;
import xyz.sathro.factory.vulkan.Vulkan;
import xyz.sathro.factory.vulkan.descriptors.DescriptorPool;
import xyz.sathro.factory.vulkan.descriptors.DescriptorSet;
import xyz.sathro.factory.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.factory.vulkan.models.VulkanBuffer;
import xyz.sathro.factory.vulkan.models.VulkanPipeline;
import xyz.sathro.factory.vulkan.utils.VulkanUtils;
import xyz.sathro.factory.vulkan.vertex.IVertex;
import xyz.sathro.factory.vulkan.vertex.TestVertex;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class TestSceneRenderer implements IRenderer {
	public static final TestSceneRenderer INSTANCE = new TestSceneRenderer();
	private final List<Body> bodies = new ArrayList<>();
	private final List<Constraint> constraints = new ArrayList<>();
	private boolean dirty;
	private VulkanPipeline[] graphicPipelines;
	private VulkanPipeline[] octreePipelines;
	private DescriptorPool descriptorPool;
	private DescriptorSetLayout descriptorSetLayout;
	private List<DescriptorSet> descriptorSets;
	private DescriptorPool octreeDescriptorPool;
	private DescriptorSetLayout octreeDescriptorSetLayout;
	private List<DescriptorSet> octreeDescriptorSets;
	private long commandPool;
	private VkCommandBuffer[][] commandBuffers;
	public VulkanBuffer vertexBuffer;
	public VulkanBuffer indexBuffer;
	public VulkanBuffer octreeVertexBuffer = new VulkanBuffer();
	private VulkanBuffer[] cameraUniformBuffers;
	private VulkanBuffer[] bodiesUniformBuffers;
	private IVertex[] vertices;
	private int[] indices;
	private final int octreeVertexCount = -1;
	private boolean cbChanged = false;

	private TestSceneRenderer() { }

	private long createCommandPool() {
		return Vulkan.createCommandPool(0, Vulkan.queues.graphics.index);
	}

	private VulkanBuffer[] createUniformBuffers(int size) {
		VulkanBuffer[] uniformBuffers = new VulkanBuffer[Vulkan.swapChainImages.size()];

		for (int i = 0; i < Vulkan.swapChainImages.size(); i++) {
			uniformBuffers[i] = Vulkan.createBuffer(size, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		}

		return uniformBuffers;
	}

	private void prepareCommandBuffers() {
		cbChanged = true;
		try (MemoryStack stack = stackPush()) {
			LongBuffer vertexBuffer = stack.longs(this.vertexBuffer.buffer);
			LongBuffer octreeVertexBuffer = stack.longs(this.octreeVertexBuffer.buffer);
			LongBuffer offsets = stack.longs(0);
			IntBuffer offset = stack.ints(0);

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

					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 0, stack.longs(descriptorSets.get(i).getHandle()), offset);

					vkCmdDrawIndexed(commandBuffer, indices.length, bodies.size(), 0, 0, 0);
				}

				if (octreeVertexCount != -1) {
					for (VulkanPipeline graphicPipeline : octreePipelines) {
						vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.pipeline);

						vkCmdBindVertexBuffers(commandBuffer, 0, octreeVertexBuffer, offsets);

						vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 0, stack.longs(octreeDescriptorSets.get(i).getHandle()), null);

						vkCmdDraw(commandBuffer, octreeVertexCount, 1, 0, 0);
					}
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
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, Vulkan.swapChainImages.size())
				.setMaxSets(Vulkan.swapChainImages.size())
				.build();
	}

	private DescriptorSetLayout createDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, VK_SHADER_STAGE_VERTEX_BIT)
				.build();
	}

	private DescriptorPool createOctreeDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImages.size())
				.setMaxSets(Vulkan.swapChainImages.size())
				.build();
	}

	private DescriptorSetLayout createOctreeDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.build();
	}

	private List<DescriptorSet> createDescriptorSets() {
		final List<DescriptorSet> descriptorSets = descriptorPool.createDescriptorSets(descriptorSetLayout, Vulkan.swapChainImages.size());

		try (MemoryStack stack = stackPush()) {
			VkDescriptorBufferInfo.Buffer cameraBufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			VkDescriptorBufferInfo.Buffer bodiesBufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			for (int i = 0; i < descriptorSets.size(); i++) {
				DescriptorSet descriptorSet = descriptorSets.get(i);

				cameraBufferInfo.buffer(cameraUniformBuffers[i].buffer);
				bodiesBufferInfo.buffer(bodiesUniformBuffers[i].buffer);

				descriptorSet.updateBuilder()
						.addWrite(0, 1, 0).pBufferInfo(cameraBufferInfo).add()
						.addWrite(0, 1, 1).pBufferInfo(bodiesBufferInfo).add()
						.update();
			}
		}

		return descriptorSets;
	}

	private List<DescriptorSet> createOctreeDescriptorSets() {
		final List<DescriptorSet> descriptorSets = octreeDescriptorPool.createDescriptorSets(octreeDescriptorSetLayout, Vulkan.swapChainImages.size());

		try (MemoryStack stack = stackPush()) {
			VkDescriptorBufferInfo.Buffer cameraBufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			for (int i = 0; i < descriptorSets.size(); i++) {
				DescriptorSet descriptorSet = descriptorSets.get(i);

				cameraBufferInfo.buffer(cameraUniformBuffers[i].buffer);

				descriptorSet.updateBuilder()
						.addWrite(0, 1, 0).pBufferInfo(cameraBufferInfo).add()
						.update();
			}
		}

		return descriptorSets;
	}

	private VulkanPipeline[] createPipelines() {
		VulkanPipeline[] pipelines = new VulkanPipeline[2];

		long vertShaderModule = Vulkan.createShaderModule("shaders/scene_shader.vert", VK_SHADER_STAGE_VERTEX_BIT);
		long fragShaderModule = Vulkan.createShaderModule("shaders/scene_shader.frag", VK_SHADER_STAGE_FRAGMENT_BIT);

		VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(new long[] { vertShaderModule }, new long[] { fragShaderModule });

		VkPipelineVertexInputStateCreateInfo vertexInputInfo = Vulkan.createVertexInputInfo(new TestVertex());
		VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
				.primitiveRestartEnable(false);

		VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		VkPipelineRasterizationStateCreateInfo rasterizer = Vulkan.createRasterizer(VK_POLYGON_MODE_FILL, 1.0f, VK_CULL_MODE_NONE)
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

		pipelines[0] = Vulkan.createGraphicsPipeline(shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingDepth, pipelineLayout, depthStencilDepth); //depth write pipeline
		pipelines[1] = Vulkan.createGraphicsPipeline(shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		vkDestroyShaderModule(Vulkan.device, vertShaderModule, null);
		vkDestroyShaderModule(Vulkan.device, fragShaderModule, null);

		return pipelines;
	}

	private VulkanPipeline[] createOctreePipelines() {
		VulkanPipeline[] pipelines = new VulkanPipeline[2];

		long vertShaderModule = Vulkan.createShaderModule("shaders/octree_shader.vert", VK_SHADER_STAGE_VERTEX_BIT);
		long fragShaderModule = Vulkan.createShaderModule("shaders/octree_shader.frag", VK_SHADER_STAGE_FRAGMENT_BIT);

		VkPipelineShaderStageCreateInfo.Buffer shaderStages = Vulkan.createShaderStages(new long[] { vertShaderModule }, new long[] { fragShaderModule });

		VkPipelineVertexInputStateCreateInfo vertexInputInfo = Vulkan.createVertexInputInfo(new TestVertex());
		VkPipelineInputAssemblyStateCreateInfo inputAssembly = Vulkan.createInputAssembly(VK_PRIMITIVE_TOPOLOGY_LINE_LIST)
				.primitiveRestartEnable(false);

		VkPipelineViewportStateCreateInfo viewportState = Vulkan.createViewportState();
		VkPipelineRasterizationStateCreateInfo rasterizer = Vulkan.createRasterizer(VK_POLYGON_MODE_LINE, 1.0f, VK_CULL_MODE_NONE)
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

		VkPipelineLayoutCreateInfo pipelineLayout = Vulkan.createPipelineLayout(octreeDescriptorSetLayout.getHandle(), null);

		VkPipelineDepthStencilStateCreateInfo depthStencilDepth = Vulkan.createDepthStencil(VK_COMPARE_OP_LESS, true);
		VkPipelineDepthStencilStateCreateInfo depthStencilColor = Vulkan.createDepthStencil(VK_COMPARE_OP_EQUAL, false);

		pipelines[0] = Vulkan.createGraphicsPipeline(shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingDepth, pipelineLayout, depthStencilDepth); //depth write pipeline
		pipelines[1] = Vulkan.createGraphicsPipeline(shaderStages, vertexInputInfo, inputAssembly, viewportState, rasterizer, multisampling, colorBlendingColor, pipelineLayout, depthStencilColor); //color write pipeline

		vkDestroyShaderModule(Vulkan.device, vertShaderModule, null);
		vkDestroyShaderModule(Vulkan.device, fragShaderModule, null);

		return pipelines;
	}

	@Override
	public void init() {
		commandPool = createCommandPool();
		descriptorSetLayout = createDescriptorSetLayout();
		octreeDescriptorSetLayout = createOctreeDescriptorSetLayout();

		final List<IVertex> vertices = new ArrayList<>();
		final IntList indices = new IntArrayList();

		Vector3f[] colors = new Vector3f[] { new Vector3f(0.86f, 0.15f, 0.07f), new Vector3f(0.07f, 0.86f, 0.15f), new Vector3f(0.15f, 0.07f, 0.86f) };

		int k = 0;
		for (Side side : Side.values()) {
			final int d1 = (side.d + 1) % 3;
			final int d2 = (side.d + 2) % 3;

			Vector3f position;
			for (int i = 0; i < 2; i++) {
				for (int j = 0; j < 2; j++) {
					position = new Vector3f();
					position.setComponent(side.d, side.ordinal() % 2 == 0 ? 0.5f : -0.5f);

					position.setComponent(d1, i == 0 ? -0.5f : 0.5f);
					position.setComponent(d2, j == 0 ? -0.5f : 0.5f);

					vertices.add(new TestVertex(position, new Vector3f(colors[side.ordinal() / 2])));
				}
			}
			indices.add(k++);
			indices.add(k++);
			indices.add(k++);
			indices.add(k - 2);
			indices.add(k - 1);
			indices.add(k++);
		}

		vertexBuffer = Vulkan.createVertexBuffer(vertices);
		indexBuffer = Vulkan.createIndexBuffer(indices.toIntArray());

		this.vertices = vertices.toArray(new IVertex[0]);
		this.indices = indices.toIntArray();

		int numObjects = 100;

		Vector3d objectsSize = new Vector3d(0.2f);
		Vector3d lastObjectsSize = new Vector3d(1, 0.2f, 1);

		Vector3d pos = new Vector3d(1.2f, 2f, 1.2f);
		Pose pose = new Pose();
		Body lastBody = null;
		Pose jointPose0 = new Pose();
		Pose jointPose1 = new Pose();
		jointPose0.rotation.setAngleAxis(0.5 * Math.PI, 0, 0, 1);
		jointPose1.rotation.setAngleAxis(0.5 * Math.PI, 0, 0, 1);
		Vector3d lastSize = new Vector3d(objectsSize);

		double rotDamping = 1000d;
		double posDamping = 1000d;

		for (int i = 0; i < numObjects; i++) {
			Vector3d size = i < numObjects - 1 ? objectsSize : lastObjectsSize;

			pose.position.set(pos.x, pos.y - i * objectsSize.y, pos.z);

			Body boxBody = new Body(pose, null);
			boxBody.setBox(size, 100);
			bodies.add(boxBody);

			float s = i % 2 == 0 ? -0.5f : 0.5f;
			jointPose0.position.set(s * size.x, 0.5 * size.y, s * size.z);
			jointPose1.position.set(s * lastSize.x, -0.5 * lastSize.y, s * lastSize.z);

			if (lastBody == null) {
				jointPose1.set(jointPose0);
				jointPose1.position.add(pose.position);
			}

			Constraint constraint = new SphericalJoint(boxBody, lastBody, jointPose0, jointPose1);
			constraint.rotDamping = rotDamping;
			constraint.posDamping = posDamping;
			constraint.compliance = 0.00001;
			constraints.add(constraint);

			lastBody = boxBody;
			lastSize.set(size);
		}

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

	private List<IVertex> getOctreeVertices(Octree octree) {
		final List<Octree.Node> nodes = octree.getAllNodes();

		final List<IVertex> vertices = new ArrayList<>();
		final Vector3f color = new Vector3f(0.1f, 0.5f, 0.1f);

		for (Octree.Node node : nodes) {
			int size = node.getMaxPos().x - node.getMinPos().x;
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, 0), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, 0), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, 0, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, 0), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, 0), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, 0), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, 0), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, 0), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, 0), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, 0, size), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, 0, size), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, size), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(0, size, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, size), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, 0, size), color));

			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, size), color));
			vertices.add(new TestVertex(new Vector3f(node.getMinPos()).add(size, size, 0), color));
		}

		return vertices;
	}

	@Override
	public void beforeFrame(int imageIndex) {
//		dirty = true;

		XPBD.simulate(bodies, constraints, 1 / 60f, 120, new Vector3d(0, -10, 0));
		Octree octree = new Octree();

		for (Body body : bodies) {
			octree.insertEntity(body);
		}
//
//		List<IVertex> octreeVertices = getOctreeVertices(octree);
//
//		if (octreeVertexBuffer != null) {
//			octreeVertexBuffer.dispose();
//		}
//		octreeVertexCount = octreeVertices.size();
//		octreeVertexBuffer = Vulkan.createVertexBuffer(octreeVertices);

		final int mat4Size = 16 * Float.BYTES;
		try (MemoryStack stack = stackPush()) {
			Vulkan.UniformBufferObject ubo = new Vulkan.UniformBufferObject();

//			ubo.model.rotate((float) (glfwGetTime() * Math.toRadians(90)), 0.0f, 0.0f, 1.0f);
			ubo.view.lookAt(12.0f, 2.0f, 12.0f, 0.0f, -25.0f, 0.0f, 0.0f, 1.0f, 0.0f).rotate((float) (glfwGetTime() * Math.toRadians(15)), 0, 1, 0);
			ubo.proj.perspective((float) Math.toRadians(90), (float) Vulkan.swapChainExtent.width() / (float) Vulkan.swapChainExtent.height(), 0.01f, 1000.0f);
			ubo.proj.m11(ubo.proj.m11() * -1);

			PointerBuffer data = stack.mallocPointer(1);
			vmaMapMemory(Vulkan.vmaAllocator, cameraUniformBuffers[imageIndex].allocation, data);
			ByteBuffer buffer = data.getByteBuffer(0, Vulkan.UniformBufferObject.SIZEOF);

			ubo.view.get(0, buffer);
			ubo.proj.get(mat4Size, buffer);
			vmaUnmapMemory(Vulkan.vmaAllocator, cameraUniformBuffers[imageIndex].allocation);

			data = stack.mallocPointer(1);
			vmaMapMemory(Vulkan.vmaAllocator, bodiesUniformBuffers[imageIndex].allocation, data);
			buffer = data.getByteBuffer(0, Vulkan.UniformBufferObject.SIZEOF);

			for (int i = 0; i < bodies.size(); i++) {
				Body body = bodies.get(i);

				Matrix4f pos = new Matrix4f().translate((float) body.pose.position.x, (float) body.pose.position.y, (float) body.pose.position.z);
				Matrix4f rot = new Matrix4f().rotate((float) body.pose.rotation.x, (float) body.pose.rotation.y, (float) body.pose.rotation.z, (float) body.pose.rotation.w);
				Matrix4f size = new Matrix4f().scale((float) body.size.x, (float) body.size.y, (float) body.size.z);
				pos.mul(rot).mul(size).get(i * mat4Size, buffer);
			}

			vmaUnmapMemory(Vulkan.vmaAllocator, bodiesUniformBuffers[imageIndex].allocation);
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

		cameraUniformBuffers = createUniformBuffers(Float.BYTES * 16 * 2);
		bodiesUniformBuffers = createUniformBuffers(256 * 256);

		descriptorPool = createDescriptorPool();
		descriptorSets = createDescriptorSets();
		graphicPipelines = createPipelines();

		octreeDescriptorPool = createOctreeDescriptorPool();
		octreeDescriptorSets = createOctreeDescriptorSets();
		octreePipelines = createOctreePipelines();

		this.dirty = true;
	}

	@Override
	public void cleanupSwapChain() {
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool, commandBuffer[0]);
		}

		Arrays.stream(cameraUniformBuffers).forEach(VulkanBuffer::dispose);
		Arrays.stream(bodiesUniformBuffers).forEach(VulkanBuffer::dispose);

//		descriptorSets.forEach(DescriptorSet::dispose);
		descriptorPool.dispose();
		octreeDescriptorPool.dispose();

		Arrays.stream(graphicPipelines).forEach(VulkanPipeline::dispose);
		Arrays.stream(octreePipelines).forEach(VulkanPipeline::dispose);
	}

	@Override
	public void dispose() {
		descriptorSetLayout.dispose();
		octreeDescriptorSetLayout.dispose();
		vkDestroyCommandPool(Vulkan.device, commandPool, null);
		vertexBuffer.dispose();
		indexBuffer.dispose();
	}
}
