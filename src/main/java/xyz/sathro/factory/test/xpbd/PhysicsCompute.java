package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.factory.util.AveragedTimer;
import xyz.sathro.factory.vulkan.scene.SceneModelVertex;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.VulkanCompute;
import xyz.sathro.vulkan.buffers.StagedBuffer;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.events.VulkanDisposeEvent;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.CommandBuffers;

import java.nio.*;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memDoubleBuffer;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.*;

@Log4j2
public class PhysicsCompute {
	private static final AveragedTimer timer = new AveragedTimer(60);

	private static final int WORKGROUP_COUNT = 32;

	private static final int PUSH_CONSTANTS_SIZE = 32;

	private static final int VALUES_IN_PARTICLE = 4 * 3;
	private static final int PARTICLE_SIZE = VALUES_IN_PARTICLE * Double.BYTES;
	private static final int DISTANCE_CONSTRAINT_SIZE = Double.BYTES;
	private static final int DISTANCE_CONSTRAINT_INDEX_SIZE = Integer.BYTES * 2;
	private static final int VOLUME_CONSTRAINT_SIZE = Double.BYTES;
	private static final int VOLUME_CONSTRAINT_INDEX_SIZE = Integer.BYTES * 4;

	@Getter private static final List<Particle> particles = new ObjectArrayList<>();

	@Getter private static final List<DistanceConstraint> distanceConstraints = new ObjectArrayList<>();
	@Getter private static final Int2ObjectMap<List<DistanceConstraint>> coloredDistanceConstraints = new Int2ObjectOpenHashMap<>();

	@Getter private static final List<TetrahedralVolumeConstraint> volumeConstraints = new ObjectArrayList<>();
	@Getter private static final Int2ObjectMap<List<TetrahedralVolumeConstraint>> coloredVolumeConstraints = new Int2ObjectOpenHashMap<>();

	@Getter private static final Map<Particle, List<Constraint>> particleConstraints = new Object2ObjectOpenHashMap<>();
	@Getter private static final Object2IntMap<Constraint> constraintColors = new Object2IntOpenHashMap<>();

	private static StagedBuffer particleBuffer;

	private static DescriptorSetLayout particlesDescriptorSetLayout;
	private static DescriptorPool particleDescriptorPool;
	private static DescriptorSet particleDescriptorSet;
	private static VulkanPipeline integrationPipeline;
	private static VulkanPipeline velocityPipeline;

	private static StagedBuffer distanceConstraintIndexBuffer;
	private static StagedBuffer distanceConstraintsBuffer;

	private static DescriptorSetLayout distanceConstraintDescriptorSetLayout;
	private static DescriptorPool distanceConstraintDescriptorPool;
	private static DescriptorSet distanceConstraintsDescriptorSet;
	private static VulkanPipeline distanceConstraintPipeline;

	private static StagedBuffer volumeConstraintIndexBuffer;
	private static StagedBuffer volumeConstraintsBuffer;

	private static DescriptorSetLayout volumeConstraintDescriptorSetLayout;
	private static DescriptorPool volumeConstraintDescriptorPool;
	private static DescriptorSet volumeConstraintsDescriptorSet;
	private static VulkanPipeline volumeConstraintPipeline;

	private static DescriptorSetLayout buffersDescriptorSetLayout;
	private static DescriptorPool buffersDescriptorPool;
	private static DescriptorSet buffersDescriptorSet;
	private static VulkanPipeline buffersZeroNormalsPipeline;
	private static VulkanPipeline buffersBarycentricPipeline;
	private static VulkanPipeline buffersCalculateNormalsPipeline;
	private static VulkanPipeline buffersFinishNormalsPipeline;

	private static StagedBuffer modelVerticesBuffer;
	private static StagedBuffer modelIndexBuffer;
	private static StagedBuffer tetrahedraIndexBuffer;
	private static StagedBuffer localVertexBuffer;
	@Getter private static VulkanBuffer vertexBuffer;

	private static CommandPool commandPool;
	private static VkCommandBuffer commandBuffer;
	public static CommandPool copyCommandPool;
	private static VkCommandBuffer copyCommandBuffer;

	public static boolean ready = false;

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
		if (particleBuffer != null) {
			particleBuffer.reallocate(particles.size() * PARTICLE_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true);
		} else {
			particleBuffer = new StagedBuffer(particles.size() * PARTICLE_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, true);
		}
	}

	public static void allocateConstrainBuffers() {
		distanceConstraintIndexBuffer = new StagedBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);
		distanceConstraintsBuffer = new StagedBuffer(distanceConstraints.size() * DISTANCE_CONSTRAINT_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);

		volumeConstraintsBuffer = new StagedBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);
		volumeConstraintIndexBuffer = new StagedBuffer(volumeConstraints.size() * VOLUME_CONSTRAINT_INDEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);
	}

	public static void allocateModelBuffers(MeshedBody meshedBody) {
		modelVerticesBuffer = new StagedBuffer(meshedBody.getModelVertices().size() * (Float.BYTES * 3 + Integer.BYTES), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);
		modelIndexBuffer = new StagedBuffer(meshedBody.getModelIndices().size() * Integer.BYTES, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);

		tetrahedraIndexBuffer = new StagedBuffer(meshedBody.getTetrahedra().length * Integer.BYTES * 4, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, true, false);
		localVertexBuffer = new StagedBuffer(meshedBody.getModelVertices().size() * SceneModelVertex.SIZEOF, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, true, true, false);

		vertexBuffer = Vulkan.createBuffer(localVertexBuffer.getBufferSize(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

		vkResetCommandPool(device, copyCommandPool.handle, 0);

		try (MemoryStack stack = stackPush()) {
			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			final VkBufferCopy.Buffer vertexCopyRegion = VkBufferCopy.calloc(1, stack).size(localVertexBuffer.getBufferSize());

			vkBeginCommandBuffer(copyCommandBuffer, beginInfo);
			vkCmdCopyBuffer(copyCommandBuffer, localVertexBuffer.getGPUBuffer().handle, vertexBuffer.handle, vertexCopyRegion);
			vkEndCommandBuffer(copyCommandBuffer);
		}
	}

	public static void updateConstraintsBuffers() {
		log.info("{} tetrahedral constraints, {} distance constraints", volumeConstraints.size(), distanceConstraints.size());

		distanceConstraintIndexBuffer.updateBuffer(byteBuffer -> {
			final IntBuffer buffer = byteBuffer.asIntBuffer();
			int index = 0;
			for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
				for (DistanceConstraint constraint : constraints) {
					buffer.put(index, constraint.getBody1().getIndex());
					buffer.put(index + 1, constraint.getBody2().getIndex());
					index += 2;
				}
			}
		});
		distanceConstraintIndexBuffer.copyToGPU();

		distanceConstraintsBuffer.updateBuffer(byteBuffer -> {
			final DoubleBuffer buffer = byteBuffer.asDoubleBuffer();
			int index = 0;
			for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
				for (DistanceConstraint constraint : constraints) {
					buffer.put(index, constraint.getRestDistance());
					index++;
				}
			}
		});
		distanceConstraintsBuffer.copyToGPU();

		volumeConstraintsBuffer.updateBuffer(byteBuffer -> {
			final DoubleBuffer buffer = byteBuffer.asDoubleBuffer();
			int index = 0;
			for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
				for (TetrahedralVolumeConstraint constraint : constraints) {
					buffer.put(index, constraint.getRestVolume());
					index++;
				}
			}
		});
		volumeConstraintsBuffer.copyToGPU();

		volumeConstraintIndexBuffer.updateBuffer(byteBuffer -> {
			final IntBuffer buffer = byteBuffer.asIntBuffer();
			int index = 0;
			for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
				for (TetrahedralVolumeConstraint constraint : constraints) {
					for (int i = 0; i < constraint.getParticles().length; i++) {
						buffer.put(index + i, constraint.getParticles()[i].getIndex());
					}
					index += 4;
				}
			}
		});
		volumeConstraintIndexBuffer.copyToGPU();
	}

	public static void updateParticlesBuffers() {
		particleBuffer.updateBuffer((byteBuffer -> {
			final DoubleBuffer buffer = byteBuffer.asDoubleBuffer();

			Particle particle;
			for (int i = 0; i < particles.size(); i++) {
				particle = particles.get(i);
				particle.getPosition().get(i * VALUES_IN_PARTICLE, buffer);
				buffer.put(i * VALUES_IN_PARTICLE + 3, particle.getMass());

				particle.getPrevPosition().get(i * VALUES_IN_PARTICLE + 4, buffer);
				buffer.put(i * VALUES_IN_PARTICLE + 7, particle.getInvMass());

				particle.getVelocity().get(i * VALUES_IN_PARTICLE + 8, buffer);
			}
		}));
		particleBuffer.copyToGPU();
	}

	public static void updateModelBuffers(MeshedBody meshedBody) {
		modelVerticesBuffer.updateBuffer(byteBuffer -> {
			MeshedBody.AttachedVertex vertex;
			for (int i = 0; i < meshedBody.getModelVertices().size(); i++) {
				vertex = meshedBody.getModelVertices().get(i);

				byteBuffer.putInt(i * (Float.BYTES * 3 + Integer.BYTES), vertex.tetIndex);
				byteBuffer.putFloat(i * (Float.BYTES * 3 + Integer.BYTES) + 4, (float) vertex.baryX);
				byteBuffer.putFloat(i * (Float.BYTES * 3 + Integer.BYTES) + 8, (float) vertex.baryY);
				byteBuffer.putFloat(i * (Float.BYTES * 3 + Integer.BYTES) + 12, (float) vertex.baryZ);
			}
		});
		modelVerticesBuffer.copyToGPU();

		modelIndexBuffer.updateBuffer(byteBuffer -> {
			byteBuffer.asIntBuffer().put(0, meshedBody.getModelIndices().toIntArray());
		});
		modelIndexBuffer.copyToGPU();

		tetrahedraIndexBuffer.updateBuffer(byteBuffer -> {
			final IntBuffer buffer = byteBuffer.asIntBuffer();

			IMesh.Tetrahedron tetrahedron;
			for (int i = 0; i < meshedBody.getTetrahedra().length; i++) {
				tetrahedron = meshedBody.getTetrahedra()[i];

				for (int j = 0; j < 4; j++) {
					buffer.put(i * 4 + j, tetrahedron.particles[j].getIndex());
				}
			}
		});
		tetrahedraIndexBuffer.copyToGPU();

		final Vector3f color = new Vector3f(0.9f, 0.6f, 0.3f);
		localVertexBuffer.updateBuffer(byteBuffer -> {
			final FloatBuffer buffer = byteBuffer.asFloatBuffer();

			for (int i = 0; i < meshedBody.getModelVertices().size(); i++) {
				color.get(i * 9 + 3, buffer);
			}
		});
		localVertexBuffer.copyToGPU();
	}

	public static void createDescriptorSets() {
		vkResetDescriptorPool(device, particleDescriptorPool.getHandle(), 0);
		particleDescriptorSet = particleDescriptorPool.createDescriptorSets(particlesDescriptorSetLayout).get(0);

		vkResetDescriptorPool(device, distanceConstraintDescriptorPool.getHandle(), 0);
		distanceConstraintsDescriptorSet = distanceConstraintDescriptorPool.createDescriptorSets(distanceConstraintDescriptorSetLayout).get(0);

		vkResetDescriptorPool(device, volumeConstraintDescriptorPool.getHandle(), 0);
		volumeConstraintsDescriptorSet = volumeConstraintDescriptorPool.createDescriptorSets(volumeConstraintDescriptorSetLayout).get(0);

		vkResetDescriptorPool(device, buffersDescriptorPool.getHandle(), 0);
		buffersDescriptorSet = buffersDescriptorPool.createDescriptorSets(buffersDescriptorSetLayout).get(0);
	}

	public static void updateParticleDescriptorSet() {
		particleDescriptorSet.updateBuilder()
				.write(0).buffer(particleBuffer.getGPUBuffer()).add()
				.update();
	}

	public static void updateConstraintsDescriptorSet() {
		distanceConstraintsDescriptorSet.updateBuilder()
				.write(0).buffer(distanceConstraintIndexBuffer.getGPUBuffer()).add()
				.write(1).buffer(distanceConstraintsBuffer.getGPUBuffer()).add()
				.update();

		volumeConstraintsDescriptorSet.updateBuilder()
				.write(0).buffer(volumeConstraintIndexBuffer.getGPUBuffer()).add()
				.write(1).buffer(volumeConstraintsBuffer.getGPUBuffer()).add()
				.update();
	}

	public static void updateBuffersDescriptorSet() {
		buffersDescriptorSet.updateBuilder()
				.write(0).buffer(modelVerticesBuffer.getGPUBuffer()).add()
				.write(1).buffer(tetrahedraIndexBuffer.getGPUBuffer()).add()
				.write(2).buffer(modelIndexBuffer.getGPUBuffer()).add()
				.write(3).buffer(localVertexBuffer.getGPUBuffer()).add()
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

			final ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANTS_SIZE);
			pushConstants.putDouble(0, PhysicsController.UPS_INV * PhysicsController.SUBSTEP_COUNT_INV);
			pushConstants.putDouble(8, PhysicsController.UPS * PhysicsController.SUBSTEP_COUNT * PhysicsController.UPS * PhysicsController.SUBSTEP_COUNT);

			final LongBuffer particleDescriptorSetPointer = stack.longs(particleDescriptorSet.getHandle());
			final LongBuffer distanceConstraintDescriptorSetPointer = stack.longs(distanceConstraintsDescriptorSet.getHandle());
			final LongBuffer volumeConstraintDescriptorSetPointer = stack.longs(volumeConstraintsDescriptorSet.getHandle());

			final LongBuffer buffersDescriptorSetPointer = stack.longs(buffersDescriptorSet.getHandle());

			final VkMemoryBarrier2KHR.Buffer memoryBarrier = VkMemoryBarrier2KHR.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2_KHR)
					.srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
					.srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)
					.dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
					.dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);

			final VkDependencyInfoKHR dependencyInfo = VkDependencyInfoKHR.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
					.pMemoryBarriers(memoryBarrier);

			final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

			vkResetCommandPool(device, commandPool.handle, 0);

			vkBeginCommandBuffer(commandBuffer, beginInfo);
			{
				for (int i = 0; i < PhysicsController.SUBSTEP_COUNT; i++) {
					pushConstants.putInt(24, particles.size());
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, integrationPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, integrationPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdPushConstants(commandBuffer, integrationPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) particles.size() / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// distance constraint
					pushConstants.putDouble(16, 10);
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, distanceConstraintPipeline.layout, 1, distanceConstraintDescriptorSetPointer, null);

					offset = 0;
					for (List<DistanceConstraint> constraints : coloredDistanceConstraints.values()) {
						pushConstants.putInt(24, constraints.size());
						pushConstants.putInt(28, offset);
						vkCmdPushConstants(commandBuffer, distanceConstraintPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
						vkCmdDispatch(commandBuffer, (int) Math.ceil((float) constraints.size() / WORKGROUP_COUNT), 1, 1);
						vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

						offset += constraints.size();
					}

					// volume constraint
					pushConstants.putDouble(16, 0.00001);
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.handle);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, volumeConstraintPipeline.layout, 1, volumeConstraintDescriptorSetPointer, null);

					offset = 0;
					for (List<TetrahedralVolumeConstraint> constraints : coloredVolumeConstraints.values()) {
						pushConstants.putInt(24, constraints.size());
						pushConstants.putInt(28, offset);
						vkCmdPushConstants(commandBuffer, volumeConstraintPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
						vkCmdDispatch(commandBuffer, (int) Math.ceil((float) constraints.size() / WORKGROUP_COUNT), 1, 1);
						vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

						offset += constraints.size();
					}

					// update velocities
					pushConstants.putInt(24, particles.size());
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, velocityPipeline.handle);
					vkCmdPushConstants(commandBuffer, integrationPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, velocityPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) particles.size() / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
				}

				if (localVertexBuffer != null) {
					// updateModelBuffers
					// updateBarycentric
					pushConstants.putInt(24, modelVerticesBuffer.getBufferSize() / (Integer.BYTES + 3 * Float.BYTES));
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersBarycentricPipeline.handle);
					vkCmdPushConstants(commandBuffer, buffersBarycentricPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersBarycentricPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersBarycentricPipeline.layout, 1, buffersDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) modelVerticesBuffer.getBufferSize() / (Integer.BYTES + 3 * Float.BYTES) / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// zero normals
					pushConstants.putInt(24, modelVerticesBuffer.getBufferSize() / (Integer.BYTES + 3 * Float.BYTES));
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersZeroNormalsPipeline.handle);
					vkCmdPushConstants(commandBuffer, buffersZeroNormalsPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersZeroNormalsPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersZeroNormalsPipeline.layout, 1, buffersDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) modelVerticesBuffer.getBufferSize() / (Integer.BYTES + 3 * Float.BYTES) / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// calculate normals
					pushConstants.putInt(24, modelIndexBuffer.getBufferSize() / (Integer.BYTES * 3));
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersCalculateNormalsPipeline.handle);
					vkCmdPushConstants(commandBuffer, buffersCalculateNormalsPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersCalculateNormalsPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersCalculateNormalsPipeline.layout, 1, buffersDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) modelIndexBuffer.getBufferSize() / (Integer.BYTES * 3) / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// normalize normals
					pushConstants.putInt(24, localVertexBuffer.getBufferSize() / (9 * Float.BYTES));
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersFinishNormalsPipeline.handle);
					vkCmdPushConstants(commandBuffer, buffersFinishNormalsPipeline.layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersFinishNormalsPipeline.layout, 0, particleDescriptorSetPointer, null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, buffersFinishNormalsPipeline.layout, 1, buffersDescriptorSetPointer, null);
					vkCmdDispatch(commandBuffer, (int) Math.ceil((float) localVertexBuffer.getBufferSize() / (9 * Float.BYTES) / WORKGROUP_COUNT), 1, 1);

					vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);

					// update particle buffer
					final VkBufferCopy.Buffer particlesCopyRegion = VkBufferCopy.calloc(1, stack).size(particleBuffer.getBufferSize());
					vkCmdCopyBuffer(commandBuffer, particleBuffer.getGPUBuffer().handle, particleBuffer.getCPUBuffer().handle, particlesCopyRegion);
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

		buffersDescriptorSetLayout = DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(1, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(2, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.addBinding(3, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
				.build();

		final VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stackGet())
				.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
				.size(PUSH_CONSTANTS_SIZE);

		integrationPipeline = VulkanCompute.createComputePipeline("shaders/physics/integrate.comp", particlesDescriptorSetLayout, pushConstantRanges);
		velocityPipeline = VulkanCompute.createComputePipeline("shaders/physics/updateVelocity.comp", particlesDescriptorSetLayout, pushConstantRanges);

		distanceConstraintPipeline = VulkanCompute.createComputePipeline("shaders/physics/distanceConstraint.comp",
		                                                                 new DescriptorSetLayout[] { particlesDescriptorSetLayout, distanceConstraintDescriptorSetLayout },
		                                                                 pushConstantRanges);

		volumeConstraintPipeline = VulkanCompute.createComputePipeline("shaders/physics/volumeConstraint.comp",
		                                                               new DescriptorSetLayout[] { particlesDescriptorSetLayout, volumeConstraintDescriptorSetLayout },
		                                                               pushConstantRanges);

		buffersZeroNormalsPipeline = VulkanCompute.createComputePipeline("shaders/buffers/zeroOutNormals.comp",
		                                                                 new DescriptorSetLayout[] { particlesDescriptorSetLayout, buffersDescriptorSetLayout },
		                                                                 pushConstantRanges);

		buffersBarycentricPipeline = VulkanCompute.createComputePipeline("shaders/buffers/updateBarycentric.comp",
		                                                                 new DescriptorSetLayout[] { particlesDescriptorSetLayout, buffersDescriptorSetLayout },
		                                                                 pushConstantRanges);

		buffersCalculateNormalsPipeline = VulkanCompute.createComputePipeline("shaders/buffers/calculateNormals.comp",
		                                                                      new DescriptorSetLayout[] { particlesDescriptorSetLayout, buffersDescriptorSetLayout },
		                                                                      pushConstantRanges);

		buffersFinishNormalsPipeline = VulkanCompute.createComputePipeline("shaders/buffers/finishNormals.comp",
		                                                                   new DescriptorSetLayout[] { particlesDescriptorSetLayout, buffersDescriptorSetLayout },
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

		buffersDescriptorPool = DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 4)
				.setMaxSets(1)
				.build();

		createDescriptorSets();

		commandPool = CommandPool.newDefault(0, queues.compute, false);
		commandBuffer = CommandBuffers.createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, commandPool.handle);

		copyCommandPool = CommandPool.newDefault(0, queues.transfer, true);
		copyCommandBuffer = CommandBuffers.createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, copyCommandPool.handle);
	}

	@SubscribeEvent
	public static void onVulkanDispose(VulkanDisposeEvent event) {
		particlesDescriptorSetLayout.dispose();
		distanceConstraintDescriptorSetLayout.dispose();
		volumeConstraintDescriptorSetLayout.dispose();
		buffersDescriptorSetLayout.dispose();

		particleDescriptorPool.dispose();
		distanceConstraintDescriptorPool.dispose();
		volumeConstraintDescriptorPool.dispose();
		buffersDescriptorPool.dispose();

		buffersFinishNormalsPipeline.dispose();
		buffersCalculateNormalsPipeline.dispose();
		buffersZeroNormalsPipeline.dispose();
		distanceConstraintPipeline.dispose();
		volumeConstraintPipeline.dispose();
		buffersBarycentricPipeline.dispose();
		velocityPipeline.dispose();
		integrationPipeline.dispose();

		commandPool.dispose();
		copyCommandPool.dispose();

		particleBuffer.dispose();
		distanceConstraintIndexBuffer.dispose();
		distanceConstraintsBuffer.dispose();
		volumeConstraintIndexBuffer.dispose();
		volumeConstraintsBuffer.dispose();
		modelVerticesBuffer.dispose();
		modelIndexBuffer.dispose();
		tetrahedraIndexBuffer.dispose();
		localVertexBuffer.dispose();
		vertexBuffer.dispose();
	}

	public static void simulate() {
		if (!ready) { return; }
		try (MemoryStack stack = stackPush()) {
			final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.pCommandBuffers(stack.pointers(commandBuffer));

			timer.startRecording();
			vkResetFences(device, commandPool.fence);
			queues.compute.submitAndWait(submitInfo, commandPool.fence);
			timer.endRecording();

			updateParticles();
		}
	}

	public static void updateParticles() {
		try (MemoryStack stack = stackPush()) {
			long time0 = System.nanoTime();
			final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.pCommandBuffers(stack.pointers(copyCommandBuffer));

			synchronized (PhysicsCompute.copyCommandPool) {
				vkResetFences(device, copyCommandPool.fence);
				queues.transfer.submitAndWait(submitInfo, copyCommandPool.fence);
			}

			final PointerBuffer pointer = stack.mallocPointer(1);
			vmaMapMemory(vmaAllocator, particleBuffer.getCPUBuffer().allocation, pointer);
			{
				final DoubleBuffer buffer = memDoubleBuffer(pointer.get(0), particles.size() * PARTICLE_SIZE);
				int index = 0;
				for (Particle particle : particles) {
					particle.getPosition().set(index, buffer);
//					particle.setMass(buffer.get(index + 12));
//					particle.getPrevPosition().set(index + 16, buffer);
//					particle.getVelocity().set(index + 32, buffer);

					index += VALUES_IN_PARTICLE;
				}
			}
			vmaUnmapMemory(vmaAllocator, particleBuffer.getCPUBuffer().allocation);
//			log.info("Copying particles: {}ms", (System.nanoTime() - time0) / 1_000_000d);
		}
	}

	public static double getComputeTime() {
		return timer.getAverageTime();
	}
}
