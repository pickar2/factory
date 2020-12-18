package xyz.sathro.factory.vulkan.utils;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.ClassLoader.getSystemClassLoader;
import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.NVRayTracing.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanUtils {
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

	public static void VkCheck(int err, String errormsg) {
		if (err != VK_SUCCESS) {
			throw new VulkanException(errormsg + ": " + VKReturnCode.getByCode(err));
		}
	}

	public static void VkCheck(int err, String errormsg, int expectedResult) {
		if (err != expectedResult) {
			throw new VulkanException(errormsg + ": " + VKReturnCode.getByCode(err));
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

	public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
		return ioResourceToByteBuffer(resource, bufferSize, null);
	}

	public static String ioResourceToString(String resource, Function<String, String> function) throws IOException {
		URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
		if (url == null) {
			throw new IOException("Classpath resource not found: " + resource);
		}
		StringBuilder sb = new StringBuilder();
		new BufferedReader(new InputStreamReader(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)),
		                                         StandardCharsets.UTF_8)).lines().forEach((str) -> sb.append(str).append("\n"));
		String out = sb.toString();
		if (function != null) {
			out = function.apply(out);
		}
		return out;
	}

	public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize, Function<String, String> function) throws IOException {
		ByteBuffer buffer;
		URL url = Thread.currentThread().getContextClassLoader().getResource(resource);

		String path = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource(resource)).getPath();
		if (path == null) {
			throw new IOException("Classpath resource not found: " + resource);
		}
		File file = new File(path);
		if (file.isFile()) {
			FileInputStream fis = new FileInputStream(file);
			FileChannel fc = fis.getChannel();
			buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			if (function != null) {
				CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
				byte[] bytes = new byte[buffer.remaining()];
				buffer.get(bytes);
				String str = new String(bytes, StandardCharsets.UTF_8);
				str = function.apply(str);
				buffer = encoder.encode(CharBuffer.wrap(str));
			}
			fc.close();
			fis.close();
		} else {
			buffer = BufferUtils.createByteBuffer(bufferSize);
			InputStream source = url.openStream();
			try (source) {
				if (source == null) {
					throw new FileNotFoundException(resource);
				}
				byte[] buf = new byte[8192];
				while (true) {
					int bytes = source.read(buf, 0, buf.length);
					if (bytes == -1) {
						break;
					}
					if (buffer.remaining() < bytes) {
						buffer = resizeBuffer(buffer, Math.max(buffer.capacity() * 2, buffer.capacity() - buffer.remaining() + bytes));
					}
					buffer.put(buf, 0, bytes);
				}
				buffer.flip();
			}
		}

		return buffer;
	}

	public static ByteBuffer compileShader(String path, int vulkanStage) {
		try {
			URI uri = Objects.requireNonNull(getSystemClassLoader().getResource(path)).toURI();

			if ("jar".equals(uri.getScheme())) {
				for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
					if (provider.getScheme().equalsIgnoreCase("jar")) {
						try {
							provider.getFileSystem(uri);
						} catch (FileSystemNotFoundException e) {
							provider.newFileSystem(uri, Collections.emptyMap());
						}
					}
				}
			}
			Path preparedPath = Paths.get(uri);

			String source = new String(Files.readAllBytes(preparedPath));
			long compiler = shaderc_compiler_initialize();

			if (compiler == NULL) {
				throw new RuntimeException("Failed to create shader compiler");
			}

			long result = shaderc_compile_into_spv(compiler, source, vulkanStageToShadercKind(vulkanStage), path, "main", NULL);

			if (result == NULL) {
				throw new RuntimeException("Failed to compile shader " + path + " into SPIR-V");
			}

			if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
				throw new RuntimeException("Failed to compile shader " + path + "into SPIR-V:\n " + shaderc_result_get_error_message(result));
			}

			shaderc_compiler_release(compiler);

			return shaderc_result_get_bytes(result);
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static ByteBuffer glslToSPIRV(String classPath, int vulkanStage) throws IOException {
		ByteBuffer src = ioResourceToByteBuffer(classPath, 1024);
		long compiler = shaderc_compiler_initialize();
		long options = shaderc_compile_options_initialize();
		ShadercIncludeResolve resolver;
		ShadercIncludeResultRelease releaser;
		shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
		shaderc_compile_options_set_include_callbacks(options, resolver = new ShadercIncludeResolve() {
			public long invoke(long user_data, long requested_source, int type, long requesting_source, long include_depth) {
				ShadercIncludeResult res = ShadercIncludeResult.calloc();
				try {
					String src = classPath.substring(0, classPath.lastIndexOf('/')) + "/" + memUTF8(requested_source);
					res.content(ioResourceToByteBuffer(src, 1024));
					res.source_name(memUTF8(src));
					return res.address();
				} catch (IOException e) {
					throw new AssertionError("Failed to resolve include: " + src);
				}
			}
		}, releaser = new ShadercIncludeResultRelease() {
			public void invoke(long user_data, long include_result) {
				ShadercIncludeResult result = ShadercIncludeResult.create(include_result);
				memFree(result.source_name());
				result.free();
			}
		}, 0L);
		long res;
		try (MemoryStack stack = stackPush()) {
			res = shaderc_compile_into_spv(compiler, src, vulkanStageToShadercKind(vulkanStage), stack.UTF8(classPath), stack.UTF8("main"), options);
			if (res == 0L) {
				throw new AssertionError("Internal error during compilation!");
			}
		}
		if (shaderc_result_get_compilation_status(res) != shaderc_compilation_status_success) {
			throw new AssertionError("Shader compilation failed: " + shaderc_result_get_error_message(res));
		}
		int size = (int) shaderc_result_get_length(res);
		ByteBuffer resultBytes = createByteBuffer(size);
		resultBytes.put(Objects.requireNonNull(shaderc_result_get_bytes(res)));
		resultBytes.flip();
		shaderc_compiler_release(res);
		shaderc_compiler_release(compiler);
		releaser.free();
		resolver.free();

		return resultBytes;
	}

	public static ByteBuffer glslToSPIRV(String classPath, int vulkanStage, Function<String, String> function) throws IOException {
		String src = ioResourceToString(classPath, function);
		long compiler = shaderc_compiler_initialize();
		long options = shaderc_compile_options_initialize();
		ShadercIncludeResolve resolver;
		ShadercIncludeResultRelease releaser;
		shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
		shaderc_compile_options_set_include_callbacks(options, resolver = new ShadercIncludeResolve() {
			public long invoke(long user_data, long requested_source, int type, long requesting_source, long include_depth) {
				ShadercIncludeResult res = ShadercIncludeResult.calloc();
				try {
					String src = classPath.substring(0, classPath.lastIndexOf('/')) + "/" + memUTF8(requested_source);
					res.content(ioResourceToByteBuffer(src, 1024));
					res.source_name(memUTF8(src));
					return res.address();
				} catch (IOException e) {
					throw new AssertionError("Failed to resolve include: " + src);
				}
			}
		}, releaser = new ShadercIncludeResultRelease() {
			public void invoke(long user_data, long include_result) {
				ShadercIncludeResult result = ShadercIncludeResult.create(include_result);
				memFree(result.source_name());
				result.free();
			}
		}, 0L);
		long res;
		try (MemoryStack stack = stackPush()) {
			ByteBuffer buffer = memUTF8(src, false);
			res = shaderc_compile_into_spv(compiler, buffer, vulkanStageToShadercKind(vulkanStage), stack.UTF8(classPath), stack.UTF8("main"), options);
			if (res == 0L) {
				throw new AssertionError("Internal error during compilation!");
			}
			memFree(buffer);
		}
		if (shaderc_result_get_compilation_status(res) != shaderc_compilation_status_success) {
			throw new AssertionError("Shader compilation failed: " + shaderc_result_get_error_message(res));
		}
		int size = (int) shaderc_result_get_length(res);
		ByteBuffer resultBytes = createByteBuffer(size);
		resultBytes.put(Objects.requireNonNull(shaderc_result_get_bytes(res)));
		resultBytes.flip();
		shaderc_compiler_release(res);
		shaderc_compiler_release(compiler);
		releaser.free();
		resolver.free();

		return resultBytes;
	}
}
