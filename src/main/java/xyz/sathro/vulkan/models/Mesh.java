package xyz.sathro.vulkan.models;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import xyz.sathro.vulkan.vertex.IVertex;

public class Mesh {
	public final IntList indices = new IntArrayList();
	public final ObjectList<IVertex> vertices = new ObjectArrayList<>();

	public Mesh() { }

	public void add(Mesh mesh) {
		int indexCount = indices.size();
		for (Integer index : mesh.indices) {
			indices.add(index + indexCount);
		}
		for (IVertex vertex : mesh.vertices) {
			vertices.add(vertex.copy());
		}
	}
}
