package xyz.sathro.factory.vulkan;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.vulkan.models.Frame;
import xyz.sathro.factory.vulkan.models.VulkanBuffer;
import xyz.sathro.factory.vulkan.models.VulkanImage;
import xyz.sathro.factory.vulkan.models.VulkanPipeline;
import xyz.sathro.factory.vulkan.renderer.IRenderer;
import xyz.sathro.factory.vulkan.renderer.TestSceneRenderer;
import xyz.sathro.factory.vulkan.utils.VKReturnCode;
import xyz.sathro.factory.vulkan.utils.VulkanException;
import xyz.sathro.factory.vulkan.utils.VulkanUtils;
import xyz.sathro.factory.vulkan.vertex.IVertex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.EXTValidationFeatures.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
import static xyz.sathro.factory.vulkan.utils.BufferUtils.asPointerBuffer;
import static xyz.sathro.factory.vulkan.utils.VulkanUtils.VkCheck;
import static xyz.sathro.factory.vulkan.utils.VulkanUtils.glslToSPIRV;

public class Vulkan {
	private static final Logger log = LogManager.getLogger(Vulkan.class);

	private static final int UINT32_MAX = 0xFFFFFFFF;
	private static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;
	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	private static final int MAX_FRAMES_IN_FLIGHT = 2;
	private static final List<IRenderer> renderers = new ArrayList<>();
	public static final int nThreads = 17;
	private static final BlockingQueue<Long> transferCommandPools = new ArrayBlockingQueue<>(nThreads);
	private static final Long2LongMap transferFences = new Long2LongOpenHashMap(nThreads);
	public static final Set<String> INSTANCE_EXTENSIONS = new HashSet<>();
	//	Stream.of(
//			"VK_KHR_external_memory_capabilities"
//	).collect(toSet());
	// TODO: if using vulkan < 1.1, then we need ^
	public static final Set<String> DEVICE_EXTENSIONS = Stream.of(
			VK_KHR_SWAPCHAIN_EXTENSION_NAME
	).collect(toSet());
	public static final Set<String> VALIDATION_LAYERS = Stream.of(
			"VK_LAYER_KHRONOS_validation",
			"VK_LAYER_LUNARG_monitor"
	).collect(toSet());
	private static final boolean msaaEnabled = false; // TODO: proper support for turning msaa on and off
	private static int needUpdateCount = 0;
	public static VkInstance instance;
	public static int msaaSamples = VK_SAMPLE_COUNT_1_BIT;
	public static VkDevice device;
	public static boolean framebufferResize;
	public static long vmaAllocator;
	public static QueueFamilies queues;
	public static long surfaceHandle;
	public static boolean debugMode = true;
	public static VkPhysicalDevice physicalDevice;
	public static VkDebugUtilsMessengerCreateInfoEXT debugMessenger;
	public static VkDebugUtilsMessengerCallbackEXT debugCallback;
	public static long debugMessengerHandle;
	public static List<Long> swapChainImages;
	public static VkExtent2D swapChainExtent;
	public static VulkanImage colorImage;
	public static VulkanImage depthImage;
	public static VkCommandBuffer[] primaryCommandBuffers;
	public static List<Frame> inFlightFrames;
	private static long commandPool;
	private static long window;
	private static long swapChain;
	private static int swapChainImageFormat;
	public static List<Long> swapChainImageViews;
	public static List<Long> swapChainFramebuffers;
	public static long renderPass;
	public static Map<Integer, Frame> imagesInFlight;
	public static int currentFrame;
	private static boolean integratedGPU = false;

	private Vulkan() { }

	private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
		ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
		buffer.flip();
		newBuffer.put(buffer);

		return newBuffer;
	}

	private static Set<String> getAvailableExtensions(ByteBuffer layer) {
		Set<String> set = new HashSet<>();
		try (MemoryStack stack = stackPush()) {
			IntBuffer intBuf = stack.mallocInt(1);
			vkEnumerateInstanceExtensionProperties(layer, intBuf, null);
			VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.callocStack(intBuf.get(0), stack);
			vkEnumerateInstanceExtensionProperties(layer, intBuf, availableExtensions);
			for (VkExtensionProperties ext : availableExtensions) {
				set.add(ext.extensionNameString());
			}
		}

		return set;
	}

	private static void createDeviceQueues() {
		try (MemoryStack stack = stackPush()) {
			PointerBuffer pointerBuf = stack.mallocPointer(1);

			vkGetDeviceQueue(device, queues.graphics.index, 0, pointerBuf);
			queues.graphics.queue = new VkQueue(pointerBuf.get(0), device);

			vkGetDeviceQueue(device, queues.present.index, 0, pointerBuf);
			queues.present.queue = new VkQueue(pointerBuf.get(0), device);

			vkGetDeviceQueue(device, queues.transfer.index, 0, pointerBuf);
			queues.transfer.queue = new VkQueue(pointerBuf.get(0), device);
		}
	}

	private static long createSurface() {
		try (MemoryStack stack = stackPush()) {
			LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);

			if (glfwCreateWindowSurface(instance, window, null, pSurface) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create window surface");
			}

			return pSurface.get(0);
		}
	}

	private static boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {
		try (MemoryStack stack = stackPush()) {
			IntBuffer extensionCount = stack.ints(0);
			vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

			VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);
			vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);

			return availableExtensions.stream().map(VkExtensionProperties::extensionNameString).collect(toSet()).containsAll(DEVICE_EXTENSIONS);
		}
	}

	private static QueueFamilies findQueueFamilies(VkPhysicalDevice physicalDevice) {
		QueueFamilies families = new QueueFamilies();

		try (MemoryStack stack = stackPush()) {
			IntBuffer intBuf = stack.mallocInt(1);

			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, intBuf, null);

			VkQueueFamilyProperties.Buffer queueProps = VkQueueFamilyProperties.mallocStack(intBuf.get(0), stack);

			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, intBuf, queueProps);

			for (int i = 0; i < queueProps.capacity() && !families.isComplete(); i++) {
				int flags = queueProps.get(i).queueFlags();

				if (i != families.getTransferIndex() && (flags & VK_QUEUE_GRAPHICS_BIT) != 0 && !families.hasGraphics()) {
					families.graphics = new VulkanQueue(i);
				}

				vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surfaceHandle, intBuf);
				if (i != families.getTransferIndex() && (intBuf.get(0) == VK_TRUE && !families.hasPresent())) {
					families.present = new VulkanQueue(i);
				}

				if (i != families.getGraphicsIndex() && i != families.getPresentIndex() && (flags & VK_QUEUE_COMPUTE_BIT) == 0 && (flags & VK_QUEUE_GRAPHICS_BIT) == 0 && (flags & VK_QUEUE_TRANSFER_BIT) != 0 && !families.hasTransfer()) {
					families.transfer = new VulkanQueue(i);
				}
			}
			if (!families.hasGraphics() || !families.hasPresent()) {
				throw new VulkanException("Device doesn't have present or graphics queues"); //TODO: on server you don't need those queues
			}

			if (!families.hasTransfer()) {
				families.transfer = families.graphics;
				//TODO: optimizations because we don't use unique transfer queue (OR ARE WE????)
			}

			if (families.getGraphicsIndex() == families.getPresentIndex()) {
				families.present = families.graphics;
			}

			return families;
		}
	}

	public static SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device, MemoryStack stack) {
		SwapChainSupportDetails details = new SwapChainSupportDetails();

		details.capabilities = VkSurfaceCapabilitiesKHR.mallocStack(stack);
		vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surfaceHandle, details.capabilities);

		IntBuffer count = stack.ints(0);

		vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceHandle, count, null);

		if (count.get(0) != 0) {
			details.formats = VkSurfaceFormatKHR.mallocStack(count.get(0), stack);
			vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceHandle, count, details.formats);
		}

		vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceHandle, count, null);

		if (count.get(0) != 0) {
			details.presentModes = stack.mallocInt(count.get(0));
			vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceHandle, count, details.presentModes);
		}

		return details;
	}

	private static boolean isDeviceSuitable(VkPhysicalDevice device) {
		QueueFamilies families = findQueueFamilies(device);

		boolean extensionsSupported = checkDeviceExtensionSupport(device);
		boolean swapChainAdequate = false;
		boolean anisotropySupported = false;

		if (extensionsSupported) {
			try (MemoryStack stack = stackPush()) {
				SwapChainSupportDetails swapChainSupport = querySwapChainSupport(device, stack);
				swapChainAdequate = swapChainSupport.formats.hasRemaining() && swapChainSupport.presentModes.hasRemaining();
				VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.mallocStack(stack);
				vkGetPhysicalDeviceFeatures(device, supportedFeatures);
				anisotropySupported = supportedFeatures.samplerAnisotropy();
			}
		}

		return families.isComplete() && extensionsSupported && swapChainAdequate && anisotropySupported;
	}

	private static VkPhysicalDevice pickPhysicalDevice() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer deviceCount = stack.ints(0);

			vkEnumeratePhysicalDevices(instance, deviceCount, null);

			if (deviceCount.get(0) == 0) {
				throw new RuntimeException("Failed to find GPUs with Vulkan support");
			}

			PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

			vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

			VkPhysicalDevice device = null;
			for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
				device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

				if (isDeviceSuitable(device)) { break; }
				device = null;
			}

			if (device == null) {
				throw new RuntimeException("Failed to find a suitable GPU");
			}

			Vulkan.msaaSamples = msaaEnabled ? getMaxUsableSampleCount(device) : VK_SAMPLE_COUNT_1_BIT;

			return device;
		}
	}

	private static int getMaxUsableSampleCount(VkPhysicalDevice device) {
		try (MemoryStack stack = stackPush()) {
			VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.mallocStack(stack);
			vkGetPhysicalDeviceProperties(device, physicalDeviceProperties);

			int sampleCountFlags = physicalDeviceProperties.limits().framebufferColorSampleCounts() & physicalDeviceProperties.limits().framebufferDepthSampleCounts();

			if ((sampleCountFlags & VK_SAMPLE_COUNT_64_BIT) != 0) {return VK_SAMPLE_COUNT_64_BIT;}
			if ((sampleCountFlags & VK_SAMPLE_COUNT_32_BIT) != 0) {return VK_SAMPLE_COUNT_32_BIT;}
			if ((sampleCountFlags & VK_SAMPLE_COUNT_16_BIT) != 0) {return VK_SAMPLE_COUNT_16_BIT;}
			if ((sampleCountFlags & VK_SAMPLE_COUNT_8_BIT) != 0) {return VK_SAMPLE_COUNT_8_BIT;}
			if ((sampleCountFlags & VK_SAMPLE_COUNT_4_BIT) != 0) {return VK_SAMPLE_COUNT_4_BIT;}
			if ((sampleCountFlags & VK_SAMPLE_COUNT_2_BIT) != 0) {return VK_SAMPLE_COUNT_2_BIT;}

			return VK_SAMPLE_COUNT_1_BIT;
		}
	}

	private static VkDevice createLogicalDevice() {
		try (MemoryStack stack = stackPush()) {
			int[] uniqueQueueFamilies = queues.unique();

			VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueQueueFamilies.length, stack);

			for (int i = 0; i < uniqueQueueFamilies.length; i++) {
				queueCreateInfos.get(i)
						.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
						.queueFamilyIndex(uniqueQueueFamilies[i])
						.pQueuePriorities(stack.floats(1.0f));
			}

			VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack)
					.samplerAnisotropy(true)
					.fragmentStoresAndAtomics(true)
					.vertexPipelineStoresAndAtomics(true)
					.sampleRateShading(msaaEnabled)
					.fillModeNonSolid(true);

			VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
					.pQueueCreateInfos(queueCreateInfos)
					.pEnabledFeatures(deviceFeatures)
					.ppEnabledExtensionNames(asPointerBuffer(DEVICE_EXTENSIONS));

			if (debugMode) {
				createInfo.ppEnabledLayerNames(asPointerBuffer(VALIDATION_LAYERS));
			}

			PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

			if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create logical device");
			}

			return new VkDevice(pDevice.get(0), physicalDevice, createInfo);
		}
	}

	private static VkDebugUtilsMessengerCreateInfoEXT getDebugMessenger() {
		int messageTypes = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
		int messageSeverities = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;

		return VkDebugUtilsMessengerCreateInfoEXT.calloc()
				.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
				.messageSeverity(messageSeverities)
				.messageType(messageTypes)
				.pfnUserCallback(debugCallback);
	}

	private static long setupDebugging() {
		try (MemoryStack stack = stackPush()) {
			LongBuffer longBuf = stack.mallocLong(1);
			VkCheck(vkCreateDebugUtilsMessengerEXT(instance, debugMessenger, null, longBuf), "Failed to create DebugUtilsMessenger");
			return longBuf.get(0);
		}
	}

	private static boolean checkValidationLayerSupport() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer layerCount = stack.ints(0);
			vkEnumerateInstanceLayerProperties(layerCount, null);
			VkLayerProperties.Buffer availableLayers = VkLayerProperties.mallocStack(layerCount.get(0), stack);
			vkEnumerateInstanceLayerProperties(layerCount, availableLayers);
			Set<String> availableLayerNames = availableLayers.stream().map(VkLayerProperties::layerNameString).collect(toSet());
			return availableLayerNames.containsAll(VALIDATION_LAYERS);
		}
	}

	private static long createAllocator() {
		try (MemoryStack stack = stackPush()) {
			VmaVulkanFunctions functions = VmaVulkanFunctions.callocStack(stack).set(instance, device);

			VmaAllocatorCreateInfo allocatorInfo = VmaAllocatorCreateInfo.callocStack(stack)
					.device(device)
					.physicalDevice(physicalDevice)
					.pVulkanFunctions(functions);

			if (device.getCapabilities().VK_KHR_dedicated_allocation) {
				allocatorInfo.flags(VMA_ALLOCATOR_CREATE_KHR_DEDICATED_ALLOCATION_BIT);
			}

			PointerBuffer pointerBuffer = stack.mallocPointer(1);
			VkCheck(vmaCreateAllocator(allocatorInfo, pointerBuffer), "Unable to create VMA allocator");
			return pointerBuffer.get(0);
		}
	}

	private static VkInstance createVkInstance() {
		try (MemoryStack stack = stackPush()) {
			if (debugMode && !checkValidationLayerSupport()) {
				throw new VulkanException("Validation requested but not supported");
			}

			PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
			if (requiredExtensions == null) {
				throw new VulkanException("Failed to find list of required Vulkan extensions");
			}

			VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
					.pApplicationName(stack.UTF8("factory"))
					.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
					.pEngineName(stack.UTF8("factory engine"))
					.engineVersion(VK_MAKE_VERSION(1, 0, 0))
					.apiVersion(VK_API_VERSION_1_2);

			Set<String> availableExtensions = getAvailableExtensions(null);
			Set<String> additionalExtensions = new HashSet<>(INSTANCE_EXTENSIONS);

			VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
					.pApplicationInfo(appInfo);

			if (debugMode) {
				int[] enabledFeaturesFlags = {
						VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT,
						VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT
				};

				VkValidationFeaturesEXT validationFeatures = VkValidationFeaturesEXT.callocStack(stack)
						.sType(VK_STRUCTURE_TYPE_VALIDATION_FEATURES_EXT)
						.pEnabledValidationFeatures(stack.ints(enabledFeaturesFlags));

				for (String validationLayer : VALIDATION_LAYERS) {
					availableExtensions.addAll(getAvailableExtensions(stack.UTF8(validationLayer)));
				}

				createInfo.ppEnabledLayerNames(asPointerBuffer(VALIDATION_LAYERS));
				additionalExtensions.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
				additionalExtensions.add(VK_EXT_VALIDATION_FEATURES_EXTENSION_NAME);

				debugMessenger.pNext(validationFeatures.address());
				createInfo.pNext(debugMessenger.address());
			}
			// TODO: crash on missing extension?
			additionalExtensions.removeIf(ext -> !availableExtensions.contains(ext));

			PointerBuffer ppExtensions = stack.mallocPointer(requiredExtensions.capacity() + additionalExtensions.size());
			ppExtensions.put(requiredExtensions);
			for (String additionalExtension : additionalExtensions) {
				ppExtensions.put(stack.UTF8(additionalExtension));
			}
			ppExtensions.flip();

			createInfo.ppEnabledExtensionNames(ppExtensions);

			PointerBuffer instancePtr = stack.mallocPointer(1);

			VkCheck(vkCreateInstance(createInfo, null, instancePtr), "Failed to create instance");

			return new VkInstance(instancePtr.get(0), createInfo);
		}
	}

	public static ByteBuffer ioResourceToByteBuffer(String resource) {
		ByteBuffer buffer = null;
		try (InputStream source = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
		     ReadableByteChannel rbc = Channels.newChannel(Objects.requireNonNull(source))) {
			buffer = createByteBuffer(source.available());

			while (true) {
				int bytes = rbc.read(buffer);
				if (bytes == -1) {
					break;
				}
				if (buffer.remaining() == 0) {
					buffer = resizeBuffer(buffer, buffer.capacity() * 3 / 2); // 50%
				}
			}
			buffer = resizeBuffer(buffer, buffer.capacity() - buffer.remaining());
			buffer.flip();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return buffer;
	}

	private static VulkanImage createImage(int width, int height, int mipLevels, int numSamples, int format, int tiling, int usage, int memoryUsage) {
		VulkanImage image = new VulkanImage();
		try (MemoryStack stack = stackPush()) {
			VkImageCreateInfo imageInfo = VkImageCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
					.imageType(VK_IMAGE_TYPE_2D)
					.extent(e -> {
						e.width(width);
						e.height(height);
						e.depth(1);
					})
					.mipLevels(mipLevels)
					.arrayLayers(1)
					.format(format)
					.tiling(tiling)
					.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
					.usage(usage)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
					.samples(numSamples);

			LongBuffer longBuffer = stack.mallocLong(1);
			PointerBuffer pointerBuffer = stack.mallocPointer(1);

			VkCheck(vmaCreateImage(vmaAllocator, imageInfo, VmaAllocationCreateInfo.callocStack(stack).usage(memoryUsage), longBuffer, pointerBuffer, null), "Failed to create image");
			image.image = longBuffer.get(0);
			image.allocation = pointerBuffer.get(0);
			image.mipLevels = mipLevels;
		}
		return image;
	}

	public static void copyMemory(long from, int size, long allocation) {
		try (MemoryStack stack = stackPush()) {
			PointerBuffer memoryPointer = stack.mallocPointer(1);
			VkCheck(vmaMapMemory(vmaAllocator, allocation, memoryPointer), "Unable to map memory");
			memCopy(from, memoryPointer.get(0), size);
			vmaUnmapMemory(vmaAllocator, allocation);
		}
	}

	public static VulkanBuffer createIndexBuffer(int[] indices) {
		int bufferSize = Integer.BYTES * indices.length;
		VulkanBuffer indexBuffer;

		try (MemoryStack stack = stackPush()) {
			PointerBuffer data = stack.mallocPointer(1);
			if (integratedGPU) {
				indexBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

				vmaMapMemory(vmaAllocator, indexBuffer.allocation, data);
				data.getIntBuffer(0, bufferSize).put(indices);
				vmaUnmapMemory(vmaAllocator, indexBuffer.allocation);
			} else {
				VulkanBuffer stagingBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);

				vmaMapMemory(vmaAllocator, stagingBuffer.allocation, data);
				data.getIntBuffer(0, bufferSize).put(indices);
				vmaUnmapMemory(vmaAllocator, stagingBuffer.allocation);

				indexBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
				copyBuffer(stagingBuffer, indexBuffer, bufferSize);
				stagingBuffer.dispose();
			}
		}

		return indexBuffer;
	}

	public static VulkanBuffer createBuffer(int size, int usage, int memoryUsage) {
		VulkanBuffer buffer = new VulkanBuffer();
		try (MemoryStack stack = stackPush()) {
			VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
					.size(size)
					.usage(usage)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			LongBuffer longBuffer = stack.mallocLong(1);
			PointerBuffer pointerBuffer = stack.mallocPointer(1);

			VkCheck(vmaCreateBuffer(vmaAllocator, bufferInfo, VmaAllocationCreateInfo.callocStack(stack).usage(memoryUsage), longBuffer, pointerBuffer, null),
			        "Failed to allocate buffer");

			buffer.buffer = longBuffer.get(0);
			buffer.allocation = pointerBuffer.get(0);
		}

		return buffer;
	}

	private static void copyBufferToImage(long buffer, VulkanImage vulkanImage) {
		try (MemoryStack stack = stackPush()) {
			final long commandPool = transferCommandPools.take();

			VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, queues.transfer, stack);

			VkBufferImageCopy.Buffer region = VkBufferImageCopy.callocStack(1, stack)
					.bufferOffset(0)
					.bufferRowLength(0)
					.bufferImageHeight(0);

			region.imageSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
			region.imageOffset().set(0, 0, 0);
			region.imageExtent(VkExtent3D.callocStack(stack).set(vulkanImage.width, vulkanImage.height, 1));

			vkCmdCopyBufferToImage(commandBuffer, buffer, vulkanImage.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

			endSingleTimeCommands(commandBuffer, commandPool, queues.transfer, transferFences.get(commandPool), stack);

			transferCommandPools.put(commandPool);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void copyBuffer(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer, long size) {
		try (MemoryStack stack = stackPush()) {
			final long commandPool = transferCommandPools.take();

			VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, queues.transfer, stack);

			VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack).size(size);
			vkCmdCopyBuffer(commandBuffer, srcBuffer.buffer, dstBuffer.buffer, copyRegion);

			endSingleTimeCommands(commandBuffer, commandPool, queues.transfer, transferFences.get(commandPool), stack);

			transferCommandPools.put(commandPool);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void endSingleTimeCommands(VkCommandBuffer commandBuffer, long commandPool, VulkanQueue queue, MemoryStack stack) {
		vkEndCommandBuffer(commandBuffer);

		VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer));

		vkQueueSubmit(queue.queue, submitInfo, VK_NULL_HANDLE);
		vkQueueWaitIdle(queue.queue);

		vkFreeCommandBuffers(device, commandPool, commandBuffer);
	}

	private static void endSingleTimeCommands(VkCommandBuffer commandBuffer, long commandPool, VulkanQueue queue, long fence, MemoryStack stack) {
		vkEndCommandBuffer(commandBuffer);

		VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer));

		vkResetFences(device, fence);
		synchronized (queue.index) {
			vkQueueSubmit(queue.queue, submitInfo, fence);
			vkWaitForFences(device, fence, true, UINT64_MAX);
		}

		vkFreeCommandBuffers(device, commandPool, commandBuffer);
	}

	private static void runSingleTimeCommand(Consumer<VkCommandBuffer> consumer, long commandPool, VulkanQueue queue, MemoryStack stack) {
		VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, queue, stack);
		consumer.accept(commandBuffer);
		endSingleTimeCommands(commandBuffer, commandPool, queue, stack);
	}

	private static void runSingleTimeCommand(Consumer<VkCommandBuffer> consumer, MemoryStack stack) {
		VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, queues.graphics, stack);
		consumer.accept(commandBuffer);
		endSingleTimeCommands(commandBuffer, commandPool, queues.graphics, stack);
	}

	private static void runSingleTimeCommand(Consumer<VkCommandBuffer> consumer) {
		try (MemoryStack stack = stackPush()) {
			runSingleTimeCommand(consumer, stack);
		}
	}

	private static VkCommandBuffer beginSingleTimeCommands(long commandPool, VulkanQueue queue, MemoryStack stack) {
		VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
				.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
				.commandPool(commandPool)
				.commandBufferCount(1);

		PointerBuffer pCommandBuffer = stack.mallocPointer(1);
		vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
		VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

		VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
				.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

		vkBeginCommandBuffer(commandBuffer, beginInfo);

		return commandBuffer;
	}

	private static VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) { //TODO: check different formats
		for (VkSurfaceFormatKHR format : formats) {
			//VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_B8G8R8A8_UNORM
			if (format.format() == VK_FORMAT_R8G8B8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
				return format;
			}
		}

		return formats.get(0);
	}

	public static long createShaderModuleCustom(String path, int shaderType, UnaryOperator<String> function) {
		try (MemoryStack stack = stackPush()) {
			ByteBuffer code = glslToSPIRV(path, shaderType, function);
			LongBuffer longBuffer = stack.mallocLong(1);

			VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
					.pCode(code);
			VkCheck(vkCreateShaderModule(device, createInfo, null, longBuffer), "Failed to create shader module " + path);

			return longBuffer.get(0);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static long createShaderModule(String path, int shaderType) {
		try (MemoryStack stack = stackPush()) {
			ByteBuffer code = VulkanUtils.compileShader(path, shaderType);
			LongBuffer longBuffer = stack.mallocLong(1);

			VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
					.pCode(code);
			VkCheck(vkCreateShaderModule(device, createInfo, null, longBuffer), "Failed to create shader module " + path);

			return longBuffer.get(0);
		}
	}

	public static VkPipelineShaderStageCreateInfo.Buffer createShaderStages(long[] vertexShaderModules, long[] fragmentShaderModules) {
		ByteBuffer name = stackUTF8("main");

		VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(vertexShaderModules.length + fragmentShaderModules.length);
		for (int i = 0; i < vertexShaderModules.length; i++) {
			shaderStages.get(i)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
					.stage(VK_SHADER_STAGE_VERTEX_BIT)
					.module(vertexShaderModules[i])
					.pName(name);
		}
		for (int i = 0; i < fragmentShaderModules.length; i++) {
			shaderStages.get(i + vertexShaderModules.length)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
					.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
					.module(fragmentShaderModules[i])
					.pName(name);
		}

		return shaderStages;
	}

	public static VkPipelineVertexInputStateCreateInfo createVertexInputInfo(IVertex vertex) {
		return VkPipelineVertexInputStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
				.pVertexAttributeDescriptions(vertex.getAttributeDescriptions())
				.pVertexBindingDescriptions(vertex.getBindingDescription());
	}

	public static VkPipelineInputAssemblyStateCreateInfo createInputAssembly(int topology) {
		return VkPipelineInputAssemblyStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
				.topology(topology)
				.primitiveRestartEnable(false);
	}

	public static VkPipelineViewportStateCreateInfo createViewportState() {
		VkViewport.Buffer viewport = VkViewport.callocStack(1)
				.x(0)
				.y(0)
				.width(swapChainExtent.width())
				.height(swapChainExtent.height())
				.minDepth(0)
				.maxDepth(1);

		VkOffset2D offset = VkOffset2D.callocStack().set(0, 0);
		VkRect2D.Buffer scissor = VkRect2D.callocStack(1)
				.offset(offset)
				.extent(swapChainExtent);

		return VkPipelineViewportStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
				.viewportCount(1)
				.pViewports(viewport)
				.scissorCount(1)
				.pScissors(scissor);
	}

	public static VkPipelineRasterizationStateCreateInfo createRasterizer(int polygonMode, float lineWidth, int cullMode) {
		return VkPipelineRasterizationStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
				.depthClampEnable(false)
				.rasterizerDiscardEnable(false)
				.polygonMode(polygonMode)
				.lineWidth(lineWidth)
				.cullMode(cullMode)
				.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
				.depthBiasEnable(false);
	}

	public static VkPipelineMultisampleStateCreateInfo createMultisampling() {
		return VkPipelineMultisampleStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
				.sampleShadingEnable(msaaEnabled)
				.minSampleShading(msaaEnabled ? 0.2f : 0)
				.rasterizationSamples(msaaSamples);
	}

	public static VkPipelineColorBlendStateCreateInfo createColorBlending(int colorWriteMask) {
		VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1)
				.blendEnable(true)
				.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
				.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.colorBlendOp(VK_BLEND_OP_ADD)
				.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
				.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
				.alphaBlendOp(VK_BLEND_OP_ADD)
				.colorWriteMask(colorWriteMask);

		return VkPipelineColorBlendStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
				.logicOpEnable(false)
				.pAttachments(colorBlendAttachment)
				.logicOp(VK_LOGIC_OP_COPY)
				.blendConstants(stackCallocFloat(4));
	}

	public static VkPipelineColorBlendStateCreateInfo createColorBlending(int colorWriteMask, int srcAlpha, int dstAlpha) {
		VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1)
				.blendEnable(true)
				.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
				.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.colorBlendOp(VK_BLEND_OP_ADD)
				.srcAlphaBlendFactor(srcAlpha)
				.dstAlphaBlendFactor(dstAlpha)
				.alphaBlendOp(VK_BLEND_OP_ADD)
				.colorWriteMask(colorWriteMask);

		return VkPipelineColorBlendStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
				.logicOpEnable(false)
				.pAttachments(colorBlendAttachment)
				.logicOp(VK_LOGIC_OP_COPY)
				.blendConstants(stackCallocFloat(4));
	}

	public static VkPipelineLayoutCreateInfo createPipelineLayout(long layout, VkPushConstantRange.Buffer pushConstantRanges) {
		return VkPipelineLayoutCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(stackLongs(layout))
				.pPushConstantRanges(pushConstantRanges);
	}

	public static VkPipelineLayoutCreateInfo createPipelineLayout(long[] layouts, VkPushConstantRange.Buffer pushConstantRanges) {
		LongBuffer buffer = MemoryStack.stackMallocLong(layouts.length);
		buffer.put(layouts);
		buffer.flip();

		return VkPipelineLayoutCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(buffer)
				.pPushConstantRanges(pushConstantRanges);
	}

	public static VkPipelineDepthStencilStateCreateInfo createDepthStencil(int depthCompareOp, boolean enableDepthWrite) {
		return VkPipelineDepthStencilStateCreateInfo.callocStack()
				.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
				.depthCompareOp(depthCompareOp)
				.depthWriteEnable(enableDepthWrite)
				.depthTestEnable(true)
				.depthBoundsTestEnable(false)
				.stencilTestEnable(false);
	}

	public static VulkanPipeline createGraphicsPipeline(Class<? extends IVertex> vertexType,
	                                                    VkPipelineShaderStageCreateInfo.Buffer shaderStages,
	                                                    VkPipelineVertexInputStateCreateInfo vertexInputInfo,
	                                                    VkPipelineInputAssemblyStateCreateInfo inputAssembly,
	                                                    VkPipelineViewportStateCreateInfo viewportState,
	                                                    VkPipelineRasterizationStateCreateInfo rasterizer,
	                                                    VkPipelineMultisampleStateCreateInfo multisampling,
	                                                    VkPipelineColorBlendStateCreateInfo colorBlending,
	                                                    VkPipelineLayoutCreateInfo pipelineLayoutInfo,
	                                                    VkPipelineDepthStencilStateCreateInfo depthStencil) {
		VulkanPipeline pipeline = new VulkanPipeline(vertexType);
		try (MemoryStack stack = stackPush()) {
			LongBuffer longBuffer = stack.mallocLong(1);
			VkCheck(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, longBuffer), "Failed to create pipeline layout");
			pipeline.layout = longBuffer.get(0);

			VkGraphicsPipelineCreateInfo.Buffer pipelineInfos = VkGraphicsPipelineCreateInfo.callocStack(1, stack)
					.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
					.pStages(shaderStages)
					.pVertexInputState(vertexInputInfo)
					.pInputAssemblyState(inputAssembly)
					.pViewportState(viewportState)
					.pRasterizationState(rasterizer)
					.pMultisampleState(multisampling)
					.pColorBlendState(colorBlending)
					.pDepthStencilState(depthStencil)
					.layout(pipeline.layout)
					.renderPass(renderPass)
					.subpass(0)
					.basePipelineHandle(VK_NULL_HANDLE);

			VkCheck(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfos, null, longBuffer), "Failed to create graphics pipeline");
			pipeline.pipeline = longBuffer.get(0);
		}
		return pipeline;
	}

	public static void run() {
		initWindow();
		initVulkan();
		mainLoop();
		cleanup();
	}

	private static void initWindow() {
		if (!glfwInit()) {
			throw new RuntimeException("Cannot initialize GLFW");
		}

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

		String title = "Vulkan demo";

		window = glfwCreateWindow(WIDTH, HEIGHT, title, NULL, NULL);

		if (window == NULL) {
			throw new RuntimeException("Cannot create window");
		}

		glfwSetFramebufferSizeCallback(window, Vulkan::framebufferResizeCallback);
	}

	private static void framebufferResizeCallback(long window, int width, int height) {
		framebufferResize = true;
	}

	private static void initVulkan() {
		if (debugMode) {
			debugCallback = new VkDebugUtilsMessengerCallbackEXT() {
				@Override
				public int invoke(int messageSeverity, int messageTypes, long pCallbackData, long pUserData) {
					VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
					log.warn(("[VULKAN] " + data.pMessageString()));
					return 0;
				}
			};
			debugMessenger = getDebugMessenger();
		} else {
			debugCallback = null;
			debugMessenger = null;
		}

		instance = createVkInstance();

		if (debugMode) {
			debugMessengerHandle = setupDebugging();
		} else {
			debugMessengerHandle = VK_NULL_HANDLE;
		}
		surfaceHandle = createSurface();

		physicalDevice = pickPhysicalDevice();
		VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.malloc();
		vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties);

		if (physicalDeviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
			integratedGPU = true;
		}

		queues = findQueueFamilies(physicalDevice);
		device = createLogicalDevice();

		createDeviceQueues();

		vmaAllocator = createAllocator();

		commandPool = createCommandPool(0, queues.graphics.index);

		VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
				.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
				.flags(VK_FENCE_CREATE_SIGNALED_BIT);

		final int amount = transferCommandPools.remainingCapacity();
		LongBuffer longBuffer = memAllocLong(1);
		for (int i = 0; i < amount; i++) {
			final long pool = createCommandPool(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, queues.transfer.index);
			VkCheck(vkCreateFence(device, fenceInfo, null, longBuffer), "Failed to create semaphore");
			transferCommandPools.add(pool);
			transferFences.put(pool, longBuffer.get(0));
		}
		memFree(longBuffer);

		renderers.add(TestSceneRenderer.INSTANCE);

		createSwapChain();
		createImageViews();
		createRenderPass();
		if (msaaEnabled)
		createColorResources();
		createDepthResources();
		createFramebuffers();
		renderers.forEach(IRenderer::init);

		primaryCommandBuffers = createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, swapChainImages.size(), commandPool);

		createSyncObjects();
	}

	private static void mainLoop() {
		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();
			drawFrame();
		}

		vkDeviceWaitIdle(device);
	}

	private static void cleanupSwapChain() {
		if (msaaEnabled)
		colorImage.dispose();
		depthImage.dispose();

		renderers.forEach(IRenderer::cleanupSwapChain);

		swapChainFramebuffers.forEach(framebuffer -> vkDestroyFramebuffer(device, framebuffer, null));

		vkDestroyRenderPass(device, renderPass, null);

		swapChainImageViews.forEach(imageView -> vkDestroyImageView(device, imageView, null));

		vkDestroySwapchainKHR(device, swapChain, null);

		imagesInFlight.clear();
	}

	private static void cleanup() {
		cleanupSwapChain();
		renderers.forEach(IRenderer::dispose);

		inFlightFrames.forEach(frame -> {
			vkDestroySemaphore(device, frame.renderFinishedSemaphore(), null);
			vkDestroySemaphore(device, frame.imageAvailableSemaphore(), null);
			vkDestroyFence(device, frame.fence(), null);
		});
		inFlightFrames.clear();

		for (long transferCommandPool : transferCommandPools) {
			vkDestroyFence(device, transferFences.get(transferCommandPool), null);
			vkDestroyCommandPool(device, transferCommandPool, null);
		}
		transferFences.clear();

		vkDestroyCommandPool(device, commandPool, null);

		vmaDestroyAllocator(vmaAllocator);

		vkDestroyDevice(device, null);

		if (debugMode) {
			vkDestroyDebugUtilsMessengerEXT(instance, debugMessengerHandle, null);
		}

		vkDestroySurfaceKHR(instance, surfaceHandle, null);

		vkDestroyInstance(instance, null);

		glfwDestroyWindow(window);

		glfwTerminate();
	}

	private static void recreateSwapChain() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer width = stack.ints(0);
			IntBuffer height = stack.ints(0);

			while (width.get(0) == 0 && height.get(0) == 0) {
				glfwGetFramebufferSize(window, width, height);
				glfwWaitEvents();
			}
		}
		vkDeviceWaitIdle(device);

		cleanupSwapChain();
		createSwapChainObjects();
	}

	private static void createSwapChainObjects() {
		createSwapChain();
		createImageViews();
		createRenderPass();
		if (msaaEnabled)
		createColorResources();
		createDepthResources();
		createFramebuffers();

		primaryCommandBuffers = createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_PRIMARY, swapChainImages.size(), commandPool);

		renderers.forEach(IRenderer::createSwapChain);
	}

	private static void createSwapChain() {
		try (MemoryStack stack = stackPush()) {
			SwapChainSupportDetails swapChainSupport = querySwapChainSupport(physicalDevice, stack);

			VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.formats);
			int presentMode = chooseSwapPresentMode(swapChainSupport.presentModes);
			VkExtent2D extent = chooseSwapExtent(swapChainSupport.capabilities);

			IntBuffer imageCount = stack.ints(swapChainSupport.capabilities.minImageCount() + 1);

			if (swapChainSupport.capabilities.maxImageCount() > 0 && imageCount.get(0) > swapChainSupport.capabilities.maxImageCount()) {
				imageCount.put(0, swapChainSupport.capabilities.maxImageCount());
			}

			VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
					.surface(surfaceHandle)
					.minImageCount(imageCount.get(0))
					.imageFormat(surfaceFormat.format())
					.imageColorSpace(surfaceFormat.colorSpace())
					.imageExtent(extent)
					.imageArrayLayers(1)
					.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

			if (!queues.graphics.index.equals(queues.present.index)) {
				createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
				createInfo.pQueueFamilyIndices(stack.ints(queues.graphics.index, queues.present.index));
			} else {
				createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			}

			createInfo.preTransform(swapChainSupport.capabilities.currentTransform());
			createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
			createInfo.presentMode(presentMode);
			createInfo.clipped(true);

			createInfo.oldSwapchain(VK_NULL_HANDLE);

			LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);

			if (vkCreateSwapchainKHR(device, createInfo, null, pSwapChain) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create swap chain");
			}

			swapChain = pSwapChain.get(0);

			vkGetSwapchainImagesKHR(device, swapChain, imageCount, null);

			LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));

			vkGetSwapchainImagesKHR(device, swapChain, imageCount, pSwapchainImages);

			swapChainImages = new ArrayList<>(imageCount.get(0));

			for (int i = 0; i < pSwapchainImages.capacity(); i++) {
				swapChainImages.add(pSwapchainImages.get(i));
			}

			swapChainImageFormat = surfaceFormat.format();
			swapChainExtent = VkExtent2D.create().set(extent);
		}
	}

	private static void createImageViews() {
		swapChainImageViews = new ArrayList<>(swapChainImages.size());

		for (long swapChainImage : swapChainImages) {
			swapChainImageViews.add(createImageView(swapChainImage, swapChainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1));
		}
	}

	private static void createRenderPass() {
		try (MemoryStack stack = stackPush()) {
			int capacity = 2;
			if (msaaEnabled) { capacity = 3; }
			VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(capacity, stack);
			VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.callocStack(capacity, stack);

			// Color attachments

			// MSAA Image
			attachments.get(0)
					.format(swapChainImageFormat)
					.samples(msaaSamples)
					.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
					.storeOp(msaaEnabled ? VK_ATTACHMENT_STORE_OP_DONT_CARE : VK_ATTACHMENT_STORE_OP_STORE)
					.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
					.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
					.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
					.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

			if (msaaEnabled) { attachments.get(0).finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL); }

			VkAttachmentReference colorAttachmentRef = attachmentRefs.get(0);
			colorAttachmentRef.attachment(0);
			colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

			// Present Image
			VkAttachmentReference colorAttachmentResolveRef = null;
			if (msaaEnabled) {
				attachments.get(2)
						.format(swapChainImageFormat)
						.samples(VK_SAMPLE_COUNT_1_BIT)
						.loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
						.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
						.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
						.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
						.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
						.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

				colorAttachmentResolveRef = attachmentRefs.get(2);
				colorAttachmentResolveRef.attachment(2);
				colorAttachmentResolveRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			}
			// Depth-Stencil attachments

			attachments.get(1)
					.format(findDepthFormat())
					.samples(msaaSamples)
					.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
					.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
					.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
					.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
					.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
					.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

			VkAttachmentReference depthAttachmentRef = attachmentRefs.get(1)
					.attachment(1)
					.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

			VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack)
					.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
					.colorAttachmentCount(1)
					.pColorAttachments(VkAttachmentReference.callocStack(1, stack).put(0, colorAttachmentRef))
					.pDepthStencilAttachment(depthAttachmentRef);

			if (msaaEnabled) { subpass.pResolveAttachments(VkAttachmentReference.callocStack(1, stack).put(0, colorAttachmentResolveRef)); }

			VkSubpassDependency.Buffer dependency = VkSubpassDependency.callocStack(1, stack)
					.srcSubpass(VK_SUBPASS_EXTERNAL)
					.dstSubpass(0)
					.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.srcAccessMask(0)
					.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

			VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
					.pAttachments(attachments)
					.pSubpasses(subpass)
					.pDependencies(dependency);

			LongBuffer pRenderPass = stack.mallocLong(1);

			if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create render pass");
			}

			renderPass = pRenderPass.get(0);
		}
	}

	private static void createFramebuffers() {
		swapChainFramebuffers = new ArrayList<>(swapChainImageViews.size());

		try (MemoryStack stack = stackPush()) {
			LongBuffer attachments = msaaEnabled ? stack.longs(colorImage.imageView, depthImage.imageView, VK_NULL_HANDLE) : stack.longs(VK_NULL_HANDLE, depthImage.imageView);
			LongBuffer pFramebuffer = stack.mallocLong(1);

			// Lets allocate the create info struct once and just update the pAttachments field each iteration
			VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
					.renderPass(renderPass)
					.width(swapChainExtent.width())
					.height(swapChainExtent.height())
					.layers(1);

			for (long imageView : swapChainImageViews) {
				attachments.put(msaaEnabled ? 2 : 0, imageView);

				framebufferInfo.pAttachments(attachments);

				if (vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create framebuffer");
				}

				swapChainFramebuffers.add(pFramebuffer.get(0));
			}
		}
	}

	public static long createCommandPool(int flags, int queueIndex) {
		try (MemoryStack stack = stackPush()) {
			VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
					.flags(flags)
					.queueFamilyIndex(queueIndex);

			LongBuffer pCommandPool = stack.mallocLong(1);

			if (vkCreateCommandPool(device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create command pool");
			}

			return pCommandPool.get(0);
		}
	}

	private static void createColorResources() {
		colorImage = createImage(swapChainExtent.width(), swapChainExtent.height(), 1, msaaSamples, swapChainImageFormat, VK_IMAGE_TILING_OPTIMAL,
		                         VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, VMA_MEMORY_USAGE_GPU_ONLY);

		colorImage.imageView = createImageView(colorImage.image, swapChainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1);

		transitionImageLayout(colorImage, swapChainImageFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 1);
	}

	private static void createDepthResources() {
		int depthFormat = findDepthFormat();

		depthImage = createImage(swapChainExtent.width(), swapChainExtent.height(), 1, msaaSamples, depthFormat, VK_IMAGE_TILING_OPTIMAL,
		                         VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
		depthImage.imageView = createImageView(depthImage.image, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT, 1);

		transitionImageLayout(depthImage, depthFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, 1);
	}

	private static int findSupportedFormat(IntBuffer formatCandidates, int tiling, int features) {
		try (MemoryStack stack = stackPush()) {
			VkFormatProperties props = VkFormatProperties.callocStack(stack);

			for (int i = 0; i < formatCandidates.capacity(); ++i) {
				int format = formatCandidates.get(i);

				vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);

				if ((tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() & features) == features) ||
				    (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() & features) == features)) {
					return format;
				}
			}
		}

		throw new RuntimeException("Failed to find supported format");
	}

	private static int findDepthFormat() {
		return findSupportedFormat(stackGet().ints(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT), VK_IMAGE_TILING_OPTIMAL, VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT);
	}

	private static boolean hasStencilComponent(int format) {
		return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
	}

	private static double log2(double n) {
		return Math.log(n) / Math.log(2);
	}

	public static VulkanImage createTextureImage(String filename) {
		try (MemoryStack stack = stackPush()) {
//			String filename = "textures/chalet.jpg";
			ByteBuffer buffer = ioResourceToByteBuffer(filename);

			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			IntBuffer pChannels = stack.mallocInt(1);

			ByteBuffer pixels = stbi_load_from_memory(buffer, pWidth, pHeight, pChannels, STBI_rgb_alpha);

//			long imageSize = pWidth.get(0) * pHeight.get(0) * 4; // pChannels.get(0);

			int mipLevels = (int) Math.floor(log2(Math.max(pWidth.get(0), pHeight.get(0)))) + 1;

			if (pixels == null) {throw new RuntimeException("Failed to load texture image " + filename + ", " + stbi_failure_reason());}

			VulkanBuffer stagingBuffer = createBuffer(pixels.remaining(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
			copyMemory(memAddress(pixels), pixels.remaining(), stagingBuffer.allocation);

			stbi_image_free(pixels);
			VulkanImage textureImage = createImage(pWidth.get(0), pHeight.get(0), mipLevels, VK_SAMPLE_COUNT_1_BIT, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_TILING_OPTIMAL,
			                                       VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
			textureImage.width = pWidth.get(0);
			textureImage.height = pHeight.get(0);

			transitionImageLayout(textureImage, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, textureImage.mipLevels);
			copyBufferToImage(stagingBuffer.buffer, textureImage);

			stagingBuffer.dispose();

			generateMipmaps(textureImage.image, VK_FORMAT_R8G8B8A8_SRGB, pWidth.get(0), pHeight.get(0), mipLevels);
			textureImage.imageView = createImageView(textureImage.image, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, mipLevels);

			return textureImage;
		}
	}

	private static void generateMipmaps(long image, int imageFormat, int width, int height, int mipLevels) {
		try (MemoryStack stack = stackPush()) {// Check if image format supports linear blitting
			VkFormatProperties formatProperties = VkFormatProperties.mallocStack(stack);
			vkGetPhysicalDeviceFormatProperties(physicalDevice, imageFormat, formatProperties);

			if ((formatProperties.optimalTilingFeatures() & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0) {
				throw new RuntimeException("Texture image format does not support linear blitting");
			}
			runSingleTimeCommand((commandBuffer) -> {
				VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.callocStack(1, stack);
				barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
				barrier.image(image);
				barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
				barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
				barrier.dstAccessMask(VK_QUEUE_FAMILY_IGNORED);
				barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
				barrier.subresourceRange().baseArrayLayer(0);
				barrier.subresourceRange().layerCount(1);
				barrier.subresourceRange().levelCount(1);

				int mipWidth = width;
				int mipHeight = height;

				for (int i = 1; i < mipLevels; i++) {
					barrier.subresourceRange().baseMipLevel(i - 1);
					barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
					barrier.newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
					barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
					barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);

					vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

					VkImageBlit.Buffer blit = VkImageBlit.callocStack(1, stack);
					blit.srcOffsets(0).set(0, 0, 0);
					blit.srcOffsets(1).set(mipWidth, mipHeight, 1);
					blit.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
					blit.srcSubresource().mipLevel(i - 1);
					blit.srcSubresource().baseArrayLayer(0);
					blit.srcSubresource().layerCount(1);
					blit.dstOffsets(0).set(0, 0, 0);
					blit.dstOffsets(1).set(mipWidth > 1 ? mipWidth / 2 : 1, mipHeight > 1 ? mipHeight / 2 : 1, 1);
					blit.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
					blit.dstSubresource().mipLevel(i);
					blit.dstSubresource().baseArrayLayer(0);
					blit.dstSubresource().layerCount(1);

					vkCmdBlitImage(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);

					barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
					barrier.newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
					barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
					barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

					vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

					if (mipWidth > 1) {mipWidth /= 2;}

					if (mipHeight > 1) {mipHeight /= 2;}
				}

				barrier.subresourceRange().baseMipLevel(mipLevels - 1);
				barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
				barrier.newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
				barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
				barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

				vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
			}, stack);
		}
	}

	public static long createTextureSampler() {
		try (MemoryStack stack = stackPush()) {
			VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
					.magFilter(VK_FILTER_LINEAR)
					.minFilter(VK_FILTER_LINEAR)
					.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.anisotropyEnable(true)
					.maxAnisotropy(16.0f)
					.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
					.unnormalizedCoordinates(false)
					.compareEnable(false)
					.compareOp(VK_COMPARE_OP_ALWAYS)
					.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
					.minLod(0) // Optional
					.maxLod(512)
					.mipLodBias(0); // Optional

			LongBuffer pTextureSampler = stack.mallocLong(1);

			if (vkCreateSampler(device, samplerInfo, null, pTextureSampler) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create texture sampler");
			}

			return pTextureSampler.get(0);
		}
	}

	private static long createImageView(long image, int format, int aspectFlags, int mipLevels) {
		try (MemoryStack stack = stackPush()) {
			VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
					.image(image)
					.viewType(VK_IMAGE_VIEW_TYPE_2D)
					.format(format)
					.subresourceRange(sr -> sr
							.aspectMask(aspectFlags)
							.baseMipLevel(0)
							.levelCount(mipLevels)
							.baseArrayLayer(0)
							.layerCount(1));

			LongBuffer pImageView = stack.mallocLong(1);

			if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create texture image view");
			}

			return pImageView.get(0);
		}
	}

	private static void transitionImageLayout(VulkanImage vulkanImage, int format, int oldLayout, int newLayout, int mipLevels) {
		try (MemoryStack stack = stackPush()) {
			VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.callocStack(1, stack);
			barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
			barrier.oldLayout(oldLayout);
			barrier.newLayout(newLayout);
			barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
			barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
			barrier.image(vulkanImage.image);

			barrier.subresourceRange().baseMipLevel(0);
			barrier.subresourceRange().levelCount(mipLevels);
			barrier.subresourceRange().baseArrayLayer(0);
			barrier.subresourceRange().layerCount(1);

			if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
				barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);

				if (hasStencilComponent(format)) {
					barrier.subresourceRange().aspectMask(barrier.subresourceRange().aspectMask() | VK_IMAGE_ASPECT_STENCIL_BIT);
				}
			} else {barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);}

			int sourceStage;
			int destinationStage;

			if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
				barrier.srcAccessMask(0);
				barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
				destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
			} else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
				barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
				barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

				sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
				destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
			} else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
				barrier.srcAccessMask(0);
				barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
				destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
			} else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
				barrier.srcAccessMask(0);
				barrier.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
				destinationStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
			} else {
				throw new IllegalArgumentException("Unsupported layout transition");
			}

			runSingleTimeCommand((commandBuffer) -> vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0, null, null, barrier), stack);
		}
	}

	public static VulkanBuffer createVertexBufferFromStream(Stream<IVertex> vertices, int bufferSize) {
		final VulkanBuffer vertexBuffer;

		try (final MemoryStack stack = stackPush()) {
			PointerBuffer data = stack.mallocPointer(1);
			if (integratedGPU) {
				vertexBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
				mapVertexStreamToBuffer(vertices, vertexBuffer, data, bufferSize);
			} else {
				final VulkanBuffer stagingBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
				mapVertexStreamToBuffer(vertices, stagingBuffer, data, bufferSize);

				vertexBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
				copyBuffer(stagingBuffer, vertexBuffer, bufferSize);

				stagingBuffer.dispose();
			}
		}

		return vertexBuffer;
	}

	private static void mapVertexStreamToBuffer(Stream<IVertex> vertices, VulkanBuffer vertexBuffer, PointerBuffer data, int bufferSize) {
		vmaMapMemory(vmaAllocator, vertexBuffer.allocation, data);
		final ByteBuffer buffer = data.getByteBuffer(0, bufferSize);
		vertices.forEach(v -> v.get(buffer));
		vmaUnmapMemory(vmaAllocator, vertexBuffer.allocation);
	}

	public static VulkanBuffer createVertexBuffer(List<IVertex> vertices) {
		return createVertexBufferFromStream(vertices.stream(), vertices.get(0).sizeof() * vertices.size());
	}

	private static int findMemoryType(int typeFilter, int properties) {
		VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.mallocStack();
		vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

		for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
			if ((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
				return i;
			}
		}

		throw new RuntimeException("Failed to find suitable memory type");
	}

	public static VkCommandBuffer[] createCommandBuffers(int level, int count, long commandPool) {
		try (MemoryStack stack = stackPush()) {
			VkCommandBuffer[] commandBuffers = new VkCommandBuffer[count];

			PointerBuffer pCommandBuffer = stack.mallocPointer(commandBuffers.length);

			VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
					.commandPool(commandPool)
					.level(level)
					.commandBufferCount(commandBuffers.length);

			VkCheck(vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer), "Failed to allocate command buffers");

			for (int i = 0; i < commandBuffers.length; i++) {
				commandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), device);
			}

			return commandBuffers;
		}
	}

	public static void recordPrimaryCommandBuffers(int imageIndex) {
		try (MemoryStack stack = stackPush()) {
			VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			VkClearValue.Buffer clearValues = VkClearValue.callocStack(2, stack);
			clearValues.get(0).color()
					.float32(0, 100 / 255.0f)
					.float32(1, 149 / 255.0f)
					.float32(2, 237 / 255.0f)
					.float32(3, 1.0f);
			clearValues.get(1).depthStencil()
					.set(1, 0);
			VkRect2D renderArea = VkRect2D.callocStack(stack)
					.offset(offset -> offset.set(0, 0))
					.extent(swapChainExtent);

			VkCommandBuffer commandBuffer = primaryCommandBuffers[imageIndex];

			VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
					.renderPass(renderPass)
					.framebuffer(swapChainFramebuffers.get(imageIndex))
					.renderArea(renderArea)
					.pClearValues(clearValues);

			VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");
			vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS);

			List<VkCommandBuffer> commandBuffers = new ArrayList<>();

			for (IRenderer renderer : renderers) {
				commandBuffers.addAll(Arrays.asList(renderer.getCommandBuffers(imageIndex)));
			}

			vkCmdExecuteCommands(commandBuffer, asPointerBuffer(commandBuffers));

			vkCmdEndRenderPass(commandBuffer);

			VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to record command buffer");
		}
	}

	private static void createSyncObjects() {
		inFlightFrames = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
		imagesInFlight = new HashMap<>(swapChainImages.size());

		try (MemoryStack stack = stackPush()) {
			VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

			VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
					.flags(VK_FENCE_CREATE_SIGNALED_BIT);

			LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
			LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
			LongBuffer pFence = stack.mallocLong(1);

			for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
				if (vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS || vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
				}

				inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
			}
		}
	}

	private static void drawFrame() {
		try (MemoryStack stack = stackPush()) {
			Frame thisFrame = inFlightFrames.get(currentFrame);

			vkWaitForFences(device, thisFrame.pFence(), true, UINT64_MAX);

			IntBuffer pImageIndex = stack.mallocInt(1);

			int vkResult = vkAcquireNextImageKHR(device, swapChain, UINT64_MAX, thisFrame.imageAvailableSemaphore(), VK_NULL_HANDLE, pImageIndex);

			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
				recreateSwapChain();
				return;
			} else if (vkResult != VK_SUCCESS && vkResult != VK_SUBOPTIMAL_KHR) {
				throw new RuntimeException("Cannot get image : " + VKReturnCode.getByCode(vkResult));
			}

			final int imageIndex = pImageIndex.get(0);

			if (imagesInFlight.containsKey(imageIndex)) {
				vkWaitForFences(device, imagesInFlight.get(imageIndex).fence(), true, UINT64_MAX);
			} else {
				vkQueueWaitIdle(queues.present.queue);
			}

			for (IRenderer renderer : renderers) {
				renderer.beforeFrame(imageIndex);
				if (renderer.commandBuffersChanged()) {
					needUpdateCount = swapChainImages.size();
				}
			}
			if (needUpdateCount-- > 0) {
				vkResetCommandPool(device, commandPool, 0);
				recordPrimaryCommandBuffers(imageIndex);
			}

			imagesInFlight.put(imageIndex, thisFrame);

			VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
					.waitSemaphoreCount(1)
					.pWaitSemaphores(thisFrame.pImageAvailableSemaphore())
					.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
					.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore())
					.pCommandBuffers(stack.pointers(primaryCommandBuffers[imageIndex]));

			vkResetFences(device, thisFrame.pFence());

			if ((vkResult = vkQueueSubmit(queues.graphics.queue, submitInfo, thisFrame.fence())) != VK_SUCCESS) {
				vkResetFences(device, thisFrame.pFence());
				throw new RuntimeException("Failed to submit draw command buffer: " + VKReturnCode.getByCode(vkResult));
			}

			VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
					.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore())
					.swapchainCount(1)
					.pSwapchains(stack.longs(swapChain))
					.pImageIndices(pImageIndex);

			vkResult = vkQueuePresentKHR(queues.present.queue, presentInfo);

			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || framebufferResize) {
				framebufferResize = false;
				recreateSwapChain();
			} else if (vkResult != VK_SUCCESS) {
				throw new RuntimeException("Failed to present swap chain image");
			}

			currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;

			for (IRenderer renderer : renderers) {
				renderer.afterFrame(imageIndex);
			}
		}
	}

	public static long createShaderModule(ByteBuffer spirvCode) {
		try (MemoryStack stack = stackPush()) {
			VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.callocStack(stack)
					.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
					.pCode(spirvCode);

			LongBuffer pShaderModule = stack.mallocLong(1);

			if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create shader module");
			}

			return pShaderModule.get(0);
		}
	}

	private static int chooseSwapPresentMode(IntBuffer availablePresentModes) {
//		for (int i = 0; i < availablePresentModes.capacity(); i++) {
//			if (availablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
//				return availablePresentModes.get(i);
//			}
//		}

		return VK_PRESENT_MODE_FIFO_KHR;
	}

	private static VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {
		if (capabilities.currentExtent().width() != UINT32_MAX) {
			return capabilities.currentExtent();
		}

		IntBuffer width = stackGet().ints(0);
		IntBuffer height = stackGet().ints(0);

		glfwGetFramebufferSize(window, width, height);

		VkExtent2D actualExtent = VkExtent2D.mallocStack().set(width.get(0), height.get(0));

		VkExtent2D minExtent = capabilities.minImageExtent();
		VkExtent2D maxExtent = capabilities.maxImageExtent();

		actualExtent.width(clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
		actualExtent.height(clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));

		return actualExtent;
	}

	private static int clamp(int min, int max, int value) {
		return Math.max(min, Math.min(max, value));
	}

	public static class QueueFamilies {
		public VulkanQueue graphics;
		public VulkanQueue present;
		public VulkanQueue transfer;

		public boolean hasGraphics() {
			return graphics != null;
		}

		public boolean hasPresent() {
			return present != null;
		}

		public boolean hasTransfer() {
			return transfer != null;
		}

		public int getGraphicsIndex() {
			return hasGraphics() ? graphics.index : -1;
		}

		public int getPresentIndex() {
			return hasPresent() ? present.index : -1;
		}

		public int getTransferIndex() {
			return hasTransfer() ? transfer.index : -1;
		}

		private boolean isComplete() {
			return hasGraphics() && hasPresent() && hasTransfer();
		}

		public int[] unique() {
			return IntStream.of(graphics.index, present.index, transfer.index).distinct().toArray();
		}

		public int[] array() {
			return new int[] { graphics.index, present.index, transfer.index };
		}
	}

	public static class VulkanQueue {
		public final Integer index;
		public VkQueue queue;

		public VulkanQueue(Integer index) {
			this.index = index;
		}
	}

	public static class SwapChainSupportDetails {
		public VkSurfaceCapabilitiesKHR capabilities;
		public VkSurfaceFormatKHR.Buffer formats;
		public IntBuffer presentModes;
	}

	public static class UniformBufferObject {
		public static final int SIZEOF = 3 * 16 * Float.BYTES;
		public final Matrix4f model;
		public final Matrix4f view;
		public final Matrix4f proj;

		public UniformBufferObject() {
			model = new Matrix4f();
			view = new Matrix4f();
			proj = new Matrix4f();
		}
	}
}
