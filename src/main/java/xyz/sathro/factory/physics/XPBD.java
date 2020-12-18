package xyz.sathro.factory.physics;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import xyz.sathro.factory.physics.constraints.Constraint;

import java.util.List;

public class XPBD {
	public static void applyBodyPairCorrection(Body body0, Body body1, Vector3d corr, double compliance, double dt, Vector3d pos0, Vector3d pos1, boolean velocityLevel) {
		double C = corr.length();
		if (C == 0) { return; }

		Vector3d normal = new Vector3d(corr);
		normal.normalize();

		double w0 = body0 != null ? body0.getInverseMass(normal, pos0) : 0;
		double w1 = body1 != null ? body1.getInverseMass(normal, pos1) : 0;

		double w = w0 + w1;
		if (w == 0) { return; }

		double lambda = C / (w + compliance / dt / dt);
		normal.mul(lambda);

		if (body0 != null) {
			body0.applyCorrection(normal, pos0, velocityLevel);
		}
		if (body1 != null) {
			normal.mul(-1);
			body1.applyCorrection(normal, pos1, velocityLevel);
		}
	}

	public static Vector3d getQuatAxis0(Quaterniond q) {
		double x2 = q.x * 2.0f;
		double w2 = q.w * 2.0f;

		return new Vector3d((q.w * w2) - 1.0f + q.x * x2, (q.z * w2) + q.y * x2, (-q.y * w2) + q.z * x2);
	}

	public static Vector3d getQuatAxis1(Quaterniond q) {
		double y2 = q.y * 2.0f;
		double w2 = q.w * 2.0f;

		return new Vector3d((-q.z * w2) + q.x * y2, (q.w * w2) - 1.0f + q.y * y2, (q.x * w2) + q.z * y2);
	}

	public static Vector3d getQuatAxis2(Quaterniond q) {
		double z2 = q.z * 2.0f;
		double w2 = q.w * 2.0f;

		return new Vector3d((q.y * w2) + q.x * z2, (-q.x * w2) + q.y * z2, (q.w * w2) - 1.0f + q.z * z2);
	}

	public static void limitAngle(Body body0, Body body1, Vector3d n, Vector3d a, Vector3d b, double minAngle, double maxAngle, double compliance, double dt, double maxCorr) {
		Vector3d c = new Vector3d();
		a.cross(b, c);

		double phi = Math.asin(c.dot(n));
		if (a.dot(b) < 0) {
			phi = Math.PI - phi;
		}
		if (phi > Math.PI) {
			phi -= 2 * Math.PI;
		}
		if (phi < -Math.PI) {
			phi += 2 * Math.PI;
		}

		if (phi < minAngle || phi > maxAngle) {
			phi = Math.min(Math.max(minAngle, phi), maxAngle);

			Quaterniond q = new Quaterniond();
			q.setAngleAxis(phi, n.x, n.y, n.z);

			Vector3d omega = new Vector3d(a);
			omega.rotate(q);
			omega.cross(b);

			phi = omega.length();
			if (phi > maxCorr) {
				omega.mul(maxCorr / phi);
			}

			applyBodyPairCorrection(body0, body1, omega, compliance, dt, null, null, false);
		}
	}

	public static void limitAngle(Body body0, Body body1, Vector3d n, Vector3d a, Vector3d b, double minAngle, double maxAngle, double compliance, double dt) {
		limitAngle(body0, body1, n, a, b, minAngle, maxAngle, compliance, dt, Math.PI);
	}

	public static void simulate(List<Body> bodies, List<Constraint> constraints, double timeStep, int numSubsteps, Vector3d gravity) {
		double dt = timeStep / numSubsteps;

		for (int i = 0; i < numSubsteps; i++) {
			for (Body body : bodies) {
				body.integrate(dt, gravity);
			}

			for (Constraint constraint : constraints) {
				constraint.solvePos(dt);
			}

			for (Body body : bodies) {
				body.update(dt);
			}

			for (Constraint constraint : constraints) {
				constraint.solveVel(dt);
			}
		}
	}
}
