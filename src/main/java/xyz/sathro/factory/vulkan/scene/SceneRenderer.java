package xyz.sathro.factory.vulkan.scene;

import lombok.extern.log4j.Log4j2;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo;
import xyz.sathro.factory.test.xpbd.FpsCamera;
import xyz.sathro.factory.test.xpbd.IMesh;
import xyz.sathro.factory.test.xpbd.PhysicsCompute;
import xyz.sathro.factory.test.xpbd.Scene;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.*;
import xyz.sathro.vulkan.renderer.IRenderer;
import xyz.sathro.vulkan.utils.CommandBuffers;
import xyz.sathro.vulkan.utils.VulkanUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.vmaMapMemory;
import static org.lwjgl.util.vma.Vma.vmaUnmapMemory;
import static org.lwjgl.vulkan.VK10.*;

@Log4j2
public class SceneRenderer implements IRenderer {
	public static final SceneRenderer INSTANCE = new SceneRenderer();

	private final Scene scene = new Scene();

	private final VkCommandBuffer[][] commandBuffers = new VkCommandBuffer[3][];
	private final SceneParticlePipeline particlePipeline = new SceneParticlePipeline();
	private final SceneModelPipeline modelPipeline = new SceneModelPipeline();

	private CommandPool commandPool;
	public CombinedBuffer particleCombinedBuffer;
//	public CombinedBuffer modelCombinedBuffer;
	public VulkanBuffer[] cameraUniformBuffers;

	public VulkanBuffer vertexBuffer;
	public VulkanBuffer indexBuffer;
	public int indexCount = 0;

	public int particleCount = 0;
	public VulkanBuffer[] particlePositionBuffers;

	public int modelCount = 0;
	public VulkanBuffer[] modelBuffers;

	private boolean dirty = false;
	private boolean cbChanged = false;

	private SceneRenderer() { }

	private void createCommandBuffers() {
		for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
			commandBuffers[i] = CommandBuffers.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_SECONDARY, 1, commandPool.handle);
		}
	}

	private void recordCommandBuffers(int imageIndex) {
		cbChanged = true;
		try (MemoryStack stack = stackPush()) {
			final LongBuffer particleVertexBuffer = stack.longs(particleCombinedBuffer.getVertexBuffer().handle);
			final LongBuffer offsets = stack.longs(0);
			final IntBuffer offset = stack.ints(0);

			final VkCommandBuffer commandBuffer = commandBuffers[imageIndex][0];

			final VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
					.renderPass(Vulkan.renderPass)
					.framebuffer(Vulkan.swapChainFramebuffers.getLong(imageIndex))
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);

			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.pInheritanceInfo(inheritanceInfo)
					.flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			VulkanUtils.VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");

//			VertexData vertexData;
//			for (VulkanPipeline pipeline : particlePipeline.pipelines) {
//				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle);
//
//				vkCmdBindIndexBuffer(commandBuffer, particleCombinedBuffer.getIndexBuffer().handle, 0, VK_INDEX_TYPE_UINT32);
//				vkCmdBindVertexBuffers(commandBuffer, 0, particleVertexBuffer, offsets);
//
//				vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout, 0, stack.longs(particlePipeline.descriptorSets.get(imageIndex).getHandle()), offset);
//
//				vertexData = particleCombinedBuffer.getVertexData(pipeline.vertexType);
//
//				vkCmdDrawIndexed(commandBuffer, vertexData.getIndexCount(), particleCount, vertexData.getIndexOffset(), vertexData.getVertexOffset(), 0);
//			}

			for (VulkanPipeline pipeline : modelPipeline.pipelines) {
				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle);
				vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout, 0, stack.longs(modelPipeline.descriptorSets.get(imageIndex).getHandle()), offset);

				for (IMesh mesh : scene.getMeshes()) {
					vkCmdBindIndexBuffer(commandBuffer, mesh.getIndexBuffer().handle, 0, VK_INDEX_TYPE_UINT32);
					vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(mesh.getVertexBuffer().handle), offsets);

					vkCmdDrawIndexed(commandBuffer, mesh.getIndexCount(), 1, 0, 0, 0);
				}
			}

			VulkanUtils.VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to record command buffer");
		}
	}

	@Override
	public void init() {
//		Physics.prepareCompute();
		PhysicsCompute.onVulkanInit();
		scene.onVulkanInit();

		commandPool = CommandPool.newDefault(0, Vulkan.queues.graphics);

		particlePipeline.createDescriptorSetLayout();
		modelPipeline.createDescriptorSetLayout();
		createSwapChain();

		CombinedBuffer.Builder builder = CombinedBuffer.builder();

		builder.addVertex(new SceneParticleVertex(new Vector3f(), new Vector3f(0.9f, 0.2f, 0.6f), 25));
		builder.addIndex(SceneParticleVertex.class, 0);

		particleCombinedBuffer = builder.build();

//		builder = CombinedBuffer.builder();

//		MeshedBody mesh = scene.getMeshes().get(0);
//
//		final List<SceneModelVertex> vertexList = Arrays.stream(mesh.getVertices()).map(pos -> new SceneModelVertex(pos.get(new Vector3f()), color)).collect(Collectors.toList());
//
//		vertexBuffer = Vulkan.createVertexBuffer(vertexList);
//		indexCount = mesh.getSurfaceIndices().length;
//		indexBuffer = Vulkan.createIndexBuffer(mesh.getSurfaceIndices());

//		for (MeshedBody mesh : scene.getMeshes()) {
//			for (Vector3d pos : mesh.getVertices()) {
//				builder.addVertex(new SceneModelVertex(pos.get(new Vector3f()), color));
//			}
//			builder.addIndices(SceneModelVertex.class, mesh.getSurfaceIndices());
//		}

//		builder.addIndices(SceneModelVertex.class, 0, 100, 2);

//		modelCombinedBuffer = builder.build();
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
			final FpsCamera camera = scene.getCamera();
			final Vector3f pos = camera.getPosition();

			final CameraUBO ubo = new CameraUBO();

			ubo.view.identity()
					.rotateXYZ((float) Math.toRadians(camera.getPitch()), (float) Math.toRadians(camera.getYaw()), (float) Math.toRadians(camera.getRoll()))
					.translate(-pos.x, -pos.y, -pos.z);

			ubo.proj.perspective((float) Math.toRadians(scene.getCamera().getFOV()), (float) Vulkan.swapChainExtent.width() / (float) Vulkan.swapChainExtent.height(), 0.01f, 1000.0f);
			ubo.proj.m11(ubo.proj.m11() * -1);

			ubo.cameraPos.set(pos);

			final PointerBuffer data = stack.mallocPointer(1);
			vmaMapMemory(Vulkan.vmaAllocator, cameraUniformBuffers[imageIndex].allocation, data);
			{
				final ByteBuffer buffer = data.getByteBuffer(0, ubo.sizeof());
				ubo.get(buffer);
			}
			vmaUnmapMemory(Vulkan.vmaAllocator, cameraUniformBuffers[imageIndex].allocation);

			vmaMapMemory(Vulkan.vmaAllocator, particlePositionBuffers[imageIndex].allocation, data);
			{
				final ByteBuffer buffer = data.getByteBuffer(0, 512 * 4 * 3);

				scene.getBodies().stream().filter(body -> body instanceof Particle).limit(512).forEach(body -> {
					buffer.putFloat((float) body.getPosition().x);
					buffer.putFloat((float) body.getPosition().y);
					buffer.putFloat((float) body.getPosition().z);
				});

				particleCount = Maths.clamp(512 - buffer.remaining() / (3 * Float.BYTES), 0, 512);
			}
			vmaUnmapMemory(Vulkan.vmaAllocator, particlePositionBuffers[imageIndex].allocation);

			vmaMapMemory(Vulkan.vmaAllocator, modelBuffers[imageIndex].allocation, data);
			{
				final ByteBuffer buffer = data.getByteBuffer(0, 65536);

				int index = 0;
				for (IMesh mesh : scene.getMeshes()) {
					final Matrix4f model = new Matrix4f();
					model.scale(15, 15, 15);
//					model.translate(1, 1, 1);

					model.get(index * 16 * 4, buffer);

					index++;
				}

				modelCount = scene.getMeshes().size();
			}
			vmaUnmapMemory(Vulkan.vmaAllocator, modelBuffers[imageIndex].allocation);
		}
		if (dirty) {
//			dirty = false;
			vkResetCommandPool(Vulkan.device, commandPool.handle, 0);
			for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
				recordCommandBuffers(i);
			}
		}
	}

	@Override
	public void afterFrame(int imageIndex) {

	}

	@Override
	public void createSwapChain() {
		createCommandBuffers();

		cameraUniformBuffers = Vulkan.createUniformBuffers((16 * 2 + 3) * Float.BYTES, Vulkan.swapChainImageCount);
		particlePositionBuffers = Vulkan.createUniformBuffers(1024 * 4 * 3, Vulkan.swapChainImageCount);

		modelBuffers = Vulkan.createUniformBuffers(65536, Vulkan.swapChainImageCount);

		particlePipeline.createDescriptorPool();
		particlePipeline.createDescriptorSets(cameraUniformBuffers, particlePositionBuffers);
		particlePipeline.createPipelines();

		modelPipeline.createDescriptorPool();
		modelPipeline.createDescriptorSets(cameraUniformBuffers, modelBuffers);
		modelPipeline.createPipelines();

		dirty = true;
	}

	@Override
	public void cleanupSwapChain() {
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool.handle, commandBuffer[0]);
		}

		Arrays.stream(cameraUniformBuffers).forEach(VulkanBuffer::dispose);
		Arrays.stream(particlePositionBuffers).forEach(VulkanBuffer::dispose);
		Arrays.stream(modelBuffers).forEach(VulkanBuffer::dispose);

		particlePipeline.descriptorPool.dispose();
		modelPipeline.descriptorPool.dispose();

		Arrays.stream(particlePipeline.pipelines).forEach(VulkanPipeline::dispose);
		Arrays.stream(modelPipeline.pipelines).forEach(VulkanPipeline::dispose);
	}

	@Override
	public void dispose() {
		particlePipeline.descriptorSetLayout.dispose();
		modelPipeline.descriptorSetLayout.dispose();

		commandPool.dispose();

		particleCombinedBuffer.dispose();
//		modelCombinedBuffer.dispose();

//		vertexBuffer.dispose();
//		indexBuffer.dispose();
	}

	private static class CameraUBO implements IBufferObject {
		private static final int SIZEOF = (2 * 16 + 3) * Float.BYTES;
		private static final int MATRIX_OFFSET = 16 * Float.BYTES;

		public final Matrix4f view = new Matrix4f();
		public final Matrix4f proj = new Matrix4f();
		public final Vector3f cameraPos = new Vector3f();

		@Override
		public int sizeof() {
			return SIZEOF;
		}

		@Override
		public void get(int index, ByteBuffer buffer) {
			view.get(index, buffer);
			proj.get(index + MATRIX_OFFSET, buffer);
			cameraPos.get(index + MATRIX_OFFSET * 2, buffer);
		}
	}
}
