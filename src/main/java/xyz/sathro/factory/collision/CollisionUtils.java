package xyz.sathro.factory.collision;

import org.joml.Vector3d;
import org.joml.Vector3i;
import xyz.sathro.factory.physics.IPhysicsEntity;

public class CollisionUtils {
	private CollisionUtils() { }

	public static boolean isEntityFullyInside(IPhysicsEntity entity, Vector3i minPos, Vector3i maxPos) {
		final Vector3d pos = entity.getPose().position;
		final AABB aabb = entity.getAABB();

		return pos.x + aabb.minPos.x > minPos.x &&
		       pos.y + aabb.minPos.y > minPos.y &&
		       pos.z + aabb.minPos.z > minPos.z &&
		       pos.x + aabb.maxPos.x < maxPos.x &&
		       pos.y + aabb.maxPos.y < maxPos.y &&
		       pos.z + aabb.maxPos.z < maxPos.z;
	}
}
