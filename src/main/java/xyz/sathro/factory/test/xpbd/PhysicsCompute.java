package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.VulkanCompute;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.events.VulkanDisposeEvent;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.CommandBuffers;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memDoubleBuffer;
import static org.lwjgl.system.MemoryUtil.memIntBuffer;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.*;

@Log4j2
public class PhysicsCompute {
	public static final LinkedList<Long> list = new LinkedList<>();

	private static final int UPS = 60;
	private static final double UPS_INV = 1.0 / UPS;
	private static final int SUBSTEP_COUNT = 15;
	private static final double SUBSTEP_COUNT_INV = 1.0 / SUBSTEP_COUNT;

	private static final int WORKGROUP_COUNT = 32;

	private static final int PARTICLE_SIZE = 4 * 4 * 8;
	private static final int DISTANCE_CONSTRAINT_SIZE = Double.BYTES;
	private static final int DISTANCE_CONSTRAINT_INDEX_SIZE = Integer.BYTES * 2;
	private static final int VOLUME_CONSTRAINT_SIZE = Double.BYTES;
	private static final int VOLUME_CONSTRAINT_INDEX_SIZE = Integer.BYTES * 4;

	private static final List<Particle> particles = new ObjectArrayList<>();

	@Getter private static final List<DistanceConstraint> distanceConstraints = new ObjectArrayList<>();
	@Getter private static final Int2ObjectMap<List<DistanceConstraint>> coloredDistanceConstraints = new Int2ObjectOpenHashMap<>();

	@Getter private static final List<TetrahedralVolumeConstraint> volumeConstraints = new ObjectArrayList<>();
	@Getter private static final Int2ObjectMap<List<TetrahedralVolumeConstraint>> coloredVolumeConstraints = new Int2ObjectOpenHashMap<>();

	@Getter private static final Map<Particle, List<Constraint>> particleConstraints = new Object2ObjectOpenHashMap<>();
	@Getter private static final Object2IntMap<Constraint> constraintColors = new Object2IntOpenHashMap<>();

	private static DescriptorSetLayout particlesDescriptorSetLayout;
	private static int particleBufferSize;
	private static VulkanBuffer particleCPUBuffer;
	private static VulkanBuffer particleGPUBuffer;
	private static DescriptorPool particleDescriptorPool;
	private static DescriptorSet particleDescriptorSet;
	private static VulkanPipeline integrationPipeline;
	private static VulkanPipeline velocityPipeline;

	private static DescriptorSetLayout distanceConstraintDescriptorSetLayout;
	private static VulkanBuffer distanceConstraintIndexStagingBuffer;
	private static VulkanBuffer distanceConstraintIndexBuffer;
	private static VulkanBuffer distanceConstraintsStagingBuffer;
	private static VulkanBuffer distanceConstraintsBuffer;
	private static DescriptorPool distanceConstraintDescriptorPool;
	private static DescriptorSet distanceConstraintsDescriptorSet;
	private static VulkanPipeline distanceConstraintPipeline;

	private static DescriptorSetLayout volumeConstraintDescriptorSetLayout;
	private static VulkanBuffer volumeConstraintIndexStagingBuffer;
	private static VulkanBuffer volumeConstraintIndexBuffer;
	private static VulkanBuffer volumeConstraintsStagingBuffer;
	private static VulkanBuffer volumeConstraintsBuffer;
	private static DescriptorPool volumeConstraintDescriptorPool;
	private static DescriptorSet volumeConstraintsDescriptorSet;
	private static VulkanPipeline volumeConstraintPipeline;

	private static CommandPool commandPool;
	private static VkCommandBuffer commandBuffer;

	static {
		EventManager.registerListeners(PhysicsCompute.class);
	}

	public static void addParticle(Particle particle) {
		particles.add(particle);
		allocateParticleBuffers();
		updateParticlesBuffers();
		updateParticleDescriptorSet();
		fillCommandBuffer();
	}

	public static void addParticles(List<Particle> newParticles) {
		particles.addAll(newParticles);
		allocateParticleBuffers();
		updateParticlesBuffers();
		updateParticleDescriptorSet();
		fillCommandBuffer();
	}

//	public static void addDistanceConstraint(DistanceConstraint distanceConstraint) {
//		distanceConstraints.add(distanceConstraint);
//
//		for (Particle particle : distanceConstraint.getConstrainedParticles()) {
//			if (!particleConstraints.containsKey(particle)) {
//				particleConstraints.put(particle, new ObjectArrayList<>());
//			}
//			particleConstraints.get(particle).add(distanceConstraint);
//		}
//	}

	public static void allocateParticleBuffers() {
		particleBufferSize = Math.max(16, particles.size()) * PARTICLE_SIZE;

		if (particleCPUBuffer != null) { particleCPUBuffer.registerToDisposal(); }
		if (particleGPUBuffer != null) { particleGPUBuffer.registerToDisposal(); }

		particleCPUBuffer = Vulkan.createBuffer(particleBufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		particleGPUBuffer = Vulkan.createBuffer(particleBufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
	}

	public static void allocateConstrainBuffers() {
		distanceConstraintsStagingBuffer = Vulkan.createBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_SIZE, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		distanceConstraintsBuffer = Vulkan.createBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

		distanceConstraintIndexStagingBuffer = Vulkan.createBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		distanceConstraintIndexBuffer = Vulkan.createBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

		volumeConstraintsStagingBuffer = Vulkan.createBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_SIZE, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		volumeConstraintsBuffer = Vulkan.createBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

		volumeConstraintIndexStagingBuffer = Vulkan.createBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
		volumeConstraintIndexBuffer = Vulkan.createBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
	}

	public static void updateConstraintsBuffers() {
		try (MemoryStack stack = stackPush()) {
			final PointerBuffer pointer = stack.mallocPointer(1);

			vmaMapMemory(vmaAllocator, distanceConstraintsStagingBuffer.allocation, pointer);
			{
				final DoubleBuffer buffer = memDoubleBuffer(pointer.get(0), distanceConstraints.size());
				int index = 0;
				for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
					for (DistanceConstraint constraint : constraints) {
						buffer.put(index, constraint.getRestDistance());
						index++;
					}
				}
			}
			vmaUnmapMemory(vmaAllocator, distanceConstraintsStagingBuffer.allocation);
			Vulkan.copyBuffer(distanceConstraintsStagingBuffer, distanceConstraintsBuffer, (long) distanceConstraints.size() * Double.BYTES);

			vmaMapMemory(vmaAllocator, distanceConstraintIndexStagingBuffer.allocation, pointer);
			{
				final IntBuffer buffer = memIntBuffer(pointer.get(0), distanceConstraints.size() * 2);
				int index = 0;
				for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
					for (DistanceConstraint constraint : constraints) {
						buffer.put(index, constraint.getBody1().getIndex());
						buffer.put(index + 1, constraint.getBody2().getIndex());
						index += 2;
					}
				}
			}
			vmaUnmapMemory(vmaAllocator, distanceConstraintIndexStagingBuffer.allocation);
			Vulkan.copyBuffer(distanceConstraintIndexStagingBuffer, distanceConstraintIndexBuffer, (long) distanceConstraints.size() * Integer.BYTES * 2);

			vmaMapMemory(vmaAllocator, volumeConstraintsStagingBuffer.allocation, pointer);
			{
				final DoubleBuffer buffer = memDoubleBuffer(pointer.get(0), volumeConstraints.size());
				int index = 0;
				for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
					for (TetrahedralVolumeConstraint constraint : constraints) {
						buffer.put(index, constraint.getRestVolume());
						index++;
					}
				}
			}
			vmaUnmapMemory(vmaAllocator, volumeConstraintsStagingBuffer.allocation);
			Vulkan.copyBuffer(volumeConstraintsStagingBuffer, volumeConstraintsBuffer, (long) volumeConstraints.size() * Double.BYTES);

			log.info("{} tets", volumeConstraints.size());

			vmaMapMemory(vmaAllocator, volumeConstraintIndexStagingBuffer.allocation, pointer);
			{
				final IntBuffer buffer = memIntBuffer(pointer.get(0), volumeConstraints.size() * 4);
				int index = 0;
				for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
					for (TetrahedralVolumeConstraint constraint : constraints) {
						for (int i = 0; i < constraint.getParticles().length; i++) {
							buffer.put(index + i, constraint.getParticles()[i].getIndex());
						}
						index += 4;
					}
				}
			}
			vmaUnmapMemory(vmaAllocator, volumeConstraintIndexStagingBuffer.allocation);
			Vulkan.copyBuffer(volumeConstraintIndexStagingBuffer, volumeConstraintIndexBuffer, (long) volumeConstraints.size() * Integer.BYTES * 4);
		}
	}

	public static void updateParticlesBuffers() {
		try (MemoryStack stack = stackPush()) {
			final PointerBuffer pointer = stack.mallocPointer(1);

			vmaMapMemory(vmaAllocator, particleCPUBuffer.allocation, pointer);
			{
				final DoubleBuffer buffer = memDoubleBuffer(pointer.get(0), particles.size() * 4 * 4);
				Particle particle;
				for (int i = 0; i < particles.size(); i++) {
					particle = particles.get(i);
					particle.getPosition().get(i * 4 * 4, buffer);
					buffer.put(i * 4 * 4 + 3, particle.getMass());

					particle.getPrevPosition().get(i * 4 * 4 + 4, buffer);
					buffer.put(i * 4 * 4 + 7, particle.getInvMass());

					particle.getVelocity().get(i * 4 * 4 + 8, buffer);
				}
			}
			vmaUnmapMemory(vmaAllocator, particleCPUBuffer.allocation);
			Vulkan.copyBuffer(particleCPUBuffer, particleGPUBuffer, particleBufferSize);
		}
	}

	public static void createDescriptorSets() {
		vkResetDescriptorPool(device, particleDescriptorPool.getHandle(), 0);
		particleDescriptorSet = particleDescriptorPool.createDescriptorSets(particlesDescriptorSetLayout).get(0);

		vkResetDescriptorPool(device, distanceConstraintDescriptorPool.getHandle(), 0);
		distanceConstraintsDescriptorSet = distanceConstraintDescriptorPool.createDescriptorSets(distanceConstraintDescriptorSetLayout).get(0);

		vkResetDescriptorPool(device, volumeConstraintDescriptorPool.getHandle(), 0);
		volumeConstraintsDescriptorSet = volumeConstraintDescriptorPool.createDescriptorSets(volumeConstraintDescriptorSetLayout).get(0);
	}

	public static void updateParticleDescriptorSet() {
		particleDescriptorSet.updateBuilder()
				.write(0).buffer(particleGPUBuffer).add()
				.update();
	}

	public static void updateConstraintsDescriptorSet() {
		distanceConstraintsDescriptorSet.updateBuilder()
				.write(0).buffer(distanceConstraintIndexBuffer).add()
				.write(1).buffer(distanceConstraintsBuffer).add()
				.update();

		volumeConstraintsDescriptorSet.updateBuilder()
				.write(0).buffer(volumeConstraintIndexBuffer).add()
				.write(1).buffer(volumeConstraintsBuffer).add()
				.update();
	}

	public static void updateConstraints() {
		allocateConstrainBuffers();
		updateConstraintsBuffers();
		updateConstraintsDescriptorSet();
		fillCommandBuffer();
	}

	public static void fillCommandBuffer() {
		try (MemoryStack stack = stackPush()) {
			int offset;

			final ByteBuffer pushConstants = stack.malloc(24);
			pushConstants.putDouble(0, UPS_INV * SUBSTEP_COUNT_INV);

			final LongBuffer particleDescriptorSetPointer = stack.longs(particleDescriptorSet.getHandle());
			final LongBuffer distanceConstraintDescriptorSetPointer = stack.longs(distanceConstraintsDescriptorSet.getHandle());
			final LongBuffer volumeConstraintDescriptorSetPointer = stack.longs(volumeConstraintsDescriptorSet.getHandle());

			final VkMemoryBarrier2KHR.Buffer memoryBarrier = VkMemoryBarrier2KHR.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2_KHR)
					.srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
					.srcAccessMask(VK_ACCESS_2_SHADER_WRITE_BIT_KHR | VK_ACCESS_2_SHADER_READ_BIT_KHR)
					.dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
					.dstAccessMask(VK_ACCESS_2_SHADER_WRITE_BIT_KHR | VK_ACCESS_2_SHADER_READ_BIT_KHR);

			final VkDependencyInfoKHR dependencyInfo = VkDependencyInfoKHR.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
					.pMemoryBarriers(memoryBarrier);

			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			vkResetCommandPool(device, commandPool.handle, 0);

			vkBeginCommandBuffer(commandBuffer, beginInfo);
			{
				for (int i = 0; i < SUBSTEP_COUNT; i++) {
					pushConstants.putInt(16, particles.size());
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, integrationPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, integrationPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdPushConstants(commandBuffer, integrationPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) particles.size() / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// distance constraint
					pushConstants.putDouble(8, 0.02);
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.layout, 1, distanceConstraintDescriptorSetPointer, null);

					offset = 0;
					for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
						pushConstants.putInt(16, constraints.size());
						pushConstants.putInt(20, offset);
						vkCmdPushConstants(commandBuffer, distanceConstraintPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
						vkCmdDispatch(commandBuffer, (int) Math.ceil((float) constraints.size() / WORKGROUP_COUNT), 1, 1);
						vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

						offset += constraints.size();
					}

					// volume constraint
					pushConstants.putDouble(8, 0.001);
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.layout, 1, volumeConstraintDescriptorSetPointer, null);

					offset = 0;
					for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
						pushConstants.putInt(16, constraints.size());
						pushConstants.putInt(20, offset);
						vkCmdPushConstants(commandBuffer, volumeConstraintPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
						vkCmdDispatch(commandBuffer, (int) Math.ceil((float) constraints.size() / WORKGROUP_COUNT), 1, 1);
						vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

						offset += constraints.size();
					}

					// update velocities
					pushConstants.putInt(16, particles.size());
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, velocityPipeline.handle);
					vkCmdPushConstants(commandBuffer, integrationPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, velocityPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) particles.size() / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
				}
			}
			vkEndCommandBuffer(commandBuffer);
		}
	}

	@SubscribeEvent
	public static void onVulkanInit( /* create event */) {
		particlesDescriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.build();

		distanceConstraintDescriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.build();

		volumeConstraintDescriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.build();

		final VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stackGet())
				.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
				.size(24);

		integrationPipeline = VulkanCompute.createComputePipeline("shaders/physics/integrate.comp", particlesDescriptorSetLayout, pushConstantRanges);
		velocityPipeline = VulkanCompute.createComputePipeline("shaders/physics/updateVelocity.comp", particlesDescriptorSetLayout, pushConstantRanges);

		distanceConstraintPipeline = VulkanCompute.createComputePipeline("shaders/physics/distanceConstraint.comp",
		                                                                 new DescriptorSetLayout[] { particlesDescriptorSetLayout, distanceConstraintDescriptorSetLayout },
		                                                                 pushConstantRanges);

		volumeConstraintPipeline = VulkanCompute.createComputePipeline("shaders/physics/volumeConstraint.comp",
		                                                               new DescriptorSetLayout[] { particlesDescriptorSetLayout, volumeConstraintDescriptorSetLayout },
		                                                               pushConstantRanges);

		particleDescriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1)
				.setMaxSets(1)
				.build();

		distanceConstraintDescriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 2)
				.setMaxSets(1)
				.build();

		volumeConstraintDescriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 2)
				.setMaxSets(1)
				.build();

		createDescriptorSets();

		commandPool = CommandPool.newDefault(0, queues.compute, false);
		commandBuffer = CommandBuffers.createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, commandPool.handle);
	}

	@SubscribeEvent
	public static void onVulkanDispose(VulkanDisposeEvent event) {

	}

	public static void simulate() {
		try (MemoryStack stack = stackPush()) {
			final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.pCommandBuffers(stack.pointers(commandBuffer));

			final long time0 = System.nanoTime();
			queues.compute.submitAndWait(submitInfo, commandPool.fence);
			list.add(System.nanoTime() - time0);
			if (list.size() > 60) {
				list.pop();
			}

//			log.info("Compute: {}ms", list.stream().reduce(0L, Long::sum) / 1_000_000f / list.size());
			vkResetFences(device, commandPool.fence);

			updateParticles();
		}
	}

	public static void updateParticles() {
		try (MemoryStack stack = stackPush()) {
			// TODO: cache copy command
			copyBuffer(particleGPUBuffer, particleCPUBuffer, (long) particles.size() * PARTICLE_SIZE);

			final PointerBuffer pointer = stack.mallocPointer(1);
			vmaMapMemory(vmaAllocator, particleCPUBuffer.allocation, pointer);
			{
				final DoubleBuffer buffer = memDoubleBuffer(pointer.get(0), particles.size() * PARTICLE_SIZE);
				Particle particle;
				for (int i = 0; i < particles.size(); i++) {
					particle = particles.get(i);
					particle.getPosition().set(i * 4 * 4, buffer);
//					particle.setMass(buffer.get(i * 4 * 4 + 12));
//					particle.getPrevPosition().set(i * 4 * 4 + 16, buffer);
//					particle.getVelocity().set(i * 4 * 4 + 32, buffer);
				}
			}
			vmaUnmapMemory(vmaAllocator, particleCPUBuffer.allocation);
		}
	}
}
