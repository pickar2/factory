package xyz.sathro.vulkan.utils;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

public class ModelLoader {
	private ModelLoader() { }

	public static Model loadModel(String resource, int flags) {
		final AIScene scene = AIFileLoader.readFile(resource, flags);

		if (scene == null || scene.mRootNode() == null) {
			throw new RuntimeException("Could not load model: " + aiGetErrorString());
		}
		final Model model = new Model();
		processNode(requireNonNull(scene.mRootNode()), scene, model);

		aiReleaseImport(scene);

		return model;
	}

	private static void processNode(AINode node, AIScene scene, Model model) {
		if (node.mMeshes() != null) {
			processNodeMeshes(scene, node, model);
		}

		final PointerBuffer children = node.mChildren();
		if (children != null) {
			for (int i = 0; i < node.mNumChildren(); i++) {
				processNode(AINode.create(children.get(i)), scene, model);
			}
		}
	}

	private static void processNodeMeshes(AIScene scene, AINode node, Model model) {
		final PointerBuffer pMeshes = scene.mMeshes();
		final IntBuffer meshIndices = node.mMeshes();

		if (pMeshes != null && meshIndices != null) {
			for (int i = 0; i < meshIndices.capacity(); i++) {
				AIMesh mesh = AIMesh.create(pMeshes.get(meshIndices.get(i)));
				processMesh(scene, mesh, model);
			}
		}
	}

	private static void processMesh(AIScene scene, AIMesh mesh, Model model) {
		processPositions(mesh, model.positions);
		processTexCoords(mesh, model.texCoords);

		processIndices(mesh, model.indices);
	}

	private static void processPositions(AIMesh mesh, List<Vector3f> positions) {
		final AIVector3D.Buffer vertices = requireNonNull(mesh.mVertices());

		for (int i = 0; i < vertices.capacity(); i++) {
			final AIVector3D position = vertices.get(i);
			positions.add(new Vector3f(position.x(), position.y(), position.z()));
		}
	}

	private static void processTexCoords(AIMesh mesh, List<Vector2f> texCoords) {
		final AIVector3D.Buffer aiTexCoords = mesh.mTextureCoords(0);
		if (aiTexCoords == null) { return; }

		for (int i = 0; i < aiTexCoords.capacity(); i++) {
			final AIVector3D coords = aiTexCoords.get(i);
			texCoords.add(new Vector2f(coords.x(), coords.y()));
		}
	}

	private static void processIndices(AIMesh mesh, List<Integer> indices) {
		final AIFace.Buffer aiFaces = mesh.mFaces();

		for (int i = 0; i < mesh.mNumFaces(); i++) {
			final AIFace face = aiFaces.get(i);
			final IntBuffer pIndices = face.mIndices();

			for (int j = 0; j < face.mNumIndices(); j++) {
				indices.add(pIndices.get(j));
			}
		}
	}

	public static class Model {
		public final List<Vector3f> positions;
		public final List<Vector2f> texCoords;
		public final IntList indices;

		public Model() {
			this.positions = new ArrayList<>();
			this.texCoords = new ArrayList<>();
			this.indices = new IntArrayList();
		}
	}
}
