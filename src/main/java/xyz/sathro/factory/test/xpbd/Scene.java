package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.body.PhysicsBody;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.factory.util.ResourceManager;
import xyz.sathro.factory.window.Window;
import xyz.sathro.factory.window.events.GameUpdateEvent;
import xyz.sathro.factory.window.events.KeyDownEvent;
import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.vulkan.events.DrawFrameEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Scene {
	@Getter private final FpsCamera camera = new FpsCamera();

	@Getter private final List<PhysicsBody> bodies = new ObjectArrayList<>();
	@Getter private final List<TetrahedralVolumeConstraint> volumeConstraints;
//	@Getter private final Int2ObjectMap<List<DistanceConstraint>> distanceConstraints;

	@Getter private final List<MeshedBody> meshes = new ObjectArrayList<>();

	private boolean isSimulating = true;
	private boolean simulateOneFrame = false;

	public Scene() {
		EventManager.registerListeners(this);

		final List<Vector3d> vertices = new ObjectArrayList<>();
		final IntList tetrahedraIndices = new IntArrayList();
		loadTetModel("models/dragon.tet", vertices, tetrahedraIndices);

		final List<Vector3d> surfaceVertices = new ObjectArrayList<>();
		final IntList surfaceIndices = new IntArrayList();
		loadObjModel("models/dragon.surf", surfaceVertices, surfaceIndices);

		final MeshedBody body = new MeshedBody(vertices, tetrahedraIndices, surfaceVertices, surfaceIndices);
		meshes.add(body);

		bodies.addAll(List.of(body.getParticles()));

		for (int i = 0; i < bodies.size(); i++) {
			bodies.get(i).setIndex(i);
		}

//		constraints.addAll(body.getConstraints());

//		final Map<Particle, List<DistanceConstraint>> distanceConstraintParticleMap = new Object2ObjectOpenHashMap<>();
//		for (Particle particle : body.getParticles()) {
//			distanceConstraintParticleMap.put(particle, new ObjectArrayList<>());
//		}
//
//		final Map<Particle, List<TetrahedralVolumeConstraint>> volumeConstraintParticleMap = new Object2ObjectOpenHashMap<>();
//		for (Particle particle : body.getParticles()) {
//			volumeConstraintParticleMap.put(particle, new ObjectArrayList<>());
//		}

//		final List<DistanceConstraint> distanceConstraints = new ObjectArrayList<>();
		final List<TetrahedralVolumeConstraint> volumeConstraints = new ObjectArrayList<>();
//
		for (Constraint constraint : body.getConstraints()) {
			if (constraint instanceof DistanceConstraint distanceConstraint) {
//				distanceConstraints.add(distanceConstraint);
////				distanceConstraintParticleMap.get(distanceConstraint.getBody1()).add(distanceConstraint);
////				distanceConstraintParticleMap.get(distanceConstraint.getBody2()).add(distanceConstraint);
			} else if (constraint instanceof TetrahedralVolumeConstraint volumeConstraint) {
				volumeConstraints.add(volumeConstraint);
////				volumeConstraintParticleMap.get(volumeConstraint.getParticles()[0]).add(volumeConstraint);
////				volumeConstraintParticleMap.get(volumeConstraint.getParticles()[1]).add(volumeConstraint);
////				volumeConstraintParticleMap.get(volumeConstraint.getParticles()[2]).add(volumeConstraint);
////				volumeConstraintParticleMap.get(volumeConstraint.getParticles()[3]).add(volumeConstraint);
			}
		}
//		final Int2ObjectMap<List<DistanceConstraint>> coloredConstraintsMap = colorizeConstraints(distanceConstraints);
//		final Int2ObjectMap<List<TetrahedralVolumeConstraint>> coloredVolumeConstraintsMap = colorConstraints(volumeConstraintParticleMap, body.getParticles());

		this.volumeConstraints = volumeConstraints;
//		this.distanceConstraints = coloredConstraintsMap;
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
		for (MeshedBody mesh : meshes) {
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

			Collections.shuffle(distanceConstraints);
			Collections.shuffle(volumeConstraints);

			final Int2ObjectMap<List<DistanceConstraint>> coloredDistanceConstraintsMap = colorizeConstraints(distanceConstraints);
			final Int2ObjectMap<List<TetrahedralVolumeConstraint>> coloredVolumeConstraintsMap = colorizeConstraints(volumeConstraints);

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
		}
	}

	@SubscribeEvent
	public void onGameUpdate(GameUpdateEvent event) {
		if (isSimulating || simulateOneFrame) {
			simulateOneFrame = false;
			PhysicsCompute.simulate();
			for (MeshedBody mesh : meshes) {
				mesh.updateBuffers();
			}
		}
	}

	@SubscribeEvent
	public void onDrawFrame(DrawFrameEvent event) {
//		if (isSimulating || simulateOneFrame) {
//			simulateOneFrame = false;
//			PhysicsCompute.simulate();
////			Physics.simulate(bodies, volumeConstraints, PhysicsCompute2.getColoredDistanceConstraints());
//			for (MeshedBody mesh : meshes) {
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
		if (event.action == GLFW.GLFW_PRESS && event.key == GLFW.GLFW_KEY_P) {
			isSimulating = !isSimulating;
		}
		if (event.action == GLFW.GLFW_PRESS && event.key == GLFW.GLFW_KEY_F) {
			simulateOneFrame = true;
		}
		if (event.action == GLFW.GLFW_PRESS && event.key == GLFW.GLFW_KEY_R) {
			PhysicsCompute.updateParticles();
		}
	}
}
