package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.joml.Vector3d;
import org.joml.Vector3f;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.factory.test.xpbd.constraint.DistanceConstraint;
import xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint;
import xyz.sathro.factory.test.xpbd.util.BodyUtils;
import xyz.sathro.factory.vulkan.scene.SceneModelVertex;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.VulkanBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Log4j2
public class MeshedBody {
	private static final Vector3f color = new Vector3f(0.9f, 0.6f, 0.3f);

	private final Vector3d[] vertices;
	private final int[] surfaceIndices;

	private final Particle[] particles;
	private final List<Constraint> constraints;

	private VulkanBuffer vertexBuffer;
	private VulkanBuffer indexBuffer;
	private int indexCount;

	public MeshedBody(List<Vector3d> allVertices, IntList tetrahedraIndices, List<Vector3d> surfaceVertices, IntList surfaceIndices) {
		final Map<Vector3d, Integer> allVertexToIndex = new HashMap<>();
		for (int i = 0; i < allVertices.size(); i++) {
			allVertexToIndex.put(allVertices.get(i), i);
		}

		final int[] surfaceIndicesFixed = new int[surfaceIndices.size()];
		for (int i = 0; i < surfaceIndices.size(); i++) {
			final Vector3d vec = surfaceVertices.get(surfaceIndices.getInt(i));
			if (!allVertexToIndex.containsKey(vec)) {
				throw new RuntimeException("index=" + i + ", (" + vec.x + ", " + vec.y + ", " + vec.z + ")");
			}
			surfaceIndicesFixed[surfaceIndicesFixed.length - i - 1] = allVertexToIndex.get(vec);
		}

		final Particle[] particles = new Particle[allVertices.size()];
		for (int i = 0; i < particles.length; i++) {
			particles[i] = new Particle(allVertices.get(i));
		}

		final Tetrahedron[] tetrahedra = new Tetrahedron[tetrahedraIndices.size() / 4];
		for (int i = 0; i < tetrahedra.length; i++) {
			tetrahedra[i] = new Tetrahedron(new Particle[] {
					particles[tetrahedraIndices.getInt(i * 4)],
					particles[tetrahedraIndices.getInt(i * 4 + 1)],
					particles[tetrahedraIndices.getInt(i * 4 + 2)],
					particles[tetrahedraIndices.getInt(i * 4 + 3)]
			});
		}

		final double distanceCompliance = 0.002;
		final double volumeCompliance = 0.000001;

		final List<Constraint> constraints = new ObjectArrayList<>(tetrahedra.length * 7);
		for (Tetrahedron tet : tetrahedra) {
			constraints.add(new DistanceConstraint(tet.particles[0], tet.particles[1], BodyUtils.getDistance(tet.particles[0], tet.particles[1]), distanceCompliance));
			constraints.add(new DistanceConstraint(tet.particles[0], tet.particles[2], BodyUtils.getDistance(tet.particles[0], tet.particles[2]), distanceCompliance));
			constraints.add(new DistanceConstraint(tet.particles[0], tet.particles[3], BodyUtils.getDistance(tet.particles[0], tet.particles[3]), distanceCompliance));
			constraints.add(new DistanceConstraint(tet.particles[1], tet.particles[2], BodyUtils.getDistance(tet.particles[1], tet.particles[2]), distanceCompliance));
			constraints.add(new DistanceConstraint(tet.particles[2], tet.particles[3], BodyUtils.getDistance(tet.particles[2], tet.particles[3]), distanceCompliance));
			constraints.add(new DistanceConstraint(tet.particles[3], tet.particles[0], BodyUtils.getDistance(tet.particles[3], tet.particles[0]), distanceCompliance));

			final double restVolume = BodyUtils.getTetVolume(tet.particles[0], tet.particles[1], tet.particles[2], tet.particles[3]);
			final double pInvMass = restVolume > 0 ? 1 / (restVolume / 4d) : 0;

			for (int i = 0; i < 4; i++) {
				tet.particles[i].setInvMass(tet.particles[i].getInvMass() + pInvMass);
			}

			constraints.add(new TetrahedralVolumeConstraint(tet.particles[0], tet.particles[1], tet.particles[2], tet.particles[3], restVolume, volumeCompliance));
		}

		this.vertices = allVertices.toArray(new Vector3d[0]);
		this.surfaceIndices = surfaceIndicesFixed;
		this.particles = particles;
		this.constraints = constraints;

		updateBuffers();
	}

	public void updateBuffers() {
		final long time0 = System.nanoTime();
		final Int2ObjectMap<Vector3f> map = new Int2ObjectOpenHashMap<>();
		for (int i = 0; i < surfaceIndices.length; i++) {
			map.putIfAbsent(i, new Vector3f());
		}

		for (int i = 0; i < surfaceIndices.length / 3; i++) {
			final Vector3d v1 = particles[surfaceIndices[i * 3]].getPosition();
			final Vector3d v2 = particles[surfaceIndices[i * 3 + 1]].getPosition();
			final Vector3d v3 = particles[surfaceIndices[i * 3 + 2]].getPosition();

			final Vector3d edge2 = v3.sub(v1, new Vector3d());
			final Vector3f normal = v2.sub(v1, new Vector3d()).cross(edge2).normalize().get(new Vector3f());

			map.get(surfaceIndices[i * 3]).add(normal);
			map.get(surfaceIndices[i * 3 + 1]).add(normal);
			map.get(surfaceIndices[i * 3 + 2]).add(normal);
		}

		map.values().forEach(Vector3f::normalize);

		map.defaultReturnValue(new Vector3f(0, 1, 0));
		final List<SceneModelVertex> vertexList = new ObjectArrayList<>();
		for (int i = 0; i < particles.length; i++) {
			vertexList.add(new SceneModelVertex(particles[i].getPosition().get(new Vector3f()), color, map.get(i)));
		}
//		log.info("Construct vertices: {}ms", (System.nanoTime() - time0) / 1_000_000d);
		final long time1 = System.nanoTime();

		if (vertexBuffer != null) {
			vertexBuffer.registerToDisposal();
		}
		if (indexBuffer != null) {
			indexBuffer.registerToDisposal();
		}

		vertexBuffer = Vulkan.createVertexBuffer(vertexList);
		indexCount = getSurfaceIndices().length;
		indexBuffer = Vulkan.createIndexBuffer(getSurfaceIndices());
//		log.info("Create buffers: {}ms", (System.nanoTime() - time1) / 1_000_000d);
	}

	@AllArgsConstructor
	public static class Tetrahedron {
		public final Particle[] particles;
	}
}
