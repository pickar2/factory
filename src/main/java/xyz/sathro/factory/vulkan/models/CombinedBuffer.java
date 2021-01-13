package xyz.sathro.factory.vulkan.models;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import xyz.sathro.factory.vulkan.Vulkan;
import xyz.sathro.factory.vulkan.vertex.IVertex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// TODO: Come up with clever name.
public class CombinedBuffer {
	private final Map<Class<? extends IVertex>, List<Integer>> data = new Object2ObjectOpenHashMap<>(); // List.of(vertexOffset, indexOffset, indexCount)
	@Getter private final VulkanBuffer vertexBuffer = new VulkanBuffer();
	@Getter private final VulkanBuffer indexBuffer = new VulkanBuffer();
	@Getter private Builder builder; // TODO: Maybe remove link to builder? The only case we need it is when only some vertexTypes changed AND we know which one.

	private CombinedBuffer() { }

	public static Builder builder() {
		return new CombinedBuffer().new Builder();
	}

	public int getVertexOffset(Class<? extends IVertex> type) {
		return data.get(type).get(0);
	}

	public int getIndexOffset(Class<? extends IVertex> type) {
		return data.get(type).get(1);
	}

	public int getIndexCount(Class<? extends IVertex> type) {
		return data.get(type).get(2);
	}

	public void dispose() {
		vertexBuffer.dispose();
		indexBuffer.dispose();
	}

	public class Builder {
		private final Map<Class<? extends IVertex>, List<IVertex>> vertices = new Object2ObjectOpenHashMap<>();
		private final Map<Class<? extends IVertex>, List<Integer>> indicesMap = new Object2ObjectOpenHashMap<>();

		private Builder() {
			builder = this;
		}

		public void addVertex(IVertex vertex) {
			if (!vertices.containsKey(vertex.getClass())) {
				vertices.put(vertex.getClass(), new ObjectArrayList<>());
			}
			vertices.get(vertex.getClass()).add(vertex);
		}

		public void addIndex(Class<? extends IVertex> type, int index) {
			if (!indicesMap.containsKey(type)) {
				indicesMap.put(type, new ObjectArrayList<>());
			}
			indicesMap.get(type).add(index);
		}

		public void addIndices(Class<? extends IVertex> type, Integer... indices) {
			if (!indicesMap.containsKey(type)) {
				indicesMap.put(type, new ObjectArrayList<>());
			}
			indicesMap.get(type).addAll(Arrays.asList(indices));
		}

		public int getTotalSize() {
			int size = 0;
			for (List<IVertex> list : vertices.values()) {
				size += list.get(0).sizeof() * list.size();
			}

			return size;
		}

		public CombinedBuffer build() {
			int vertexOffset = 0;
			IntList indices = new IntArrayList();
			for (Class<? extends IVertex> type : vertices.keySet()) {
				data.put(type, IntArrayList.wrap(new int[] { vertexOffset, indices.size(), indicesMap.get(type).size() }));

				indices.addAll(indicesMap.get(type));

				vertexOffset += vertices.get(type).size();
			}

			vertexBuffer.set(Vulkan.createVertexBufferFromStream(vertices.values().stream().flatMap(List::stream), getTotalSize()));
			indexBuffer.set(Vulkan.createIndexBuffer(indices.toIntArray()));

			return CombinedBuffer.this;
		}
	}
}
