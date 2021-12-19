package xyz.sathro.factory.vulkan.renderers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import xyz.sathro.factory.ui.Interface;
import xyz.sathro.factory.ui.components.BasicUIComponent;
import xyz.sathro.factory.vulkan.vertices.UIVertex;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.descriptors.DescriptorPool;
import xyz.sathro.vulkan.descriptors.DescriptorSet;
import xyz.sathro.vulkan.descriptors.DescriptorSetLayout;
import xyz.sathro.vulkan.models.*;
import xyz.sathro.vulkan.renderer.IRenderer;
import xyz.sathro.vulkan.utils.CommandBuffers;
import xyz.sathro.vulkan.utils.VulkanPipelineBuilder;
import xyz.sathro.vulkan.vertex.MutableVertexData;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_CPU_TO_GPU;
import static org.lwjgl.vulkan.VK10.*;
import static xyz.sathro.factory.util.Maths.ceilDiv;
import static xyz.sathro.vulkan.utils.VulkanUtils.VkCheck;

public class UIRenderer implements IRenderer {
	private static final int MAX_UBO_SIZE = 256 * 256;
	public static final UIRenderer INSTANCE = new UIRenderer();
	private final MatrixUBO matrixUBO = new MatrixUBO();
	public final Int2ObjectMap<VulkanImage> idToTextureMap = new Int2ObjectOpenHashMap<>();
	public final Object2IntOpenHashMap<VulkanImage> textureToIDMap = new Object2IntOpenHashMap<>();
	private boolean dirty;
	private VulkanPipeline[] graphicPipelines;

	private DescriptorPool matrixDescriptorPool;
	private DescriptorSetLayout matrixDescriptorSetLayout;
	private List<DescriptorSet> matrixDescriptorSets;
	private VulkanBuffer[] matrixUniformBuffers;

	public int dataSetCount = 1;
	public int maxComponentsPerSet = MAX_UBO_SIZE / BasicUIComponent.SIZEOF;
	private DescriptorPool dataDescriptorPool;
	private DescriptorSetLayout dataDescriptorSetLayout;
	private List<List<DescriptorSet>> dataDescriptorSets;
	private VulkanBuffer[][] dataUniformBuffers;

	public int textureSetCount = 1;
	public int maxTexturesPerSet = 256;
	private DescriptorPool textureDescriptorPool;
	private DescriptorSetLayout textureDescriptorSetLayout;
	private List<List<DescriptorSet>> textureDescriptorSets;

	private long commandPool;
	private VkCommandBuffer[][] commandBuffers;
	public CombinedBuffer quadCombinedBuffer;
	public CombinedBuffer combinedBuffer;
	private boolean cbChanged = false;
	public List<BasicUIComponent> quadComponentList = new ObjectArrayList<>();
	public List<BasicUIComponent> nonQuadComponentList = new ObjectArrayList<>();
	public List<BasicUIComponent> componentList = new ObjectArrayList<>();
	private long textureSampler;

	private VulkanImage errorTexture;
	private int updateDataIndex;

	private UIRenderer() { }

	// TODO: Move this to API
	private VulkanBuffer[] createUniformBuffers(int size) {
		final VulkanBuffer[] uniformBuffers = new VulkanBuffer[Vulkan.swapChainImageCount];

		for (int i = 0; i < Vulkan.swapChainImageCount; i++) {
			uniformBuffers[i] = Vulkan.createBuffer(size, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_CPU_TO_GPU);
		}

		return uniformBuffers;
	}

	private void prepareCommandBuffers() {
		cbChanged = true;
		try (MemoryStack stack = stackPush()) {
			final LongBuffer quadVertexBuffer = stack.longs(this.quadCombinedBuffer.getVertexBuffer().handle);
			final LongBuffer vertexOffsets = stack.longs(0);
			final IntBuffer dataOffsets = stack.ints(0);

			for (int cbIndex = 0; cbIndex < commandBuffers.length; cbIndex++) {
				final VkCommandBuffer commandBuffer = commandBuffers[cbIndex][0];

				final VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
						.renderPass(Vulkan.renderPass)
						.framebuffer(Vulkan.swapChainFramebuffers.getLong(cbIndex))
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);

				final VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
						.pInheritanceInfo(inheritanceInfo)
						.flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
						.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

				VkCheck(vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin recording command buffer");

				for (VulkanPipeline graphicPipeline : graphicPipelines) {
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.handle);

					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 0, stack.longs(matrixDescriptorSets.get(cbIndex).getHandle()), null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 1, stack.longs(dataDescriptorSets.get(0).get(cbIndex).getHandle()), null);
					vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 2, stack.longs(textureDescriptorSets.get(0).get(cbIndex).getHandle()), null);

					int index = 0;
					int offset = 0;
					int firstTextureID = 0;
					int lastTextureID = maxTexturesPerSet;
					int setIndex;
					int dataSetIndex = 0;

					vkCmdBindVertexBuffers(commandBuffer, 0, quadVertexBuffer, vertexOffsets);
					vkCmdBindIndexBuffer(commandBuffer, quadCombinedBuffer.getIndexBuffer().handle, 0, VK_INDEX_TYPE_UINT32);
					for (BasicUIComponent component : quadComponentList) {
						// FIXME: now if textureID changed to be outside of its previous set things won't work well
						if (component.hasTexture && (component.textureID < firstTextureID || component.textureID >= lastTextureID)) {
							setIndex = ceilDiv(component.textureID, maxTexturesPerSet);

							firstTextureID = setIndex * maxTexturesPerSet;
							lastTextureID = (setIndex + 1) * maxTexturesPerSet;
							vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 2, stack.longs(textureDescriptorSets.get(setIndex).get(cbIndex).getHandle()), null);
						}

						vkCmdDrawIndexed(commandBuffer, 6, 1, 0, 0, index);

						if (++index == maxComponentsPerSet) {
							index = 0;
							if (dataSetIndex != dataSetCount - 1) {
								dataSetIndex++;
								vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 1, stack.longs(dataDescriptorSets.get(dataSetIndex).get(cbIndex).getHandle()), null);
							}
						}
//						if (index == 8) {
//							index = 0;
//							offset += 256;
//							dataOffsets.put(0, offset);
//							vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 1, stack.longs(dataDescriptorSets.get(cbIndex).getHandle()), dataOffsets);
//						}
					}

					if (!nonQuadComponentList.isEmpty()) {
						vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(this.combinedBuffer.getVertexBuffer().handle), vertexOffsets);
						vkCmdBindIndexBuffer(commandBuffer, combinedBuffer.getIndexBuffer().handle, 0, VK_INDEX_TYPE_UINT32);
						MutableVertexData vertexData;
						for (BasicUIComponent component : nonQuadComponentList) {
							if (component.hasTexture && (component.textureID < firstTextureID || component.textureID >= lastTextureID)) {
								setIndex = (int) Math.ceil((double) component.textureID / maxTexturesPerSet);

								firstTextureID = setIndex * maxTexturesPerSet;
								lastTextureID = (setIndex + 1) * maxTexturesPerSet;
								vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicPipeline.layout, 2, stack.longs(textureDescriptorSets.get(setIndex).get(cbIndex).getHandle()), null);
							}
							vertexData = combinedBuffer.getObjectVertexDataMap().get(component).get(UIVertex.class);

							vkCmdDrawIndexed(commandBuffer, vertexData.getIndexCount(), 1, vertexData.getIndexOffset(), vertexData.getVertexOffset(), index++);
						}
					}
				}

				VkCheck(vkEndCommandBuffer(commandBuffer), "Failed to record command buffer");
			}
		}
	}

	// TODO: Move this to API
	private VkCommandBuffer[][] createCommandBuffers() {
		final VkCommandBuffer[] buffers = CommandBuffers.createCommandBuffers(VK_COMMAND_BUFFER_LEVEL_SECONDARY, Vulkan.swapChainImageCount, commandPool);

		final VkCommandBuffer[][] commandBuffers = new VkCommandBuffer[Vulkan.swapChainImageCount][];
		for (int i = 0; i < commandBuffers.length; i++) {
			commandBuffers[i] = new VkCommandBuffer[] { buffers[i] };
		}

		return commandBuffers;
	}

	private DescriptorPool createMatrixDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImageCount)
				.setMaxSets(Vulkan.swapChainImageCount)
				.build();
	}

	private DescriptorSetLayout createMatrixDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT)
				.build();
	}

	private List<DescriptorSet> createMatrixDescriptorSets() {
		final List<DescriptorSet> descriptorSets = matrixDescriptorPool.createDescriptorSets(matrixDescriptorSetLayout, Vulkan.swapChainImageCount);

		try (MemoryStack stack = stackPush()) {
			final VkDescriptorBufferInfo.Buffer cameraBufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
					.offset(0)
					.range(VK_WHOLE_SIZE);

			for (int i = 0; i < descriptorSets.size(); i++) {
				cameraBufferInfo.buffer(matrixUniformBuffers[i].handle);

				descriptorSets.get(i).updateBuilder()
						.write(0).pBufferInfo(cameraBufferInfo).add()
						.update();
			}
		}

		return descriptorSets;
	}

	private DescriptorPool createDataDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Vulkan.swapChainImageCount * dataSetCount)
				.setMaxSets(Vulkan.swapChainImageCount * dataSetCount)
				.build();
	}

	private DescriptorSetLayout createDataDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
				.build();
	}

	private List<List<DescriptorSet>> createDataDescriptorSets() {
		final List<List<DescriptorSet>> descriptorSets = new ObjectArrayList<>();

		for (int setIndex = 0; setIndex < dataSetCount; setIndex++) {
			final List<DescriptorSet> currentSets = dataDescriptorPool.createDescriptorSets(dataDescriptorSetLayout, Vulkan.swapChainImageCount);

			try (MemoryStack stack = stackPush()) {
				final VkDescriptorBufferInfo.Buffer uiObjectsBufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
						.offset(0)
						.range(VK_WHOLE_SIZE);

				for (int imageIndex = 0; imageIndex < currentSets.size(); imageIndex++) {
					uiObjectsBufferInfo.buffer(dataUniformBuffers[setIndex][imageIndex].handle);

					currentSets.get(imageIndex).updateBuilder()
							.write(0).pBufferInfo(uiObjectsBufferInfo).add()
							.update();
				}
			}

			descriptorSets.add(currentSets);
		}

		return descriptorSets;
	}

	private DescriptorPool createTextureDescriptorPool() {
		return DescriptorPool.builder()
				.setTypeSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, Vulkan.swapChainImageCount * maxTexturesPerSet * textureSetCount)
				.setMaxSets(Vulkan.swapChainImageCount * textureSetCount)
				.build();
	}

	private DescriptorSetLayout createTextureDescriptorSetLayout() {
		return DescriptorSetLayout.builder()
				.addBinding(0, maxTexturesPerSet, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT)
				.build();
	}

	private List<List<DescriptorSet>> createTextureDescriptorSets() {
		final List<List<DescriptorSet>> textureDescriptorSets = new ObjectArrayList<>();

		for (int setIndex = 0; setIndex < textureSetCount; setIndex++) {
			textureDescriptorSets.add(textureDescriptorPool.createDescriptorSets(textureDescriptorSetLayout, Vulkan.swapChainImageCount));
		}

		updateTextureDescriptorSets(textureDescriptorSets);

		return textureDescriptorSets;
	}

	private void updateTextureDescriptorSets(List<List<DescriptorSet>> descriptorSets) {
		if (descriptorSets == null) { return; }

		int textureIndex = 0;
		for (int setIndex = 0; setIndex < textureSetCount; setIndex++) {
			final int texturesInCurrentSet;
			if (setIndex == textureSetCount - 1) { // last set
				texturesInCurrentSet = textureToIDMap.size() - (maxTexturesPerSet * setIndex);
			} else {
				texturesInCurrentSet = maxTexturesPerSet;
			}

			final VkDescriptorImageInfo.Buffer imageArrayInfo = VkDescriptorImageInfo.calloc(maxTexturesPerSet);

			for (int i = 0; i < texturesInCurrentSet; i++) {
				imageArrayInfo.get(i)
						.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
						.sampler(textureSampler)
						.imageView(idToTextureMap.get(textureIndex++).imageView);
			}

			for (int i = texturesInCurrentSet; i < maxTexturesPerSet; i++) {
				imageArrayInfo.get(i)
						.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
						.sampler(textureSampler)
						.imageView(errorTexture.imageView);
			}

			for (int i = 0; i < descriptorSets.get(setIndex).size(); i++) {
				descriptorSets.get(setIndex).get(i).updateBuilder()
						.write(0).pImageInfo(imageArrayInfo).add()
						.update();
			}

			imageArrayInfo.free();
		}
	}

	private VulkanPipeline[] createPipelines() {
		return new VulkanPipelineBuilder(new String[] { "shaders/ui_shader.vert" }, new String[] { "shaders/ui_shader.frag" }, new UIVertex(), new DescriptorSetLayout[] { matrixDescriptorSetLayout, dataDescriptorSetLayout, textureDescriptorSetLayout })
				.setCullMode(VK_CULL_MODE_NONE)
				.setDepthBuffer(false)
				.build();
	}

	private CombinedBuffer.Builder getDefaultBuilder() {
		return CombinedBuffer.builder().
				addVertex(new UIVertex(0)).
				addVertex(new UIVertex(1)).
				addVertex(new UIVertex(2)).
				addVertex(new UIVertex(3)).
				addIndices(UIVertex.class, 0, 1, 2, 1, 2, 3);
	}

	@Override
	public void init() {
		errorTexture = Vulkan.createTextureImage("textures/texture.jpg");
		idToTextureMap.put(0, errorTexture);
		textureToIDMap.put(errorTexture, 0);
		textureSampler = Vulkan.createTextureSampler(1);
		commandPool = Vulkan.createCommandPool(0, Vulkan.queues.graphics);

		matrixDescriptorSetLayout = createMatrixDescriptorSetLayout();
		dataDescriptorSetLayout = createDataDescriptorSetLayout();
		textureDescriptorSetLayout = createTextureDescriptorSetLayout();

		quadCombinedBuffer = getDefaultBuilder().build();
		dataUniformBuffers = createDataBuffers();

		createSwapChain();

		Interface.INSTANCE.init();
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

	public void rebuildUI(boolean texturesChanged, List<BasicUIComponent> components) {
		componentList = new ObjectArrayList<>(components);

		final int newDataSetCount = Math.max(1, ceilDiv(componentList.size(), maxComponentsPerSet));
		if (dataSetCount != newDataSetCount) {
			dirty = true;
			dataSetCount = newDataSetCount;
			if (dataDescriptorPool != null) {
				dataDescriptorPool.registerToDisposal();
			}
			if (dataUniformBuffers != null) {
				for (VulkanBuffer[] buffers : dataUniformBuffers) {
					for (VulkanBuffer buffer : buffers) {
						buffer.registerToDisposal();
					}
				}
			}
			dataUniformBuffers = createDataBuffers();
			dataDescriptorPool = createDataDescriptorPool();
			dataDescriptorSets = createDataDescriptorSets();
		}

		if (texturesChanged) {
			textureToIDMap.clear();
			textureToIDMap.put(errorTexture, 0);
			idToTextureMap.clear();
			idToTextureMap.put(0, errorTexture);
			for (BasicUIComponent component : componentList) {
				if (component.hasTexture) {
					if (!textureToIDMap.containsKey(component.texture)) {
						idToTextureMap.put(textureToIDMap.size(), component.texture);
						textureToIDMap.put(component.texture, textureToIDMap.size() + 1);
						component.textureID = textureToIDMap.size();
					} else {
						component.textureID = textureToIDMap.getInt(component.texture);
					}
				}
			}

			final int newTextureSetCount = Math.max(1, ceilDiv(textureToIDMap.size(), maxTexturesPerSet));
			if (textureSetCount != newTextureSetCount) {
				dirty = true;
				textureSetCount = newTextureSetCount;
				if (textureDescriptorPool != null) {
					textureDescriptorPool.registerToDisposal();
				}
				textureDescriptorPool = createTextureDescriptorPool();
				textureDescriptorSets = createTextureDescriptorSets();
			} else {
				updateTextureDescriptorSets(textureDescriptorSets);
			}
		}

		quadComponentList = new ObjectArrayList<>();
		nonQuadComponentList = new ObjectArrayList<>();

		for (BasicUIComponent component : componentList) {
			if (component.isSimpleQuad()) {
				quadComponentList.add(component);
			} else {
				nonQuadComponentList.add(component);
			}
		}

		if (combinedBuffer != null) {
			combinedBuffer.registerToDisposal();
		}
		if (!nonQuadComponentList.isEmpty()) {
			final CombinedBuffer.Builder builder = CombinedBuffer.builder();
			nonQuadComponentList.forEach(c -> c.appendToBufferBuilder(builder));
			combinedBuffer = builder.build();
		}

		updateDataBuffers();
	}

	private VulkanBuffer[][] createDataBuffers() {
		final VulkanBuffer[][] buffers = new VulkanBuffer[dataSetCount][];
		for (int i = 0; i < dataSetCount; i++) {
			buffers[i] = createUniformBuffers(MAX_UBO_SIZE);
		}

		return buffers;
	}

	public void updateMatrixBuffers() {
		matrixUBO.proj.identity().setOrtho(0, Vulkan.swapChainExtent.width(), 0, Vulkan.swapChainExtent.height(), 2, -2);
		for (VulkanBuffer buffer : matrixUniformBuffers) {
			Vulkan.mapDataToVulkanBuffer(matrixUBO, buffer);
		}
	}

	public void updateDataBuffers() {
		updateDataIndex = Vulkan.swapChainImageCount;
	}

	@Override
	public void beforeFrame(int imageIndex) {
		if (updateDataIndex-- > 0) {
			for (int i = 0; i < dataSetCount; i++) {
				final long index = i;
				Vulkan.mapDataToVulkanBuffer(buffer -> componentList.stream().sequential().skip(index * maxComponentsPerSet).limit(maxComponentsPerSet).forEach(c -> c.get(buffer)), dataUniformBuffers[i][imageIndex], MAX_UBO_SIZE);
			}
		}

		if (dirty) {
			dirty = false;
			vkResetCommandPool(Vulkan.device, commandPool, 0);
			prepareCommandBuffers();
		}
	}

	@Override
	public void afterFrame(int imageIndex) {
		// void
	}

	@Override
	public void createSwapChain() {
		commandBuffers = createCommandBuffers();

		matrixUniformBuffers = createUniformBuffers(MatrixUBO.SIZEOF);
		updateMatrixBuffers();

		matrixDescriptorPool = createMatrixDescriptorPool();
		matrixDescriptorSets = createMatrixDescriptorSets();

		dataDescriptorPool = createDataDescriptorPool();
		dataDescriptorSets = createDataDescriptorSets();

		textureDescriptorPool = createTextureDescriptorPool();
		textureDescriptorSets = createTextureDescriptorSets();

		graphicPipelines = createPipelines();

		this.dirty = true;
	}

	@Override
	public void cleanupSwapChain() {
		for (VkCommandBuffer[] commandBuffer : commandBuffers) {
			VK10.vkFreeCommandBuffers(Vulkan.device, commandPool, commandBuffer[0]);
		}

		Arrays.stream(matrixUniformBuffers).forEach(VulkanBuffer::dispose);

		matrixDescriptorPool.dispose();
		dataDescriptorPool.dispose();
		textureDescriptorPool.dispose();

		Arrays.stream(graphicPipelines).forEach(VulkanPipeline::dispose);
	}

	@Override
	public void dispose() {
		for (VulkanBuffer[] buffers : dataUniformBuffers) {
			for (VulkanBuffer buffer : buffers) {
				buffer.dispose();
			}
		}

		matrixDescriptorSetLayout.dispose();
		dataDescriptorSetLayout.dispose();
		textureDescriptorSetLayout.dispose();
		vkDestroyCommandPool(Vulkan.device, commandPool, null);

		errorTexture.dispose();
		quadCombinedBuffer.dispose();
		vkDestroySampler(Vulkan.device, textureSampler, null);
	}

	private static class MatrixUBO implements IBufferObject {
		private static final int SIZEOF = 16 * Float.BYTES;

		public final Matrix4f proj = new Matrix4f();

		@Override
		public int sizeof() {
			return SIZEOF;
		}

		@Override
		public void get(int index, ByteBuffer buffer) {
			proj.get(index, buffer);
		}
	}
}
