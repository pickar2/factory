package xyz.sathro.factory.test.xpbd;

import lombok.AllArgsConstructor;
import org.joml.Vector3d;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.constraint.Constraint;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.models.VulkanBuffer;

import java.util.List;

public interface IMesh extends IDisposable {
	VulkanBuffer getVertexBuffer();
	VulkanBuffer getIndexBuffer();
	int getIndexCount();

	void updateBuffers();

	Particle[] getParticles();
	List<Constraint> getConstraints();

	RayIntersectionResult intersect(Vector3d origin, Vector3d dir);

	@AllArgsConstructor
	class Tetrahedron {
		public final Particle[] particles;
	}
}
