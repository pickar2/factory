package xyz.sathro.vulkan.models;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.vertex.IVertex;
import xyz.sathro.vulkan.vertex.MutableVertexData;
import xyz.sathro.vulkan.vertex.VertexData;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

// TODO: Come up with clever name.
public class CombinedBuffer implements IDisposable {
	private final Map<Class<? extends IVertex>, VertexData> data = new Object2ObjectOpenHashMap<>();
	@Getter private final VulkanBuffer vertexBuffer = new VulkanBuffer();
	@Getter private final VulkanBuffer indexBuffer = new VulkanBuffer();
	@Getter private final Map<Object, Map<Class<? extends IVertex>, MutableVertexData>> objectVertexDataMap = new Object2ObjectOpenHashMap<>();
	@Getter private Builder builder; // TODO: Maybe remove link to builder? The only case we need it is when only some vertexTypes changed AND we know which one.

	private CombinedBuffer() { }

	public static Builder builder() {
		return new CombinedBuffer().new Builder();
	}

	public static <K, N, E extends List<N>> void mergeMaps(Map<K, E> first, Map<K, E> second, Function<E, E> listProducer) {
		for (final var entry : second.entrySet()) {
			if (!first.containsKey(entry.getKey())) {
				first.put(entry.getKey(), listProducer.apply(entry.getValue()));
			} else {
				first.get(entry.getKey()).addAll(entry.getValue());
			}
		}
	}

	public int getVertexOffset(Class<? extends IVertex> type) {
		return data.get(type).getVertexOffset();
	}

	public int getIndexOffset(Class<? extends IVertex> type) {
		return data.get(type).getIndexOffset();
	}

	public int getIndexCount(Class<? extends IVertex> type) {
		return data.get(type).getIndexCount();
	}

	public VertexData getVertexData(Class<? extends IVertex> type) {
		return data.get(type);
	}

	public void dispose() {
		vertexBuffer.dispose();
		indexBuffer.dispose();
	}

	public class Builder {
		private final Map<Class<? extends IVertex>, List<IVertex>> verticesMap = new Object2ObjectOpenHashMap<>();
		private final Map<Class<? extends IVertex>, IntList> indicesMap = new Object2ObjectOpenHashMap<>();
		private final Map<Class<? extends IVertex>, MutableVertexData> currentVertexData = new Object2ObjectOpenHashMap<>();
		private Object currentObject;

		private Builder() {
			builder = this;
		}

		public Builder startObject(Object object) {
			if (currentObject != null) { throw new IllegalStateException(); }
			currentObject = object;

			return this;
		}

		public Builder endObject() {
			objectVertexDataMap.put(currentObject, currentVertexData);
			currentObject = null;
			currentVertexData.clear();

			return this;
		}

		public Builder addVertex(IVertex vertex) {
			final Class<? extends IVertex> vertexClass = vertex.getClass();
			if (!verticesMap.containsKey(vertexClass)) {
				verticesMap.put(vertexClass, new ObjectArrayList<>());
			}
			if (!currentVertexData.containsKey(vertexClass)) {
				currentVertexData.put(vertexClass, new MutableVertexData(verticesMap.get(vertexClass).size(), -1, 0));
			}
			if (currentVertexData.get(vertexClass).getVertexOffset() == -1) {
				currentVertexData.get(vertexClass).setVertexOffset(verticesMap.get(vertexClass).size());
			}
			verticesMap.get(vertexClass).add(vertex);

			return this;
		}

		public Builder addVertices(IVertex... vertices) {
			Class<? extends IVertex> vertexClass;
			for (IVertex vertex : vertices) {
				vertexClass = vertex.getClass();
				if (!verticesMap.containsKey(vertexClass)) {
					verticesMap.put(vertexClass, new ObjectArrayList<>());
				}
				if (!currentVertexData.containsKey(vertexClass)) {
					currentVertexData.put(vertexClass, new MutableVertexData(verticesMap.get(vertexClass).size(), -1, 0));
				}
				if (currentVertexData.get(vertexClass).getVertexOffset() == -1) {
					currentVertexData.get(vertexClass).setVertexOffset(verticesMap.get(vertexClass).size());
				}
				verticesMap.get(vertexClass).add(vertex);
			}

			return this;
		}

		public Builder addIndex(Class<? extends IVertex> type, int index) {
			if (!indicesMap.containsKey(type)) {
				indicesMap.put(type, new IntArrayList());
			}
			if (!currentVertexData.containsKey(type)) {
				currentVertexData.put(type, new MutableVertexData(-1, indicesMap.get(type).size(), 0));
			}
			if (currentVertexData.get(type).getIndexOffset() == -1) {
				currentVertexData.get(type).setIndexOffset(indicesMap.get(type).size());
			}
			currentVertexData.get(type).setIndexCount(currentVertexData.get(type).getIndexCount() + 1);
			indicesMap.get(type).add(index);

			return this;
		}

		public Builder addIndices(Class<? extends IVertex> type, int... indices) {
			if (!indicesMap.containsKey(type)) {
				indicesMap.put(type, new IntArrayList());
			}
			if (!currentVertexData.containsKey(type)) {
				currentVertexData.put(type, new MutableVertexData(-1, indicesMap.get(type).size(), 0));
			}
			if (currentVertexData.get(type).getIndexOffset() == -1) {
				currentVertexData.get(type).setIndexOffset(indicesMap.get(type).size());
			}
			currentVertexData.get(type).setIndexCount(currentVertexData.get(type).getIndexCount() + indices.length);
			indicesMap.get(type).addElements(indicesMap.get(type).size(), indices);

			return this;
		}

		public int getVertexCount(Class<? extends IVertex> type) {
			if (verticesMap.containsKey(type)) { return verticesMap.get(type).size(); }
			return 0;
		}

		public int getTotalSize() {
			int size = 0;
			for (List<IVertex> list : verticesMap.values()) {
				if (!list.isEmpty()) {
					size += list.get(0).sizeof() * list.size();
				}
			}

			return size;
		}

		/**
		 * Copies vertices and indices from other builder to this one.
		 *
		 * @param other builder to copy data from.
		 * @return this
		 */
		public Builder add(Builder other) {
			mergeMaps(this.verticesMap, other.verticesMap, ObjectArrayList::new);
			mergeMaps(this.indicesMap, other.indicesMap, IntArrayList::new);

			return this;
		}

		public CombinedBuffer build() {
			int vertexOffset = 0;
			final IntList indices = new IntArrayList();
			for (Class<? extends IVertex> type : verticesMap.keySet()) {
				data.put(type, new VertexData(vertexOffset, indices.size(), indicesMap.get(type).size()));

				indices.addAll(indicesMap.get(type));

				vertexOffset += verticesMap.get(type).size();
			}

			vertexBuffer.set(Vulkan.createVertexBufferFromStream(verticesMap.values().stream().flatMap(List::stream), getTotalSize()));
			indexBuffer.set(Vulkan.createIndexBuffer(indices.toIntArray()));

			return CombinedBuffer.this;
		}
	}
}
