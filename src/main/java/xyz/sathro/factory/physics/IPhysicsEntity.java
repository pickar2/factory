package xyz.sathro.factory.physics;

import xyz.sathro.factory.collision.AABB;

public interface IPhysicsEntity {
	AABB getAABB();

	Pose getPose();

	Pose getPrevPose();

	boolean isCollidable();

	boolean isMovable();

	boolean movedLastTick();
}
