package xyz.sathro.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.CommandPool;
import xyz.sathro.vulkan.models.VulkanBuffer;
import xyz.sathro.vulkan.models.VulkanPipeline;
import xyz.sathro.vulkan.utils.CommandBuffers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Paths;
import java.util.function.Consumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memIntBuffer;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.vulkan.Vulkan.*;
import static xyz.sathro.vulkan.utils.VulkanUtils.VkCheck;

public class VulkanCompute {
	private VulkanCompute() { }

	public static void mandelbrot() {
		try (MemoryStack stack = stackPush()) {
			final int WIDTH = 3200;
			final int HEIGHT = 2400;
			final int WORKGROUP_SIZE = 32;
			final int bufferSize = WIDTH * HEIGHT * Integer.BYTES;

			final VulkanBuffer buffer = createBuffer(bufferSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_GPU_TO_CPU);

			final DescriptorSetLayout descriptorSetLayout = DescriptorSetLayout.builder()
					.addBinding(0, 1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT)
					.build();
			final DescriptorPool descriptorPool = DescriptorPool.builder()
					.setTypeSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1)
					.setMaxSets(1)
					.build();
			final DescriptorSet descriptorSet = descriptorPool.createDescriptorSets(descriptorSetLayout, 1).get(0);

			final VulkanPipeline pipeline = createComputePipeline("shaders/mandelbrot.comp", descriptorSetLayout);

			final VkDescriptorBufferInfo.Buffer descriptorBufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
					.buffer(buffer.buffer)
					.offset(0)
					.range(bufferSize);

			descriptorSet.updateBuilder()
					.write(0).pBufferInfo(descriptorBufferInfo).add()
					.update();

			executeOnce(commandBuffer -> {
				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle);
				vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.layout, 0, stack.longs(descriptorSet.getHandle()), null);

				vkCmdDispatch(commandBuffer, (int) Math.ceil((float) WIDTH / WORKGROUP_SIZE), (int) Math.ceil((float) HEIGHT / WORKGROUP_SIZE), 1);
			}, () -> {
				final BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
				final int[] array = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

				final PointerBuffer pointer = stack.mallocPointer(1);
				vmaMapMemory(vmaAllocator, buffer.allocation, pointer);
				memIntBuffer(pointer.get(0), bufferSize).get(array);
				vmaUnmapMemory(vmaAllocator, buffer.allocation);

				try {
					ImageIO.write(img, "png", Paths.get("test.png").toFile());
				} catch (IOException e) {
					e.printStackTrace();
				}

				buffer.dispose();
				descriptorPool.dispose();
				descriptorSetLayout.dispose();
				pipeline.dispose();
			});
		}
	}

	public static VulkanPipeline createComputePipeline(String shaderPath, DescriptorSetLayout descriptorSetLayout) {
		try (MemoryStack stack = stackPush()) {
			final long shaderModule = createShaderModule(shaderPath, VK_SHADER_STAGE_COMPUTE_BIT);

			final VkPipelineShaderStageCreateInfo shaderStageCreateInfo = VkPipelineShaderStageCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
					.stage(VK_SHADER_STAGE_COMPUTE_BIT)
					.module(shaderModule)
					.pName(stack.UTF8("main"));

			final VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = createPipelineLayout(descriptorSetLayout.getHandle(), null);

			final VulkanPipeline pipeline = new VulkanPipeline(null);
			final LongBuffer longBuffer = stack.mallocLong(1);
			VkCheck(vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, longBuffer), "Failed to create pipeline layout");
			pipeline.layout = longBuffer.get(0);

			final VkComputePipelineCreateInfo.Buffer pipelineInfos = VkComputePipelineCreateInfo.callocStack(1, stack)
					.sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
					.stage(shaderStageCreateInfo)
					.layout(pipeline.layout)
					.basePipelineHandle(VK_NULL_HANDLE);

			VkCheck(vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfos, null, longBuffer), "Failed to create compute pipeline");
			pipeline.handle = longBuffer.get(0);

			vkDestroyShaderModule(device, shaderModule, null);

			return pipeline;
		}
	}

	public static void executeOnce(Consumer<VkCommandBuffer> bufferConsumer, Runnable callback) {
		try (MemoryStack stack = stackPush()) {
			final CommandPool commandPool = CommandPool.newDefault(0, queues.compute, false);
			final VkCommandBuffer commandBuffer = CommandBuffers.beginSingleTimeCommands(commandPool.handle, stack);

			bufferConsumer.accept(commandBuffer);

			vkEndCommandBuffer(commandBuffer);

			final VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.pCommandBuffers(stack.pointers(commandBuffer));
			queues.compute.submit(submitInfo, commandPool.fence);

			callback.run();

			commandPool.dispose();
		}
	}
}
