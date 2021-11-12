package xyz.sathro.vulkan.utils;

import org.lwjgl.BufferUtils;
import xyz.sathro.factory.util.ResourceManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.NVRayTracing.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanUtils {
	private final static long shaderCompilerHandle = shaderc_compiler_initialize();
	private static int minUniformBufferOffsetAlignment;
	private static int maxPerStageDescriptorSampledImages;

	public static void init() {
//		VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.calloc();
//		vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties);
//
//		minUniformBufferOffsetAlignment = (int) deviceProperties.limits().minUniformBufferOffsetAlignment();
//		maxPerStageDescriptorSampledImages = deviceProperties.limits().maxPerStageDescriptorSampledImages();
//		deviceProperties.free();
	}

	public static int calculateUniformBufferAlignment(int bufferSize) {
		if (minUniformBufferOffsetAlignment > 0) {
			bufferSize = (bufferSize + minUniformBufferOffsetAlignment - 1) & -minUniformBufferOffsetAlignment;
		}
		return bufferSize;
	}

	public static int getMaxDescrSampledImages() {
		return maxPerStageDescriptorSampledImages;
	}

	private static int vulkanStageToShadercKind(int stage) {
		return switch (stage) {
			case VK_SHADER_STAGE_VERTEX_BIT -> shaderc_vertex_shader;
			case VK_SHADER_STAGE_FRAGMENT_BIT -> shaderc_fragment_shader;
			case VK_SHADER_STAGE_COMPUTE_BIT -> shaderc_compute_shader;
			case VK_SHADER_STAGE_RAYGEN_BIT_NV -> shaderc_raygen_shader;
			case VK_SHADER_STAGE_CLOSEST_HIT_BIT_NV -> shaderc_closesthit_shader;
			case VK_SHADER_STAGE_MISS_BIT_NV -> shaderc_miss_shader;
			case VK_SHADER_STAGE_ANY_HIT_BIT_NV -> shaderc_anyhit_shader;
			default -> throw new IllegalArgumentException("Stage: " + stage);
		};
	}

	public static void VkCheck(int err) {
		if (err != VK_SUCCESS) {
			throw new VulkanException("VULKAN ERROR: " + VKReturnCode.getByCode(err));
		}
	}

	public static void VkCheck(int err, String errorMsg) {
		if (err != VK_SUCCESS) {
			throw new VulkanException(errorMsg + ": " + VKReturnCode.getByCode(err));
		}
	}

	public static void VkCheck(int err, String errorMsg, int expectedResult) {
		if (err != expectedResult) {
			throw new VulkanException(errorMsg + ": " + VKReturnCode.getByCode(err));
		}
	}

	public static long[] BufferToArray(LongBuffer buffer) {
		long[] ret = new long[buffer.capacity()];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = buffer.get(i);
		}
		return ret;
	}

	public static int[] BufferToArray(IntBuffer buffer) {
		int[] ret = new int[buffer.capacity()];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = buffer.get(i);
		}
		return ret;
	}

	private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
		ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
		buffer.flip();
		newBuffer.put(buffer);
		return newBuffer;
	}

	public static ByteBuffer compileShader(String path, int vulkanStage) {
		String source = "";
		try {
			source = new String(Files.readAllBytes(ResourceManager.getPathFromString(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (shaderCompilerHandle == NULL) {
			throw new RuntimeException("Failed to create shader compiler");
		}

		long result = shaderc_compile_into_spv(shaderCompilerHandle, source, vulkanStageToShadercKind(vulkanStage), path, "main", NULL);
		if (result == NULL) {
			throw new RuntimeException("Failed to compile shader " + path + " into SPIR-V");
		}

		if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
			throw new RuntimeException("Failed to compile shader " + path + "into SPIR-V:\n " + shaderc_result_get_error_message(result));
		}

		return shaderc_result_get_bytes(result);
	}

	public static void dispose() {
		shaderc_compiler_release(shaderCompilerHandle);
	}
}
