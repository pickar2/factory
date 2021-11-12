package xyz.sathro.vulkan;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.factory.util.ResourceManager;
import xyz.sathro.vulkan.models.*;
import xyz.sathro.vulkan.renderer.IRenderer;
import xyz.sathro.vulkan.renderer.MainRenderer;
import xyz.sathro.vulkan.utils.*;
import xyz.sathro.vulkan.vertex.IVertex;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
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
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK11.vkEnumerateInstanceVersion;
import static xyz.sathro.factory.Engine.debugMode;
import static xyz.sathro.factory.util.Maths.clamp;
import static xyz.sathro.factory.window.Window.handle;
import static xyz.sathro.vulkan.utils.VulkanUtils.*;

public class Vulkan {
	private static final Logger logger = LogManager.getLogger(Vulkan.class);

	public static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;
	public static final int UINT32_MAX = 0xFFFFFFFF;
	private static final VkFenceCreateInfo defaultSignaledFenceInfo = VkFenceCreateInfo.calloc()
			.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
			.flags(VK_FENCE_CREATE_SIGNALED_BIT);

	private static final VkFenceCreateInfo defaultFenceInfo = VkFenceCreateInfo.calloc()
			.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

	public static final Set<String> INSTANCE_EXTENSIONS = new HashSet<>();
	public static final Set<String> DEVICE_EXTENSIONS = Stream.of(
			VK_KHR_SWAPCHAIN_EXTENSION_NAME
	).collect(toSet());
	public static final Set<String> VALIDATION_LAYERS = Stream.of(
			"VK_LAYER_KHRONOS_validation",
			"VK_LAYER_LUNARG_monitor"
	).collect(toSet());
	public static final VkInstance instance;
	public static final VkDevice device;
	public static final long vmaAllocator;
	public static final long surfaceHandle;
	public static final VkPhysicalDevice physicalDevice;
	public static final VkDebugUtilsMessengerCreateInfoEXT debugMessenger;
	public static final VkDebugUtilsMessengerCallbackEXT debugCallback;
	public static final long debugMessengerHandle;
	public static final long commandPool;
	public static final boolean integratedGPU;
	private static final IntSet suppressedDebugMessageIDs;

	public static final boolean msaaEnabled = false; // TODO: proper support for turning msaa on and off
	public static int msaaSamples = VK_SAMPLE_COUNT_1_BIT;

	public static QueueFamilies queues;
	public static VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc();
	public static int vulkanApiVersion;

	public static LongList swapChainImages;
	public static VkExtent2D swapChainExtent;
	public static VulkanImage colorImage;
	public static VulkanImage depthImage;
	public static long oldSwapChainHandle = VK_NULL_HANDLE;
	public static long swapChainHandle;
	public static int swapChainImageFormat;
	public static LongList swapChainImageViews;
	public static LongList swapChainFramebuffers;
	public static long renderPass;

	public static int swapChainImageCount;

	static {
		if (debugMode) {
			suppressedDebugMessageIDs = new IntOpenHashSet();
			suppressedDebugMessageIDs.add(0); // Invalid layer manifest file version 1.2.0. MAY SUPPRESS OTHER VALID MESSAGES
			suppressedDebugMessageIDs.add(0x2dc65ef4); // informs that debug extensions should not be used in prod

			debugCallback = new VkDebugUtilsMessengerCallbackEXT() {
				@Override
				public int invoke(int messageSeverity, int messageTypes, long pCallbackData, long pUserData) {
					final VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

					if (!suppressedDebugMessageIDs.contains(data.messageIdNumber())) {
						logger.warn(("[VULKAN] " + data.pMessageString()));
					}

					return 0;
				}
			};
			debugMessenger = getDebugMessenger();
		} else {
			suppressedDebugMessageIDs = null;
			debugCallback = null;
			debugMessenger = null;
		}

		vulkanApiVersion = getMaxVulkanApiVersion();
		if (vulkanApiVersion == VK_API_VERSION_1_0 || vulkanApiVersion == VK_API_VERSION_1_1) {
//			INSTANCE_EXTENSIONS.add("VK_KHR_get_physical_device_properties2");
//			INSTANCE_EXTENSIONS.add("VK_KHR_external_memory_capabilities");
			INSTANCE_EXTENSIONS.add("VK_NV_external_memory_capabilities");
		}
		instance = createVkInstance();

		if (debugMode) {
			debugMessengerHandle = setupDebugging();
		} else {
			debugMessengerHandle = VK_NULL_HANDLE;
		}
		surfaceHandle = createSurface();

		physicalDevice = pickPhysicalDevice();

		integratedGPU = properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;

//		queues = findQueueFamilies(physicalDevice);
		device = createLogicalDevice();

		createDeviceQueues();

		vmaAllocator = createAllocator();

		commandPool = createCommandPool(0, queues.graphics);
	}

	private Vulkan() { }

	public static int getMaxVulkanApiVersion() {
		try (MemoryStack stack = stackPush()) {
			if (vkGetInstanceProcAddr(null, stack.UTF8("vkEnumerateInstanceVersion")) == VK_NULL_HANDLE) {
				return VK_API_VERSION_1_0;
			}
			final IntBuffer intBuffer = stack.mallocInt(1);
			vkEnumerateInstanceVersion(intBuffer);

			return VK_MAKE_VERSION(1, VK_VERSION_MINOR(intBuffer.get(0)), 0);
		}
	}

	public static long createDefaultFence(boolean signaled) {
		try (MemoryStack stack = stackPush()) {
			final LongBuffer longBuffer = stack.mallocLong(1);
			VkCheck(vkCreateFence(device, signaled ? defaultSignaledFenceInfo : defaultFenceInfo, null, longBuffer), "Failed to create fence");

			return longBuffer.get(0);
		}
	}

	private static Set<String> getAvailableExtensions(ByteBuffer layer) {
		final Set<String> set = new ObjectOpenHashSet<>();
		try (MemoryStack stack = stackPush()) {
			final IntBuffer intBuf = stack.mallocInt(1);
			vkEnumerateInstanceExtensionProperties(layer, intBuf, null);

			final VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.calloc(intBuf.get(0), stack);
			vkEnumerateInstanceExtensionProperties(layer, intBuf, availableExtensions);

			for (VkExtensionProperties ext : availableExtensions) {
				set.add(ext.extensionNameString());
			}
		}

		return set;
	}

	private static void createDeviceQueues() {
		try (MemoryStack stack = stackPush()) {
			final PointerBuffer pointerBuf = stack.mallocPointer(1);

			final Int2ObjectMap<VkQueue> map = new Int2ObjectOpenHashMap<>();

			vkGetDeviceQueue(device, queues.graphics.index, 0, pointerBuf);
			map.put(queues.graphics.index, new VkQueue(pointerBuf.get(0), device));
			vkGetDeviceQueue(device, queues.present.index, 0, pointerBuf);
			map.put(queues.present.index, new VkQueue(pointerBuf.get(0), device));
			vkGetDeviceQueue(device, queues.compute.index, 0, pointerBuf);
			map.put(queues.compute.index, new VkQueue(pointerBuf.get(0), device));
			vkGetDeviceQueue(device, queues.transfer.index, 0, pointerBuf);
			map.put(queues.transfer.index, new VkQueue(pointerBuf.get(0), device));

			queues.graphics.queue = map.get(queues.graphics.index);
			queues.present.queue = map.get(queues.present.index);
			queues.compute.queue = map.get(queues.compute.index);
			queues.transfer.queue = map.get(queues.transfer.index);
		}
	}

	private static long createSurface() {
		try (MemoryStack stack = stackPush()) {
			final LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);

			if (glfwCreateWindowSurface(instance, handle, null, pSurface) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create window surface");
			}

			return pSurface.get(0);
		}
	}

	private static boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer extensionCount = stack.ints(0);
			vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

			final VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
			vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);

			return availableExtensions.stream().map(VkExtensionProperties::extensionNameString).collect(toSet()).containsAll(DEVICE_EXTENSIONS);
		}
	}

	private static QueueFamilies findQueueFamilies(VkPhysicalDevice physicalDevice) {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer intBuf = stack.mallocInt(1);

			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, intBuf, null);

			final VkQueueFamilyProperties.Buffer queueProps = VkQueueFamilyProperties.malloc(intBuf.get(0), stack);

			vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, intBuf, queueProps);

			final List<VulkanQueue> queues = new ObjectArrayList<>();

			for (int i = 0; i < queueProps.capacity(); i++) {
				final VulkanQueue queue = new VulkanQueue(i);
				final int flags = queueProps.get(i).queueFlags();

				if ((flags & VK_QUEUE_GRAPHICS_BIT) != 0) {
					queue.flags.add(VulkanQueue.Flag.GRAPHICS);
				}
				if ((flags & VK_QUEUE_TRANSFER_BIT) != 0) {
					queue.flags.add(VulkanQueue.Flag.TRANSFER);
				}
				if ((flags & VK_QUEUE_COMPUTE_BIT) != 0) {
					queue.flags.add(VulkanQueue.Flag.COMPUTE);
				}
				vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surfaceHandle, intBuf);
				if (intBuf.get(0) == VK_TRUE) {
					queue.flags.add(VulkanQueue.Flag.PRESENT);
				}

				queues.add(queue);
			}

			final VulkanQueue queue = queues.get(0);

			VulkanQueue graphics = queues.stream().filter(VulkanQueue::hasGraphicsFlag).findFirst().orElse(queue);
			VulkanQueue present = queues.stream().filter(VulkanQueue::hasPresentFlag).findFirst().orElse(queue);
			VulkanQueue compute = queues.stream()
					.filter(VulkanQueue::hasComputeFlag)
					.findFirst().orElse(queue);
			VulkanQueue transfer = queues.stream()
					.filter(VulkanQueue::hasTransfersFlag)
					.findFirst().orElse(queue);

			compute = queues.stream()
					.filter(VulkanQueue::hasComputeFlag)
					.filter(vq -> vq.index != present.index)
					.filter(vq -> vq.index != graphics.index)
					.findFirst().orElse(compute);

			final int computeIndex = compute.index;

			transfer = queues.stream()
					.filter(VulkanQueue::hasTransfersFlag)
					.filter(vq -> vq.index != present.index)
					.filter(vq -> vq.index != graphics.index)
					.filter(vq -> vq.index != computeIndex)
					.findFirst().orElse(transfer);

			return new QueueFamilies(graphics, present, compute, transfer);
		}
	}

	public static SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device, MemoryStack stack) {
		final SwapChainSupportDetails details = new SwapChainSupportDetails();

		details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
		vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surfaceHandle, details.capabilities);

		final IntBuffer count = stack.ints(0);

		vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceHandle, count, null);

		if (count.get(0) != 0) {
			details.formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
			vkGetPhysicalDeviceSurfaceFormatsKHR(device, surfaceHandle, count, details.formats);
		}

		vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceHandle, count, null);

		if (count.get(0) != 0) {
			details.presentModes = stack.mallocInt(count.get(0));
			vkGetPhysicalDeviceSurfacePresentModesKHR(device, surfaceHandle, count, details.presentModes);
		}

		return details;
	}

	// TODO: extract families check
	private static boolean isDeviceSuitable(VkPhysicalDevice device) {
		final boolean extensionsSupported = checkDeviceExtensionSupport(device);
		boolean swapChainAdequate = false;
		boolean anisotropySupported = false;

		if (extensionsSupported) {
			try (MemoryStack stack = stackPush()) {
				final SwapChainSupportDetails swapChainSupport = querySwapChainSupport(device, stack);
				swapChainAdequate = swapChainSupport.formats.hasRemaining() && swapChainSupport.presentModes.hasRemaining();

				final VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.malloc(stack);
				vkGetPhysicalDeviceFeatures(device, supportedFeatures);

				anisotropySupported = supportedFeatures.samplerAnisotropy();
			}
		}

		return extensionsSupported && swapChainAdequate && anisotropySupported;
	}

	private static VkPhysicalDevice pickPhysicalDevice() {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer deviceCount = stack.ints(0);

			vkEnumeratePhysicalDevices(instance, deviceCount, null);

			if (deviceCount.get(0) == 0) {
				throw new RuntimeException("Failed to find GPUs with Vulkan support");
			}

			final PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));

			vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

			VkPhysicalDevice device = null;
			QueueFamilies families = null;
			for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
				device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

				if (!isDeviceSuitable(device)) { continue; }
				families = findQueueFamilies(device);
				if (families.isComplete()) { break; }

				device = null;
			}

			if (device == null) {
				throw new RuntimeException("Failed to find a suitable GPU");
			}

			queues = families;

			vkGetPhysicalDeviceProperties(device, properties);

			System.out.println(properties.deviceNameString());

			Vulkan.msaaSamples = msaaEnabled ? getMaxUsableSampleCount() : VK_SAMPLE_COUNT_1_BIT;

			return device;
		}
	}

	private static int getMaxUsableSampleCount() {
		final int sampleCountFlags = properties.limits().framebufferColorSampleCounts() & properties.limits().framebufferDepthSampleCounts();

		if ((sampleCountFlags & VK_SAMPLE_COUNT_64_BIT) != 0) { return VK_SAMPLE_COUNT_64_BIT; }
		if ((sampleCountFlags & VK_SAMPLE_COUNT_32_BIT) != 0) { return VK_SAMPLE_COUNT_32_BIT; }
		if ((sampleCountFlags & VK_SAMPLE_COUNT_16_BIT) != 0) { return VK_SAMPLE_COUNT_16_BIT; }
		if ((sampleCountFlags & VK_SAMPLE_COUNT_8_BIT) != 0) { return VK_SAMPLE_COUNT_8_BIT; }
		if ((sampleCountFlags & VK_SAMPLE_COUNT_4_BIT) != 0) { return VK_SAMPLE_COUNT_4_BIT; }
		if ((sampleCountFlags & VK_SAMPLE_COUNT_2_BIT) != 0) { return VK_SAMPLE_COUNT_2_BIT; }

		return VK_SAMPLE_COUNT_1_BIT;
	}

	private static VkDevice createLogicalDevice() {
		try (MemoryStack stack = stackPush()) {
			final int[] uniqueQueueFamilies = queues.unique();

			final VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);

			for (int i = 0; i < uniqueQueueFamilies.length; i++) {
				queueCreateInfos.get(i)
						.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
						.queueFamilyIndex(uniqueQueueFamilies[i])
						.pQueuePriorities(stack.floats(1.0f));
			}

			final VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
					.samplerAnisotropy(true)
					.fragmentStoresAndAtomics(true)
					.vertexPipelineStoresAndAtomics(true)
					.sampleRateShading(msaaEnabled)
					.fillModeNonSolid(true);

			final VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
					.pQueueCreateInfos(queueCreateInfos)
					.pEnabledFeatures(deviceFeatures)
					.ppEnabledExtensionNames(BufferUtils.asPointerBuffer(DEVICE_EXTENSIONS));

			if (debugMode) {
				createInfo.ppEnabledLayerNames(BufferUtils.asPointerBuffer(VALIDATION_LAYERS));
			}

			final PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);

			if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create logical device");
			}

			return new VkDevice(pDevice.get(0), physicalDevice, createInfo);
		}
	}

	private static VkDebugUtilsMessengerCreateInfoEXT getDebugMessenger() {
		final int messageTypes = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
		final int messageSeverities = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;

		return VkDebugUtilsMessengerCreateInfoEXT.calloc()
				.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
				.messageSeverity(messageSeverities)
				.messageType(messageTypes)
				.pfnUserCallback(debugCallback);
	}

	private static long setupDebugging() {
		try (MemoryStack stack = stackPush()) {
			final LongBuffer longBuf = stack.mallocLong(1);
			VkCheck(vkCreateDebugUtilsMessengerEXT(instance, debugMessenger, null, longBuf), "Failed to create DebugUtilsMessenger");

			return longBuf.get(0);
		}
	}

	private static boolean checkValidationLayerSupport() {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer layerCount = stack.ints(0);
			vkEnumerateInstanceLayerProperties(layerCount, null);

			final VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount.get(0), stack);
			vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

			final Set<String> availableLayerNames = availableLayers.stream().map(VkLayerProperties::layerNameString).collect(toSet());

			return availableLayerNames.containsAll(VALIDATION_LAYERS);
		}
	}

	private static long createAllocator() {
		try (MemoryStack stack = stackPush()) {
			final VmaVulkanFunctions functions = VmaVulkanFunctions.calloc(stack).set(instance, device);

			final VmaAllocatorCreateInfo allocatorInfo = VmaAllocatorCreateInfo.calloc(stack)
					.device(device)
					.physicalDevice(physicalDevice)
					.pVulkanFunctions(functions)
					.instance(instance);

			if (device.getCapabilities().VK_KHR_dedicated_allocation) {
				allocatorInfo.flags(VMA_ALLOCATOR_CREATE_KHR_DEDICATED_ALLOCATION_BIT);
			}

			final PointerBuffer pointerBuffer = stack.mallocPointer(1);
			VkCheck(vmaCreateAllocator(allocatorInfo, pointerBuffer), "Unable to create VMA allocator");

			return pointerBuffer.get(0);
		}
	}

	private static VkInstance createVkInstance() {
		try (MemoryStack stack = stackPush()) {
			if (debugMode && !checkValidationLayerSupport()) {
				throw new VulkanException("Validation requested but not supported");
			}

			final PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
			if (requiredExtensions == null) {
				throw new VulkanException("Failed to find list of required Vulkan extensions");
			}

			final VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
					.pApplicationName(stack.UTF8("factory"))
					.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
					.pEngineName(stack.UTF8("factory engine"))
					.engineVersion(VK_MAKE_VERSION(1, 0, 0))
					.apiVersion(vulkanApiVersion);

			final Set<String> availableExtensions = getAvailableExtensions(null);
			final Set<String> additionalExtensions = new HashSet<>(INSTANCE_EXTENSIONS);

			final VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
					.pApplicationInfo(appInfo);

			if (debugMode) {
				final int[] enabledFeaturesFlags = {
						VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT,
						VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT
				};

				final VkValidationFeaturesEXT validationFeatures = VkValidationFeaturesEXT.calloc(stack)
						.sType(VK_STRUCTURE_TYPE_VALIDATION_FEATURES_EXT)
						.pNext(debugMessenger.address())
						.pEnabledValidationFeatures(stack.ints(enabledFeaturesFlags));

				for (String validationLayer : VALIDATION_LAYERS) {
					availableExtensions.addAll(getAvailableExtensions(stack.UTF8(validationLayer)));
				}

				createInfo.ppEnabledLayerNames(BufferUtils.asPointerBuffer(VALIDATION_LAYERS));
				additionalExtensions.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
				additionalExtensions.add(VK_EXT_VALIDATION_FEATURES_EXTENSION_NAME);

				createInfo.pNext(validationFeatures.address());
			}
			// TODO: crash on missing extension?
			additionalExtensions.removeIf(ext -> !availableExtensions.contains(ext));

			final PointerBuffer ppExtensions = stack.mallocPointer(requiredExtensions.capacity() + additionalExtensions.size());
			ppExtensions.put(requiredExtensions);
			for (String additionalExtension : additionalExtensions) {
				ppExtensions.put(stack.UTF8(additionalExtension));
			}
			ppExtensions.flip();

			createInfo.ppEnabledExtensionNames(ppExtensions);

			final PointerBuffer instancePtr = stack.mallocPointer(1);

			VkCheck(vkCreateInstance(createInfo, null, instancePtr), "Failed to create instance");

			return new VkInstance(instancePtr.get(0), createInfo);
		}
	}

	public static VulkanImage createImage(int width, int height, int mipLevels, int numSamples, int format, int tiling, int usage, int memoryUsage) {
		final VulkanImage image = new VulkanImage();
		try (MemoryStack stack = stackPush()) {
			final VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
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

			final LongBuffer longBuffer = stack.mallocLong(1);
			final PointerBuffer pointerBuffer = stack.mallocPointer(1);

			VkCheck(vmaCreateImage(vmaAllocator, imageInfo, VmaAllocationCreateInfo.calloc(stack).usage(memoryUsage), longBuffer, pointerBuffer, null), "Failed to create image");
			image.image = longBuffer.get(0);
			image.allocation = pointerBuffer.get(0);
			image.mipLevels = mipLevels;
		}

		return image;
	}

	public static VulkanBuffer createIndexBuffer(int[] indices) {
		return putDataIntoGPUOnlyBuffer(buffer -> buffer.asIntBuffer().put(indices), Integer.BYTES * indices.length, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
	}

	public static VulkanBuffer createBuffer(int size, int usage, int memoryUsage) {
		final VulkanBuffer buffer = new VulkanBuffer();
		try (MemoryStack stack = stackPush()) {
			final VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
					.size(size)
					.usage(usage)
					.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			final LongBuffer longBuffer = stack.mallocLong(1);
			final PointerBuffer pointerBuffer = stack.mallocPointer(1);

			VkCheck(vmaCreateBuffer(vmaAllocator, bufferInfo, VmaAllocationCreateInfo.calloc(stack).usage(memoryUsage), longBuffer, pointerBuffer, null),
			        "Failed to allocate buffer");

			buffer.buffer = longBuffer.get(0);
			buffer.allocation = pointerBuffer.get(0);
		}

		return buffer;
	}

	private static void copyBufferToImage(long buffer, VulkanImage vulkanImage) {
		try (MemoryStack stack = stackPush();
		     DefaultCommandPool commandPool = DefaultCommandPools.takeTransferPool()) {
			final VkCommandBuffer commandBuffer = CommandBuffers.beginSingleTimeCommands(commandPool.handle, stack);

			final VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
					.bufferOffset(0)
					.bufferRowLength(0)
					.bufferImageHeight(0);

			region.imageSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
			region.imageOffset().set(0, 0, 0);
			region.imageExtent(VkExtent3D.calloc(stack).set(vulkanImage.width, vulkanImage.height, 1));

			vkCmdCopyBufferToImage(commandBuffer, buffer, vulkanImage.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

			CommandBuffers.endSingleTimeCommands(commandBuffer, commandPool.handle, commandPool.fence, queues.transfer, stack);
		}
	}

	private static void copyBuffer(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer, long size) {
		try (MemoryStack stack = stackPush();
		     DefaultCommandPool commandPool = DefaultCommandPools.takeTransferPool()) {
			final VkCommandBuffer commandBuffer = CommandBuffers.beginSingleTimeCommands(commandPool.handle, stack);
			final VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack).size(size);

			vkCmdCopyBuffer(commandBuffer, srcBuffer.buffer, dstBuffer.buffer, copyRegion);

			CommandBuffers.endSingleTimeCommands(commandBuffer, commandPool.handle, commandPool.fence, queues.transfer, stack);
		}
	}

	public static long createShaderModule(String path, int shaderType) {
		try (MemoryStack stack = stackPush()) {
			final ByteBuffer code = compileShader(path, shaderType);
			final LongBuffer longBuffer = stack.mallocLong(1);

			final VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
					.pCode(code);
			VkCheck(vkCreateShaderModule(device, createInfo, null, longBuffer), "Failed to create shader module " + path);

			return longBuffer.get(0);
		}
	}

	public static VkPipelineShaderStageCreateInfo.Buffer createShaderStages(long[] vertexShaderModules, long[] fragmentShaderModules) {
		final ByteBuffer name = stackUTF8("main"); // TODO: reuse "main" utf8 buffer

		final VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(vertexShaderModules.length + fragmentShaderModules.length, stackGet());
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
		return VkPipelineVertexInputStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
				.pVertexAttributeDescriptions(vertex.getVertexInfo().getAttributeDescriptions())
				.pVertexBindingDescriptions(vertex.getVertexInfo().getBindingDescription());
	}

	public static VkPipelineInputAssemblyStateCreateInfo createInputAssembly(int topology) {
		return VkPipelineInputAssemblyStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
				.topology(topology)
				.primitiveRestartEnable(false);
	}

	public static VkPipelineViewportStateCreateInfo createViewportState() {
		final VkViewport.Buffer viewport = VkViewport.calloc(1, stackGet())
				.x(0)
				.y(0)
				.width(swapChainExtent.width())
				.height(swapChainExtent.height())
				.minDepth(0)
				.maxDepth(1);

		final VkOffset2D offset = VkOffset2D.calloc(stackGet()).set(0, 0);
		final VkRect2D.Buffer scissor = VkRect2D.calloc(1, stackGet())
				.offset(offset)
				.extent(swapChainExtent);

		return VkPipelineViewportStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
				.viewportCount(viewport.capacity())
				.pViewports(viewport)
				.scissorCount(scissor.capacity())
				.pScissors(scissor);
	}

	public static VkPipelineRasterizationStateCreateInfo createRasterizer(int polygonMode, float lineWidth, int cullMode) {
		return VkPipelineRasterizationStateCreateInfo.calloc(stackGet())
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
		return VkPipelineMultisampleStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
				.sampleShadingEnable(msaaEnabled)
				.minSampleShading(msaaEnabled ? 0.2f : 0)
				.rasterizationSamples(msaaSamples);
	}

	public static VkPipelineColorBlendStateCreateInfo createColorBlending(int colorWriteMask) {
		final VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stackGet())
				.blendEnable(true)
				.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
				.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.colorBlendOp(VK_BLEND_OP_ADD)
				.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
				.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
				.alphaBlendOp(VK_BLEND_OP_ADD)
				.colorWriteMask(colorWriteMask);

		return VkPipelineColorBlendStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
				.logicOpEnable(false)
				.pAttachments(colorBlendAttachment)
				.logicOp(VK_LOGIC_OP_COPY)
				.blendConstants(stackCallocFloat(4));
	}

	public static VkPipelineColorBlendStateCreateInfo createColorBlending(int colorWriteMask, int srcAlpha, int dstAlpha) {
		final VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stackGet())
				.blendEnable(true)
				.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
				.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
				.colorBlendOp(VK_BLEND_OP_ADD)
				.srcAlphaBlendFactor(srcAlpha)
				.dstAlphaBlendFactor(dstAlpha)
				.alphaBlendOp(VK_BLEND_OP_ADD)
				.colorWriteMask(colorWriteMask);

		return VkPipelineColorBlendStateCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
				.logicOpEnable(false)
				.pAttachments(colorBlendAttachment)
				.logicOp(VK_LOGIC_OP_COPY)
				.blendConstants(stackCallocFloat(4));
	}

	public static VkPipelineLayoutCreateInfo createPipelineLayout(long layout, VkPushConstantRange.Buffer pushConstantRanges) {
		return VkPipelineLayoutCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(stackLongs(layout))
				.pPushConstantRanges(pushConstantRanges);
	}

	public static VkPipelineLayoutCreateInfo createPipelineLayout(long[] layouts, VkPushConstantRange.Buffer pushConstantRanges) {
		final LongBuffer buffer = stackMallocLong(layouts.length);
		buffer.put(layouts);
		buffer.flip();

		return VkPipelineLayoutCreateInfo.calloc(stackGet())
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(buffer)
				.pPushConstantRanges(pushConstantRanges);
	}

	public static VkPipelineDepthStencilStateCreateInfo createDepthStencil(int depthCompareOp, boolean enableDepthWrite) {
		return VkPipelineDepthStencilStateCreateInfo.calloc(stackGet())
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
		final VulkanPipeline pipeline = new VulkanPipeline(vertexType);
		try (MemoryStack stack = stackPush()) {
			final LongBuffer longBuffer = stack.mallocLong(1);
			VkCheck(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, longBuffer), "Failed to create pipeline layout");
			pipeline.layout = longBuffer.get(0);

			final VkGraphicsPipelineCreateInfo.Buffer pipelineInfos = VkGraphicsPipelineCreateInfo.calloc(1, stack)
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
			pipeline.handle = longBuffer.get(0);
		}
		return pipeline;
	}

	public static void initVulkan(List<IRenderer> renderers) {
		createSwapChainObjects();

		MainRenderer.init(renderers);

		logger.info("VULKAN READY");
	}

	public static void cleanup() {
		cleanupSwapChain();
		if (swapChainHandle != VK_NULL_HANDLE) {
			vkDestroySwapchainKHR(device, swapChainHandle, null);
		}
		MainRenderer.cleanup();

		dispose();
		properties.free();
		DefaultCommandPools.dispose();
		defaultSignaledFenceInfo.free();
		defaultFenceInfo.free();

		DisposalQueue.lateDispose();

		vkDestroyCommandPool(device, commandPool, null);

		AIFileLoader.dispose();
		ResourceManager.dispose();

		vmaDestroyAllocator(vmaAllocator);
		vkDestroyDevice(device, null);

		if (debugMode) {
			vkDestroyDebugUtilsMessengerEXT(instance, debugMessengerHandle, null);
			debugMessenger.free();
			debugCallback.free();
		}

		vkDestroySurfaceKHR(instance, surfaceHandle, null);
		vkDestroyInstance(instance, null);

		glfwDestroyWindow(handle);

		glfwTerminate();
	}

	public static long createCommandPool(int flags, VulkanQueue queue) {
		try (MemoryStack stack = stackPush()) {
			final VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
					.flags(flags)
					.queueFamilyIndex(queue.index);

			final LongBuffer pCommandPool = stack.mallocLong(1);

			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (queue) {
				if (vkCreateCommandPool(device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
					throw new RuntimeException("Failed to create command pool");
				}
			}

			return pCommandPool.get(0);
		}
	}

	private static boolean hasStencilComponent(int format) {
		return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
	}

	public static VulkanImage createTextureImage(String filename) {
		final ByteBuffer buffer = ResourceManager.stringToByteBuffer(filename);
		// TODO: return default error texture when ByteBuffer is null
		assert buffer != null;
		final VulkanImage image = createTextureFromBytes(buffer);
		memFree(buffer);

		return image;
	}

	public static VulkanImage createTextureFromBytes(@NotNull ByteBuffer buffer) {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer pWidth = stack.mallocInt(1);
			final IntBuffer pHeight = stack.mallocInt(1);
			final IntBuffer pChannels = stack.mallocInt(1);

			final ByteBuffer pixels = stbi_load_from_memory(buffer, pWidth, pHeight, pChannels, STBI_rgb_alpha);

			return createTextureFromSTBIBuffer(pixels, pWidth.get(0), pHeight.get(0), pChannels.get(0));
		}
	}

	public static VulkanImage createTextureFromSTBIBuffer(ByteBuffer pixels, int width, int height, int channels) {
		try (MemoryStack stack = stackPush()) {
			if (pixels == null) {
				throw new RuntimeException("Failed to load texture image from buffer, " + stbi_failure_reason());
			}

			final VulkanBuffer stagingBuffer = createBuffer(pixels.remaining(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);

			final PointerBuffer memoryPointer = stack.mallocPointer(1);
			VkCheck(vmaMapMemory(vmaAllocator, stagingBuffer.allocation, memoryPointer), "Unable to map memory");
			memCopy(memAddress(pixels), memoryPointer.get(0), pixels.remaining());
			vmaUnmapMemory(vmaAllocator, stagingBuffer.allocation);

			stbi_image_free(pixels);

			final int mipLevels = Maths.log2(Math.max(width, height)) + 1;
			final VulkanImage textureImage = createImage(width, height, mipLevels, VK_SAMPLE_COUNT_1_BIT, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
			textureImage.width = width;
			textureImage.height = height;

			transitionImageLayout(textureImage, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, textureImage.mipLevels);
			copyBufferToImage(stagingBuffer.buffer, textureImage);

			stagingBuffer.dispose();

			generateMipmaps(textureImage.image, VK_FORMAT_R8G8B8A8_SRGB, width, height, mipLevels);
			textureImage.imageView = createImageView(textureImage.image, VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, mipLevels);

			return textureImage;
		}
	}

	private static void generateMipmaps(long image, int imageFormat, int width, int height, int mipLevels) {
		try (MemoryStack stack = stackPush()) {// TODO: Check if image format supports linear blitting (?)
			final VkFormatProperties formatProperties = VkFormatProperties.malloc(stack);
			vkGetPhysicalDeviceFormatProperties(physicalDevice, imageFormat, formatProperties);

			if ((formatProperties.optimalTilingFeatures() & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0) {
				throw new RuntimeException("Texture image format does not support linear blitting");
			}

			final VkCommandBuffer commandBuffer = CommandBuffers.beginSingleTimeCommands(commandPool, stack);

			final VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
					.image(image)
					.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.dstAccessMask(VK_QUEUE_FAMILY_IGNORED);
			barrier.subresourceRange()
					.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.baseArrayLayer(0)
					.layerCount(1)
					.levelCount(1);

			int mipWidth = width;
			int mipHeight = height;

			for (int i = 1; i < mipLevels; i++) {
				barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
						.newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
						.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
						.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
						.subresourceRange().baseMipLevel(i - 1);

				vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

				final VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);

				blit.srcOffsets(0).set(0, 0, 0);
				blit.srcOffsets(1).set(mipWidth, mipHeight, 1);

				blit.srcSubresource()
						.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
						.mipLevel(i - 1)
						.baseArrayLayer(0)
						.layerCount(1);

				blit.dstOffsets(0).set(0, 0, 0);
				blit.dstOffsets(1).set(mipWidth > 1 ? mipWidth / 2 : 1, mipHeight > 1 ? mipHeight / 2 : 1, 1);

				blit.dstSubresource()
						.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
						.mipLevel(i)
						.baseArrayLayer(0)
						.layerCount(1);

				vkCmdBlitImage(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);

				barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
				barrier.newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
				barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
				barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

				vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

				if (mipWidth > 1) { mipWidth /= 2; }
				if (mipHeight > 1) { mipHeight /= 2; }
			}

			barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
					.newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
					.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
					.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
					.subresourceRange().baseMipLevel(mipLevels - 1);

			vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

			CommandBuffers.endSingleTimeCommands(commandBuffer, commandPool, queues.graphics, stack);
		}
	}

	public static long createTextureSampler(float maxAnisotropy) {
		try (MemoryStack stack = stackPush()) {
			final VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
					.magFilter(VK_FILTER_LINEAR)
					.minFilter(VK_FILTER_LINEAR)
					.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
					.anisotropyEnable(true)
					.maxAnisotropy(maxAnisotropy)
					.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
					.unnormalizedCoordinates(false)
					.compareEnable(false)
					.compareOp(VK_COMPARE_OP_ALWAYS)
					.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
					.minLod(0) // Optional
					.maxLod(512)
					.mipLodBias(0); // Optional

			final LongBuffer pTextureSampler = stack.mallocLong(1);

			if (vkCreateSampler(device, samplerInfo, null, pTextureSampler) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create texture sampler");
			}

			return pTextureSampler.get(0);
		}
	}

	public static long createImageView(long image, int format, int aspectFlags, int mipLevels) {
		try (MemoryStack stack = stackPush()) {
			final VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
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

			final LongBuffer pImageView = stack.mallocLong(1);

			if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create texture image view");
			}

			return pImageView.get(0);
		}
	}

	public static void transitionImageLayout(VulkanImage vulkanImage, int format, int oldLayout, int newLayout, int mipLevels) {
		try (MemoryStack stack = stackPush()) {
			final VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
					.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
					.oldLayout(oldLayout)
					.newLayout(newLayout)
					.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
					.image(vulkanImage.image);

			barrier.subresourceRange().baseMipLevel(0)
					.levelCount(mipLevels)
					.baseArrayLayer(0)
					.layerCount(1);

			if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
				barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);

				if (hasStencilComponent(format)) {
					barrier.subresourceRange().aspectMask(barrier.subresourceRange().aspectMask() | VK_IMAGE_ASPECT_STENCIL_BIT);
				}
			} else { barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT); }

			final int sourceStage;
			final int destinationStage;

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

			final VkCommandBuffer commandBuffer = CommandBuffers.beginSingleTimeCommands(commandPool, stack);
			vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0, null, null, barrier);
			CommandBuffers.endSingleTimeCommands(commandBuffer, commandPool, queues.graphics, stack);
		}
	}

	public static VulkanBuffer createVertexBufferFromStream(Stream<IVertex> vertices, int bufferSize) {
		return putDataIntoGPUOnlyBuffer(buffer -> vertices.forEach(vertex -> vertex.get(buffer)), bufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
	}

	public static VulkanBuffer putDataIntoGPUOnlyBuffer(Consumer<ByteBuffer> consumer, int bufferSize, int bufferUsage) {
		final VulkanBuffer buffer;

		if (integratedGPU) {
			buffer = createBuffer(bufferSize, bufferUsage, VMA_MEMORY_USAGE_CPU_TO_GPU);
			mapDataToVulkanBuffer(consumer, buffer, bufferSize);
		} else {
			final VulkanBuffer stagingBuffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_CPU_ONLY);
			mapDataToVulkanBuffer(consumer, stagingBuffer, bufferSize);

			buffer = createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | bufferUsage, VMA_MEMORY_USAGE_GPU_ONLY);
			copyBuffer(stagingBuffer, buffer, bufferSize);

			stagingBuffer.dispose();
		}

		return buffer;
	}

	public static void mapDataToVulkanBuffer(Consumer<ByteBuffer> consumer, VulkanBuffer vulkanBuffer, int bufferSize) {
		try (MemoryStack stack = stackPush()) {
			final PointerBuffer data = stack.mallocPointer(1);
			vmaMapMemory(vmaAllocator, vulkanBuffer.allocation, data);
			consumer.accept(data.getByteBuffer(0, bufferSize));
			vmaUnmapMemory(vmaAllocator, vulkanBuffer.allocation);
		}
	}

	public static void mapDataToVulkanBuffer(IBufferObject bufferObject, VulkanBuffer vulkanBuffer) {
		mapDataToVulkanBuffer(bufferObject::get, vulkanBuffer, bufferObject.sizeof());
	}

	public static VulkanBuffer createVertexBuffer(List<IVertex> vertices) {
		return createVertexBufferFromStream(vertices.stream(), vertices.get(0).sizeof() * vertices.size());
	}

	public static void cleanupSwapChain() {
		if (msaaEnabled) { colorImage.dispose(); }
		depthImage.dispose();

		MainRenderer.cleanupSwapChain();

		swapChainFramebuffers.forEach(framebuffer -> vkDestroyFramebuffer(device, framebuffer, null));

		vkDestroyRenderPass(device, renderPass, null);

		swapChainImageViews.forEach(imageView -> vkDestroyImageView(device, imageView, null));

		if (oldSwapChainHandle != VK_NULL_HANDLE) {
			vkDestroySwapchainKHR(device, oldSwapChainHandle, null);
		}
		oldSwapChainHandle = swapChainHandle;
	}

	public static void createSwapChainObjects() {
		createSwapChain();
		createImageViews();
		createRenderPass();
		if (msaaEnabled) { createColorResources(); }
		createDepthResources();
		createFramebuffers();
	}

	private static VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) { //TODO: check different formats
		for (final VkSurfaceFormatKHR format : formats) {
			//VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_B8G8R8A8_UNORM
			if ((format.format() == VK_FORMAT_R8G8B8_SRGB) && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
				return format;
			}
		}

		return formats.get(0);
	}

	private static int chooseSwapPresentMode(IntBuffer availablePresentModes) {
		for (int i = 0; i < availablePresentModes.capacity(); i++) {
			if (availablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
				return availablePresentModes.get(i);
			}
		}

		return VK_PRESENT_MODE_FIFO_KHR;
	}

	private static void createSwapChain() {
		try (MemoryStack stack = stackPush()) {
			final SwapChainSupportDetails swapChainSupport = querySwapChainSupport(physicalDevice, stack);

			final VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.formats);
			final int presentMode = chooseSwapPresentMode(swapChainSupport.presentModes);
			final VkExtent2D extent = chooseSwapExtent(swapChainSupport.capabilities, stack);

			final IntBuffer imageCount = stack.ints(swapChainSupport.capabilities.minImageCount() + 1);

			if (swapChainSupport.capabilities.maxImageCount() > 0 && imageCount.get(0) > swapChainSupport.capabilities.maxImageCount()) {
				imageCount.put(0, swapChainSupport.capabilities.maxImageCount());
			}

			final VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
					.surface(surfaceHandle)
					.minImageCount(imageCount.get(0))
					.imageFormat(surfaceFormat.format())
					.imageColorSpace(surfaceFormat.colorSpace())
					.imageExtent(extent)
					.imageArrayLayers(1)
					.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
					.preTransform(swapChainSupport.capabilities.currentTransform())
					.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
					.presentMode(presentMode)
					.clipped(true)
					.oldSwapchain(oldSwapChainHandle);

			if (queues.graphics.index != queues.present.index) {
				createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
				createInfo.pQueueFamilyIndices(stack.ints(queues.graphics.index, queues.present.index));
			} else {
				createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			}

			final LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);
			if (vkCreateSwapchainKHR(device, createInfo, null, pSwapChain) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create swap chain");
			}

			swapChainHandle = pSwapChain.get(0);

			vkGetSwapchainImagesKHR(device, swapChainHandle, imageCount, null);

			final LongBuffer pSwapChainImages = stack.mallocLong(imageCount.get(0));
			vkGetSwapchainImagesKHR(device, swapChainHandle, imageCount, pSwapChainImages);

			swapChainImages = new LongArrayList(imageCount.get(0));
			swapChainImageCount = imageCount.get(0);

			for (int i = 0; i < pSwapChainImages.capacity(); i++) {
				swapChainImages.add(pSwapChainImages.get(i));
			}

			swapChainImageFormat = surfaceFormat.format();
			swapChainExtent = VkExtent2D.create().set(extent);
		}
	}

	public static void recreateSwapChain() {
		try (MemoryStack stack = stackPush()) {
			final IntBuffer width = stack.ints(0);
			final IntBuffer height = stack.ints(0);

			while (width.get(0) == 0 && height.get(0) == 0) {
				glfwGetFramebufferSize(handle, width, height);
				glfwWaitEvents();
			}
		}
		vkDeviceWaitIdle(device);

		cleanupSwapChain();
		createSwapChainObjects();
		MainRenderer.createSwapChainObjects();
	}

	private static void createImageViews() {
		swapChainImageViews = new LongArrayList(swapChainImageCount);

		for (long swapChainImage : swapChainImages) {
			swapChainImageViews.add(createImageView(swapChainImage, swapChainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1));
		}
	}

	// TODO: split msaa
	private static void createRenderPass() {
		try (MemoryStack stack = stackPush()) {
			final int capacity = msaaEnabled ? 3 : 2;
			final VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(capacity, stack);
			final VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.calloc(capacity, stack);

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

			final VkAttachmentReference colorAttachmentRef = attachmentRefs.get(0);
			colorAttachmentRef.attachment(0);
			colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

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

			final VkAttachmentReference depthAttachmentRef = attachmentRefs.get(1)
					.attachment(1)
					.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

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

			final VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
					.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
					.colorAttachmentCount(1)
					.pColorAttachments(VkAttachmentReference.calloc(1, stack).put(0, colorAttachmentRef))
					.pDepthStencilAttachment(depthAttachmentRef);

			if (msaaEnabled) { subpass.pResolveAttachments(VkAttachmentReference.calloc(1, stack).put(0, colorAttachmentResolveRef)); }

			final VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
					.srcSubpass(VK_SUBPASS_EXTERNAL)
					.dstSubpass(0)
					.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.srcAccessMask(0)
					.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
					.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

			final VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
					.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
					.pAttachments(attachments)
					.pSubpasses(subpass)
					.pDependencies(dependency);

			final LongBuffer pRenderPass = stack.mallocLong(1);

			if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
				throw new RuntimeException("Failed to create render pass");
			}

			renderPass = pRenderPass.get(0);
		}
	}

	private static void createFramebuffers() {
		swapChainFramebuffers = new LongArrayList(swapChainImageViews.size());

		try (MemoryStack stack = stackPush()) {
			final LongBuffer attachments = msaaEnabled ? stack.longs(colorImage.imageView, depthImage.imageView, VK_NULL_HANDLE) : stack.longs(VK_NULL_HANDLE, depthImage.imageView);
			final LongBuffer pFramebuffer = stack.mallocLong(1);

			final VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
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

	private static void createColorResources() {
		colorImage = createImage(swapChainExtent.width(), swapChainExtent.height(), 1, msaaSamples, swapChainImageFormat, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
		colorImage.imageView = createImageView(colorImage.image, swapChainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1);

		transitionImageLayout(colorImage, swapChainImageFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, 1);
	}

	private static void createDepthResources() {
		final int depthFormat = findDepthFormat();

		depthImage = createImage(swapChainExtent.width(), swapChainExtent.height(), 1, msaaSamples, depthFormat, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VMA_MEMORY_USAGE_GPU_ONLY);
		depthImage.imageView = createImageView(depthImage.image, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT, 1);

		transitionImageLayout(depthImage, depthFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, 1);
	}

	private static int findSupportedFormat(IntBuffer formatCandidates, int tiling, int features) {
		try (MemoryStack stack = stackPush()) {
			final VkFormatProperties props = VkFormatProperties.calloc(stack);

			for (int i = 0; i < formatCandidates.capacity(); ++i) {
				final int format = formatCandidates.get(i);

				vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);

				if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() & features) == features ||
				    tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() & features) == features) {
					return format;
				}
			}
		}

		throw new RuntimeException("Failed to find supported format");
	}

	private static int findDepthFormat() {
		return findSupportedFormat(stackGet().ints(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT), VK_IMAGE_TILING_OPTIMAL, VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT);
	}

	private static VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
		if (capabilities.currentExtent().width() != UINT32_MAX) {
			return capabilities.currentExtent();
		}

		final IntBuffer width = stackGet().ints(0);
		final IntBuffer height = stackGet().ints(0);

		glfwGetFramebufferSize(handle, width, height);

		final VkExtent2D actualExtent = VkExtent2D.malloc(stack).set(width.get(0), height.get(0));

		final VkExtent2D minExtent = capabilities.minImageExtent();
		final VkExtent2D maxExtent = capabilities.maxImageExtent();

		actualExtent.width(clamp(actualExtent.width(), minExtent.width(), maxExtent.width()));
		actualExtent.height(clamp(actualExtent.height(), minExtent.height(), maxExtent.height()));

		return actualExtent;
	}

	public static class QueueFamilies {
		public final VulkanQueue graphics;
		public final VulkanQueue present;
		public final VulkanQueue compute;
		public final VulkanQueue transfer;

		public QueueFamilies(VulkanQueue graphics, VulkanQueue present, VulkanQueue compute, VulkanQueue transfer) {
			this.graphics = graphics;
			this.present = present;
			this.compute = compute;
			this.transfer = transfer;
		}

		public boolean hasGraphics() {
			return graphics != null;
		}

		public boolean hasPresent() {
			return present != null;
		}

		public boolean hasTransfer() {
			return transfer != null;
		}

		public boolean hasCompute() {
			return compute != null;
		}

		private boolean isComplete() {
			return hasGraphics() && hasPresent() && hasTransfer() && hasCompute();
		}

		public int[] unique() {
			return IntStream.of(graphics.index, present.index, transfer.index, compute.index).distinct().toArray();
		}
	}

	public static class SwapChainSupportDetails {
		public VkSurfaceCapabilitiesKHR capabilities;
		public VkSurfaceFormatKHR.Buffer formats;
		public IntBuffer presentModes;
	}
}
