package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.meshoptimizer.MeshOptimizer;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.body.PhysicsBody;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.factory.test.xpbd.events.PhysicsUpdateEvent;
import xyz.sathro.factory.util.ResourceManager;
import xyz.sathro.factory.window.Window;
import xyz.sathro.factory.window.events.KeyDownEvent;
import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.vulkan.events.DrawFrameEvent;
import xyz.sathro.vulkan.events.VulkanDisposeEvent;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Scene {
	@Getter private final FpsCamera camera = new FpsCamera();

	private final ExecutorService threadPool = Executors.newFixedThreadPool(1);

	@Getter private final List<PhysicsBody> bodies = new ObjectArrayList<>();
//	@Getter private final List<TetrahedralVolumeConstraint> volumeConstraints;
//	@Getter private final Int2ObjectMap<List<DistanceConstraint>> distanceConstraints;

	@Getter private final List<IMesh> meshes = new ObjectArrayList<>();

	public Scene() {
		EventManager.registerListeners(this);

		final List<Vector3d> vertices = new ObjectArrayList<>();
		final IntList tetrahedraIndices = new IntArrayList();
		loadTetModel("models/newDragon.tet", vertices, tetrahedraIndices);

		final List<MeshedBody.AttachedVertex> surfaceVertices = new ObjectArrayList<>();
		IntList surfaceIndices = new IntArrayList();
		loadTetSurfModel("models/newDragon.tetsurf", surfaceVertices, surfaceIndices);

		final IntBuffer intBuffer = MemoryUtil.memAllocInt(surfaceIndices.size());
		intBuffer.put(0, surfaceIndices.toIntArray());

		MeshOptimizer.meshopt_optimizeVertexCache(intBuffer, intBuffer, surfaceVertices.size());

		final int[] ints = new int[surfaceIndices.size()];
		intBuffer.get(ints);
		surfaceIndices = IntList.of(ints);
		MemoryUtil.memFree(intBuffer);

		for (Vector3d vertex : vertices) {
			vertex.y += 1;
		}

		final IMesh body = new MeshedBody(vertices, tetrahedraIndices, surfaceVertices, surfaceIndices);
		meshes.add(body);

		bodies.addAll(List.of(body.getParticles()));

		for (int i = 0; i < bodies.size(); i++) {
			bodies.get(i).setIndex(i);
		}
	}

	private static <E extends Constraint> Int2ObjectMap<List<E>> colorizeConstraints(List<E> constraints) {
		// in every color can be only one instance of certain particle
		final Int2ObjectMap<List<E>> coloredConstraintsMap = new Int2ObjectOpenHashMap<>();
		final Int2ObjectMap<Set<Particle>> particlesInColor = new Int2ObjectOpenHashMap<>();
		int color = 0;
		for (E constraint : constraints) {
			out:
			while (true) {
				if (!coloredConstraintsMap.containsKey(color)) {
					coloredConstraintsMap.put(color, new ArrayList<>());
					particlesInColor.put(color, new ObjectOpenHashSet<>());

					coloredConstraintsMap.get(color).add(constraint);
					particlesInColor.get(color).addAll(constraint.getConstrainedParticles());
					break;
				}
				for (Particle particle : constraint.getConstrainedParticles()) {
					if (particlesInColor.get(color).contains(particle)) {
						color++;
						continue out;
					}
				}
				coloredConstraintsMap.get(color).add(constraint);
				particlesInColor.get(color).addAll(constraint.getConstrainedParticles());
				color = 0;
				break;
			}
		}

		return coloredConstraintsMap;
	}

	private static void loadTetModel(String path, List<Vector3d> vertices, IntList indices) {
		try {
			final List<String> lines = Files.readAllLines(ResourceManager.getPathFromString(path));
			final String[] split = lines.get(0).split(" ");
			final int vertexCount = Integer.parseInt(split[1]);
			final int tetCount = Integer.parseInt(split[2]);
			for (int i = 1; i < 1 + vertexCount; i++) {
				final String[] line = lines.get(i).split(" ");
				vertices.add(new Vector3d(Double.parseDouble(line[0]),
				                          Double.parseDouble(line[1]),
				                          Double.parseDouble(line[2])));
			}

			for (int i = 1 + vertexCount; i < 1 + vertexCount + tetCount; i++) {
				final String[] line = lines.get(i).split(" ");
				for (int j = 0; j < 4; j++) {
					indices.add(Integer.parseInt(line[j]));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void loadTetSurfModel(String path, List<MeshedBody.AttachedVertex> vertices, IntList indices) {
		try {
			final List<String> lines = Files.readAllLines(ResourceManager.getPathFromString(path));
			final String[] split = lines.get(0).split(" ");
			final int vertexCount = Integer.parseInt(split[1]);
			final int triangleCount = Integer.parseInt(split[2]);
			for (int i = 1; i < 1 + vertexCount; i++) {
				final String[] line = lines.get(i).split(" ");
				vertices.add(new MeshedBody.AttachedVertex(
						Integer.parseInt(line[0]),
						Double.parseDouble(line[1]),
						Double.parseDouble(line[2]),
						Double.parseDouble(line[3])
				));
			}

			for (int i = 1 + vertexCount; i < 1 + vertexCount + triangleCount; i++) {
				final String[] line = lines.get(i).split(" ");
				for (int j = 0; j < 3; j++) {
					indices.add(Integer.parseInt(line[j]));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void loadObjModel(String path, List<Vector3d> vertices, IntList indices) {
		try {
			final List<String> lines = Files.readAllLines(ResourceManager.getPathFromString(path));
			for (String line : lines) {
				final String[] split = line.split(" ");
				if (split[0].equals("v")) {
					vertices.add(new Vector3d(Double.parseDouble(split[1]),
					                          Double.parseDouble(split[2]),
					                          Double.parseDouble(split[3])));
				} else if (split[0].equals("f")) {
					for (int j = 1; j < 4; j++) {
						indices.add(Integer.parseInt(split[j]) - 1);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SubscribeEvent
	public void onVulkanInit( /* create event */) {
		for (IMesh mesh : meshes) {
			PhysicsCompute.addParticles(List.of(mesh.getParticles()));

			final Map<Particle, List<DistanceConstraint>> distanceConstraintParticleMap = new Object2ObjectOpenHashMap<>();
			for (Particle particle : mesh.getParticles()) {
				distanceConstraintParticleMap.put(particle, new ObjectArrayList<>());
			}

			final Map<Particle, List<Constraint>> constraintParticleMap = new Object2ObjectOpenHashMap<>();
			for (Particle particle : mesh.getParticles()) {
				constraintParticleMap.put(particle, new ObjectArrayList<>());
			}

			final Map<Particle, List<TetrahedralVolumeConstraint>> volumeConstraintParticleMap = new Object2ObjectOpenHashMap<>();
			for (Particle particle : mesh.getParticles()) {
				volumeConstraintParticleMap.put(particle, new ObjectArrayList<>());
			}

			final List<DistanceConstraint> distanceConstraints = new ObjectArrayList<>();
			final List<TetrahedralVolumeConstraint> volumeConstraints = new ObjectArrayList<>();

			for (Constraint constraint : mesh.getConstraints()) {
				if (constraint instanceof DistanceConstraint distanceConstraint) {
					distanceConstraints.add(distanceConstraint);
					distanceConstraintParticleMap.get(distanceConstraint.getBody1()).add(distanceConstraint);
					distanceConstraintParticleMap.get(distanceConstraint.getBody2()).add(distanceConstraint);

					constraintParticleMap.get(distanceConstraint.getBody1()).add(distanceConstraint);
					constraintParticleMap.get(distanceConstraint.getBody2()).add(distanceConstraint);
				} else if (constraint instanceof TetrahedralVolumeConstraint volumeConstraint) {
					volumeConstraints.add(volumeConstraint);

					volumeConstraintParticleMap.get(volumeConstraint.getParticles()[0]).add(volumeConstraint);
					volumeConstraintParticleMap.get(volumeConstraint.getParticles()[1]).add(volumeConstraint);
					volumeConstraintParticleMap.get(volumeConstraint.getParticles()[2]).add(volumeConstraint);
					volumeConstraintParticleMap.get(volumeConstraint.getParticles()[3]).add(volumeConstraint);

					for (int i = 0; i < volumeConstraint.getParticles().length; i++) {
						constraintParticleMap.get(volumeConstraint.getParticles()[i]).add(volumeConstraint);
					}
				}
			}

			final Set<Constraint> alreadyInL = new ObjectOpenHashSet<>();
			final List<TetrahedralVolumeConstraint> sortedVolumeConstraints = new LinkedList<>();

			final Object2IntMap<Constraint> d = new Object2IntOpenHashMap<>();

			final Int2ObjectMap<LinkedList<TetrahedralVolumeConstraint>> D = new Int2ObjectOpenHashMap<>();

			int maxDV = 0;
			for (TetrahedralVolumeConstraint constraint : volumeConstraints) {
				final int dI = constraint.getConstrainedParticles().stream().mapToInt(p -> (int) (constraintParticleMap.get(p).stream().filter(constraint1 -> constraint1 instanceof TetrahedralVolumeConstraint).distinct().count() - 1)).reduce(0, Integer::sum);
				d.put(constraint, dI);
				maxDV = Math.max(maxDV, dI);

				if (!D.containsKey(dI)) {
					D.put(dI, new LinkedList<>());
				}
				D.get(dI).add(constraint);
			}

			for (int i = 0; i < maxDV; i++) {
				if (!D.containsKey(i)) {
					D.put(i, new LinkedList<>());
				}
			}

			int k = 0;

			for (int i = 0; i < volumeConstraints.size(); i++) {
				for (int j = 0; j < maxDV; j++) {
					if (!D.get(j).isEmpty()) {
						k = Math.max(k, j);
						final TetrahedralVolumeConstraint v = D.get(j).pop();
						sortedVolumeConstraints.add(v);
						alreadyInL.add(v);

						final List<Constraint> neighbors = v.getConstrainedParticles().stream().flatMap(p -> constraintParticleMap.get(p).stream().filter(c -> c instanceof TetrahedralVolumeConstraint)).toList();

						for (Constraint w : neighbors) {
							if (!alreadyInL.contains(w)) {
								D.get(d.getInt(w)).remove(w);
								d.put(w, d.getInt(w) - 1);
								D.get(d.getInt(w)).add((TetrahedralVolumeConstraint) w);
							}
						}
					}
				}
			}

			final Int2ObjectMap<List<DistanceConstraint>> coloredDistanceConstraintsMap = colorizeConstraints(distanceConstraints);
			final Int2ObjectMap<List<TetrahedralVolumeConstraint>> coloredVolumeConstraintsMap = colorizeConstraints(sortedVolumeConstraints);

//			System.out.println(coloredDistanceConstraintsMap.keySet().size());
//			System.out.println(coloredVolumeConstraintsMap.keySet().size());

			for (Int2ObjectMap.Entry<List<DistanceConstraint>> entry : coloredDistanceConstraintsMap.int2ObjectEntrySet()) {
				for (DistanceConstraint constraint : entry.getValue()) {
					PhysicsCompute.getConstraintColors().put(constraint, entry.getIntKey());
				}
			}

			for (Int2ObjectMap.Entry<List<TetrahedralVolumeConstraint>> entry : coloredVolumeConstraintsMap.int2ObjectEntrySet()) {
				for (TetrahedralVolumeConstraint constraint : entry.getValue()) {
					PhysicsCompute.getConstraintColors().put(constraint, entry.getIntKey());
				}
			}
			PhysicsCompute.getParticleConstraints().putAll(constraintParticleMap);

			PhysicsCompute.getColoredDistanceConstraints().putAll(coloredDistanceConstraintsMap);
			PhysicsCompute.getDistanceConstraints().addAll(distanceConstraints);

			PhysicsCompute.getColoredVolumeConstraints().putAll(coloredVolumeConstraintsMap);
			PhysicsCompute.getVolumeConstraints().addAll(volumeConstraints);

			PhysicsCompute.updateConstraints();

			PhysicsCompute.allocateModelBuffers((MeshedBody) mesh);
			PhysicsCompute.updateModelBuffers((MeshedBody) mesh);
			PhysicsCompute.updateBuffersDescriptorSet();

			PhysicsCompute.fillCommandBuffer();
			PhysicsCompute.ready = true;
		}
	}

	@SubscribeEvent
	public void onPhysicsUpdate(PhysicsUpdateEvent event) {
//		threadPool.submit(() -> { // FIXME: if compute finishes faster than buffers are updated there will be problems
//			for (IMesh mesh : meshes) {
//				mesh.updateBuffers();
//			}
//		});
	}

	@SubscribeEvent
	public void onVulkanDispose(VulkanDisposeEvent event) {
		threadPool.shutdown();
		for (IMesh mesh : meshes) {
			mesh.dispose();
		}
	}

	@SubscribeEvent
	public void onDrawFrame(DrawFrameEvent event) {
//		if (isSimulating || simulateOneFrame) {
//			simulateOneFrame = false;
//			PhysicsCompute.simulate();
////			Physics.simulate(bodies, volumeConstraints, PhysicsCompute2.getColoredDistanceConstraints());
//			for (IMesh mesh : meshes) {
//				mesh.updateBuffers();
//			}
//		}
	}

	@SubscribeEvent
	public void onMouseClick(MouseClickEvent event) {
		if (Window.isCursorGrabbed()) {
			// create ray from cameraPos + cameraRotation
		} else {
			// create ray from cameraPos + somehow get direction from cursor pos
		}
		// cast ray, test intersection with all objects on scene
	}

	@SubscribeEvent
	public void onKeyPressed(KeyDownEvent event) {

	}
}
