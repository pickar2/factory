package xyz.sathro.factory.test.xpbd;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
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
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.VulkanBuffer;

import java.util.List;
import java.util.Objects;

@Log4j2
public class MeshedBody implements IMesh {
	private static final Vector3f color = new Vector3f(0.9f, 0.6f, 0.3f);

	@Getter private final Particle[] particles;
	@Getter private final List<Constraint> constraints;
	@Getter private final Tetrahedron[] tetrahedra;

	@Getter private final List<AttachedVertex> modelVertices;
	@Getter private final IntList modelIndices;

	//	private final List<Vector3d> surfacePositions;
	private final Vector3d[] surfacePositions;
	private final Vector3f[] normalMap;

	@Getter private final VulkanBuffer indexBuffer;
	@Getter private final int indexCount;
	@Getter private VulkanBuffer vertexBuffer;

	public MeshedBody(List<Vector3d> tetVertices, IntList tetIndices, List<AttachedVertex> modelVertices, IntList modelIndices) {
		particles = new Particle[tetVertices.size()];
		for (int i = 0; i < particles.length; i++) {
			particles[i] = new Particle(tetVertices.get(i));
			particles[i].setIndex(i);
		}

		tetrahedra = new Tetrahedron[tetIndices.size() / 4];
		for (int i = 0; i < tetrahedra.length; i++) {
			tetrahedra[i] = new Tetrahedron(new Particle[] {
					particles[tetIndices.getInt(i * 4)],
					particles[tetIndices.getInt(i * 4 + 1)],
					particles[tetIndices.getInt(i * 4 + 2)],
					particles[tetIndices.getInt(i * 4 + 3)]
			});
		}

//		final List<Tetrahedron> tetrahedronList = new ObjectArrayList<>(tets);
//
//		final List<Tetrahedron[]> hexahedronList = new ObjectArrayList<>();
//		final Set<Tetrahedron> used = new ObjectOpenHashSet<>();
//
//		for (Tetrahedron tetrahedron : tetrahedronList) {
//			if (used.contains(tetrahedron)) { continue; }
//			final Tetrahedron[] array = new Tetrahedron[4];
//			array[0] = tetrahedron;
//			int index = 1;
//			for (Tetrahedron testTet : tetrahedronList) {
//				if (index == 4) { break; }
//				if (tetrahedron == testTet) { continue; }
//				if (used.contains(testTet)) { continue; }
//
//				int count = 0;
//				for (Particle particle : tetrahedron.particles) {
//					for (Particle firstParticle : testTet.particles) {
//						if (firstParticle.getIndex() == particle.getIndex()) {
//							count++;
//						}
//					}
//				}
//				if (count == 3) {
//					array[index++] = testTet;
//				}
//			}
//			if (index == 4) {
//				used.addAll(List.of(array));
//				hexahedronList.add(array);
//			}
//		}
//
//		log.info("Found {} hexahedra", hexahedronList.size());
//		log.info("Used {} tets, {} left", used.size(), tetrahedronList.size() - used.size());
//
//		tetrahedra = tetrahedronList.stream().filter(t -> !used.contains(t)).toArray(Tetrahedron[]::new);
//
//		System.out.println(tetrahedra.length);

		final double distanceCompliance = 0.002;
		final double volumeCompliance = 0.000001;
		constraints = new ObjectArrayList<>(tetrahedra.length * 3);

		final IntSet pairs = new IntOpenHashSet();

		for (Tetrahedron tet : tetrahedra) {
			for (int i = 0; i < 3; i++) {
				final Particle p1 = tet.particles[0];
				final Particle p2 = tet.particles[i + 1];
				if (!pairs.contains(Objects.hash(p1, p2))) {
					constraints.add(new DistanceConstraint(p1, p2, BodyUtils.getDistance(p1, p2), distanceCompliance));
					pairs.add(Objects.hash(p1, p2));
				}

				final Particle p3 = tet.particles[i + 1];
				final Particle p4 = tet.particles[(i + 2) % 4];
				if (!pairs.contains(Objects.hash(p3, p4))) {
					constraints.add(new DistanceConstraint(p3, p4, BodyUtils.getDistance(p3, p4), distanceCompliance));
					pairs.add(Objects.hash(p3, p4));
				}
			}

			final double restVolume = BodyUtils.getTetVolume(tet.particles[0], tet.particles[1], tet.particles[2], tet.particles[3]);
			final double pInvMass = restVolume > 0 ? 1 / (restVolume / 4d) : 0;

			for (int i = 0; i < 4; i++) {
				tet.particles[i].setInvMass(tet.particles[i].getInvMass() + pInvMass);
			}

			constraints.add(new TetrahedralVolumeConstraint(tet.particles[0], tet.particles[1], tet.particles[2], tet.particles[3], restVolume, volumeCompliance));
		}

		this.modelVertices = modelVertices;
		this.modelIndices = modelIndices;
		indexCount = modelIndices.size();
		indexBuffer = Vulkan.createIndexBuffer(modelIndices.toIntArray());

		surfacePositions = new Vector3d[modelVertices.size()];
		for (int i = 0; i < modelVertices.size(); i++) {
			surfacePositions[i] = new Vector3d();
		}

		normalMap = new Vector3f[modelIndices.size()];

		for (int i = 0; i < modelIndices.size(); i++) {
			normalMap[i] = new Vector3f();
		}

//		updateBuffers();
	}

	@Override
	public RayIntersectionResult intersect(Vector3d origin, Vector3d dir) {
		// assuming surface positions are not being modified right now
		for (int i = 0; i < modelIndices.size(); i += 3) {
			final Vector3d v0 = surfacePositions[modelIndices.getInt(i)];
			final Vector3d v1 = surfacePositions[modelIndices.getInt(i + 1)];
			final Vector3d v2 = surfacePositions[modelIndices.getInt(i + 2)];

			final Vector3d v0v1 = v1.sub(v0, new Vector3d());
			final Vector3d v0v2 = v2.sub(v0, new Vector3d());
			final Vector3d pvec = dir.cross(v0v2, new Vector3d());

			final double det = v0v1.dot(pvec);
			if (det < 0.001) { continue; }

			final double invDet = 1 / det;
			final Vector3d tvec = origin.sub(v0, new Vector3d());

			final double u = tvec.dot(pvec) * invDet;
			if (u < 0 || u > 1) { continue; }

			final Vector3d qvec = tvec.cross(v0v1, new Vector3d());
			final double v = dir.dot(qvec) * invDet;
			if (v < 0 || u + v > 1) { continue; }

			return new RayIntersectionResult(true, v0v2.dot(qvec) * invDet);
		}

		return RayIntersectionResult.notIntersected();
	}

	@Override
	public void updateBuffers() {
////		long time = System.nanoTime();
//		// get vertex positions
//		for (int i = 0; i < modelVertices.size(); i++) {
//			AttachedVertex vertex = modelVertices.get(i);
//			final Tetrahedron tet = tetrahedra[vertex.tetIndex];
//
//			final Vector3d vec = surfacePositions[i];
//
//			vec.zero();
//			vec.fma(vertex.baryX, tet.particles[0].getPosition());
//			vec.fma(vertex.baryY, tet.particles[1].getPosition());
//			vec.fma(vertex.baryZ, tet.particles[2].getPosition());
//			vec.fma(1 - vertex.baryX - vertex.baryY - vertex.baryZ, tet.particles[3].getPosition());
//		}
////		log.info("Barycentric coordinates: {}ms", Maths.timeToDeltaMs(time, 2));
////		time = System.nanoTime();
//
//		// generate normals
//		for (int i = 0; i < modelIndices.size(); i++) {
//			normalMap[i].zero();
//		}
//
//		int index1, index2, index3;
//		final Vector3d temp1 = new Vector3d();
//		final Vector3d temp2 = new Vector3d();
//		final Vector3f temp3 = new Vector3f();
//		for (int i = 0; i < modelIndices.size(); i += 3) {
//			index1 = modelIndices.getInt(i);
//			index2 = modelIndices.getInt(i + 1);
//			index3 = modelIndices.getInt(i + 2);
//
//			final Vector3d v1 = surfacePositions[index1];
//			final Vector3d v2 = surfacePositions[index2];
//			final Vector3d v3 = surfacePositions[index3];
//
//			final Vector3d edge2 = v3.sub(v1, temp1);
//			final Vector3f normal = v2.sub(v1, temp2).cross(edge2).normalize().get(temp3);
//
//			normalMap[index1].add(normal);
//			normalMap[index2].add(normal);
//			normalMap[index3].add(normal);
//		}
//
//		for (Vector3f vector3f : normalMap) {
//			vector3f.normalize();
//		}
//
////		log.info("Generate normals: {}ms", Maths.timeToDeltaMs(time, 2));
////		time = System.nanoTime();
//
//		// create vulkan vertices
//		final List<SceneModelVertex> vertexList = new ObjectArrayList<>();
//		for (int i = 0; i < surfacePositions.length; i++) {
//			vertexList.add(new SceneModelVertex(surfacePositions[i].get(new Vector3f()), color, normalMap[i]));
//		}
//
//		// dispose and create vulkan buffers
//		if (vertexBuffer != null) { vertexBuffer.registerToDisposal(); }
//
//		vertexBuffer = Vulkan.createVertexBuffer(vertexList);
////		log.info("Vertex buffer: {}ms", Maths.timeToDeltaMs(time, 2));
	}

	@Override
	public void dispose() {
		indexBuffer.dispose();
	}

	@AllArgsConstructor
	public static class AttachedVertex {
		final int tetIndex;
		final double baryX, baryY, baryZ;
	}
}
