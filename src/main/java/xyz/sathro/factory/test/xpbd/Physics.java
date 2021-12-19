package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.extern.log4j.Log4j2;
import org.joml.Vector3d;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.test.xpbd.body.PhysicsBody;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.CommandBuffers;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.createBuffer;
import static xyz.sathro.vulkan.Vulkan.vmaAllocator;
import static xyz.sathro.vulkan.VulkanCompute.createComputePipeline;

@Log4j2
public class Physics {
	private static final int UPS = 60;
	private static final double UPS_INV = 1.0 / UPS;
	private static final int SUBSTEP_COUNT = 10;
	private static final double SUBSTEP_COUNT_INV = 1.0 / SUBSTEP_COUNT;

	private static final int indexBufferSize = 8000 * 2 * Integer.BYTES;
	private static final int invMassesBufferSize = 10000 * Float.BYTES;
	private static final int restDistanceBufferSize = 8000 * Float.BYTES;
	private static final int positionBufferSize = 10000 * 4 * Double.BYTES;
	private static final int constantBufferSize = Integer.BYTES + Float.BYTES;

	private static VulkanBuffer indexBuffer;
	private static VulkanBuffer invMassesBuffer;
	private static VulkanBuffer restDistanceBuffer;
	private static VulkanBuffer positionCPUBuffer;
	private static VulkanBuffer positionGPUBuffer;
	private static VulkanBuffer constantBuffer;

	private static IntBuffer indexByteBuffer;
	private static FloatBuffer invMassesByteBuffer;
	private static FloatBuffer restDistanceByteBuffer;
	private static DoubleBuffer positionByteBuffer;
	private static ByteBuffer constantByteBuffer;

	private static DescriptorSetLayout descriptorSetLayout;
	private static DescriptorPool descriptorPool;
	private static DescriptorSet descriptorSet;
	private static VulkanPipeline pipeline;

	private static CommandPool commandPool;
	private static VkCommandBuffer commandBuffer;

	static {
		EventManager.registerListeners(Physics.class);
	}

	private Physics() { }

	public static void simulate(List<PhysicsBody> bodies, List<TetrahedralVolumeConstraint> volumeConstraints, Int2ObjectMap<List<DistanceConstraint>> distanceConstraints) {
		// TODO: get collision pairs using currentPos and currentVel
		// TODO: create collision constraints

//		final List<DistanceConstraint> distanceConstraintsCPU = new ObjectArrayList<>();
//		for (Int2ObjectMap.Entry<List<DistanceConstraint>> entry : distanceConstraints.int2ObjectEntrySet()) {
//			if (entry.getValue().size() < 64) {
//				distanceConstraintsCPU.addAll(entry.getValue());
//			}
//		}

//		for (int i = 0; i < bodies.size(); i++) {
//			invMassesByteBuffer.put(i, (float) bodies.get(i).getInvMass());
//		}

		final Vector3d gravity = new Vector3d(0, -10, 0);

		final double dt = UPS_INV * SUBSTEP_COUNT_INV;
		final double dtInv = UPS * SUBSTEP_COUNT;
		final long time0 = System.nanoTime();
		long computeTime = 0;
		for (int i = 0; i < SUBSTEP_COUNT; i++) {
			// predict positions
			for (PhysicsBody body : bodies) {
				body.integrate(dt, gravity);
			}

			// project position constraints
			for (Constraint constraint : volumeConstraints) {
				constraint.solvePosition(dt);
			}

//			for (DistanceConstraint constraint : distanceConstraintsCPU) {
//				constraint.solvePosition(dt);
//			}
//
//			final long time1 = System.nanoTime();
//			computeDistanceConstraints(distanceConstraints, bodies, dt);
//			computeTime += System.nanoTime() - time1;

			// update velocities
			for (PhysicsBody body : bodies) {
				body.updateVelocity(dtInv);
			}

			// project velocity constraints
//			for (Constraint constraint : constraints) {
//				constraint.solveVelocity(dt);
//			}
		}

		log.info("Physics: {}ms; Compute:{}ms", (System.nanoTime() - time0) / 1_000_000d, computeTime / 1_000_000d);
	}

	public static void prepareCompute() {
		descriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(2, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(3, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(4, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.build();
		descriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 5)
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 5)
				.setMaxSets(1)
				.build();
		descriptorSet = descriptorPool.createDescriptorSets(descriptorSetLayout, 1).get(0);

		pipeline = createComputePipeline("shaders/physics/distanceConstraint.comp", descriptorSetLayout, null);

		indexBuffer = createBuffer(indexBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		invMassesBuffer = createBuffer(invMassesBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		restDistanceBuffer = createBuffer(restDistanceBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		positionCPUBuffer = createBuffer(positionBufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		positionGPUBuffer = createBuffer(positionBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
		constantBuffer = createBuffer(constantBufferSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);

		descriptorSet.updateBuilder()
				.write(0).buffer(indexBuffer).add()
				.write(1).buffer(invMassesBuffer).add()
				.write(2).buffer(restDistanceBuffer).add()
				.write(3).buffer(positionGPUBuffer).add()
				.write(4).buffer(constantBuffer).add()
				.update();

		try (MemoryStack stack = stackPush()) {
			final PointerBuffer pointer = stack.mallocPointer(1);
			vmaMapMemory(vmaAllocator, constantBuffer.allocation, pointer);
			constantByteBuffer = memByteBuffer(pointer.get(0), constantBufferSize);

			vmaMapMemory(vmaAllocator, indexBuffer.allocation, pointer);
			indexByteBuffer = memIntBuffer(pointer.get(0), indexBufferSize);

			vmaMapMemory(vmaAllocator, restDistanceBuffer.allocation, pointer);
			restDistanceByteBuffer = memFloatBuffer(pointer.get(0), restDistanceBufferSize);

			vmaMapMemory(vmaAllocator, invMassesBuffer.allocation, pointer);
			invMassesByteBuffer = memFloatBuffer(pointer.get(0), invMassesBufferSize);

			vmaMapMemory(vmaAllocator, positionCPUBuffer.allocation, pointer);
			positionByteBuffer = memDoubleBuffer(pointer.get(0), positionBufferSize);

			commandPool = CommandPool.newDefault(0, Vulkan.queues.compute, true);
			commandBuffer = CommandBuffers.createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, commandPool.handle);

			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			vkBeginCommandBuffer(commandBuffer, beginInfo);

			vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle);
			vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.layout, 0, stack.longs(descriptorSet.getHandle()), null);

			vkCmdDispatch(commandBuffer, (int) Math.ceil((float) 10000 / 32), 1, 1);

			vkEndCommandBuffer(commandBuffer);
		}
	}

	private static void computeDistanceConstraints(Int2ObjectMap<List<DistanceConstraint>> distanceConstraints, List<PhysicsBody> particles, double dt) {
		final double compliance = 0.1;
		for (int i = 0; i < particles.size(); i++) {
			particles.get(i).getPosition().get(i * 4, positionByteBuffer);
		}
		Vulkan.copyBuffer(positionCPUBuffer, positionGPUBuffer, positionBufferSize);

		long timeBuffers = 0;
		long timeCompute = 0;
		try (MemoryStack stack = stackPush()) {
			for (List<DistanceConstraint> list : distanceConstraints.values()) {
				if (list.size() < 64) { continue; }

				constantByteBuffer.putInt(0, list.size());
				constantByteBuffer.putFloat(4, (float) (compliance / (dt * dt)));

				long time = System.nanoTime();
				for (int i = 0; i < list.size(); i++) {
					final DistanceConstraint constraint = list.get(i);

					indexByteBuffer.put(i * 2, constraint.getBody1().getIndex());
					indexByteBuffer.put(i * 2 + 1, constraint.getBody2().getIndex());
					restDistanceByteBuffer.put(i, (float) constraint.getRestDistance());
				}
				timeBuffers += System.nanoTime() - time;

				time = System.nanoTime();

				final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
						.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
						.pCommandBuffers(stack.pointers(commandBuffer));

				vkResetFences(Vulkan.device, commandPool.fence);
				Vulkan.queues.compute.submitAndWait(submitInfo, commandPool.fence);

				timeCompute += System.nanoTime() - time;
			}

			log.info("Buffers: {}ms, Compute: {}ms", timeBuffers / 1_000_000d, timeCompute / 1_000_000d);
		}

		Vulkan.copyBuffer(positionGPUBuffer, positionCPUBuffer, positionBufferSize);
		for (int i = 0; i < particles.size(); i++) {
			particles.get(i).getPosition().set(i * 4, positionByteBuffer);
		}
	}
}
