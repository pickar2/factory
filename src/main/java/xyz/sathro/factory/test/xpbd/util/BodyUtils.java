package xyz.sathro.factory.test.xpbd.util;

import org.joml.Vector3d;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.body.PhysicsBody;

import static xyz.sathro.factory.test.xpbd.constraint.TetrahedralVolumeConstraint.ONE_OVER_SIX;

public class BodyUtils {
//	public static Vector3d getDistanceVector(PhysicsBody body1, PhysicsBody body2) {
//		return getDistanceVector(body1, body2, new Vector3d());
//	}
//
//	public static Vector3d getDistanceVector(PhysicsBody body1, PhysicsBody body2, Vector3d out) {
//		return out.set(body2.getPosition()).sub(body1.getPosition());
//	}

	public static double getDistance(PhysicsBody body1, PhysicsBody body2) {
		return body1.getPosition().distance(body2.getPosition());
	}

	public static double getDistanceSquared(PhysicsBody body1, PhysicsBody body2) {
		return body1.getPosition().distanceSquared(body2.getPosition());
	}

	public static double getTetVolume(Vector3d pos0, Vector3d pos1, Vector3d pos2, Vector3d pos3) {
		final Vector3d diff0 = pos1.sub(pos0, new Vector3d());
		final Vector3d diff1 = pos2.sub(pos0, new Vector3d());
		final Vector3d diff2 = pos3.sub(pos0, new Vector3d());

		return diff0.cross(diff1).dot(diff2) * ONE_OVER_SIX;
	}

	public static double getTetVolume(Particle p0, Particle p1, Particle p2, Particle p3) {
		final Vector3d diff0 = p1.getPosition().sub(p0.getPosition(), new Vector3d());
		final Vector3d diff1 = p2.getPosition().sub(p0.getPosition(), new Vector3d());
		final Vector3d diff2 = p3.getPosition().sub(p0.getPosition(), new Vector3d());

		return diff0.cross(diff1).dot(diff2) * ONE_OVER_SIX;
	}
}
