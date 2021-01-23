package xyz.sathro.factory.physics.constraints.contact;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.constraints.Constraint;
import xyz.sathro.factory.util.Maths;

public class RigidBodyContactConstraint extends Constraint {
	private final Vector3d contactPoint0;
	private final Vector3d contactPoint1;
	private final Vector3d normal;
	private final double stiffness;
	private final double friction;
	private final Vector3d tangentDirection;
	private final double normalT_K_normal_inv;
	private final double maxImpulse;
	private final double goalVelocity;
	private double sumOfImpulses;

	public RigidBodyContactConstraint(Body body0, Body body1, Vector3d contactPoint0, Vector3d contactPoint1, Vector3d normal, double restitution, double stiffness, double friction) {
		super(body0, body1);
		this.contactPoint0 = contactPoint0;
		this.contactPoint1 = contactPoint1;
		this.normal = normal;

		this.stiffness = stiffness;
		this.friction = friction;

		this.sumOfImpulses = 0;

		final Vector3d u_rel = calcURel();
		final double u_rel_n = normal.dot(u_rel);

		// tangent direction
		Vector3d t = new Vector3d(u_rel).sub(new Vector3d(normal).mul(u_rel_n));
		double tl2 = t.lengthSquared();
		if (tl2 > 1.0e-6) {
			t.div(Math.sqrt(tl2));
		}
		tangentDirection = t;

		// determine K matrix
		Matrix3d rot0 = new Matrix3d().set(body0.pose.rotation);
		Matrix3d rot1 = new Matrix3d().set(body1.pose.rotation);

		Matrix3d invInertiaW0 = new Matrix3d(rot0).mul(Maths.diagonalFromVector(body0.invInertia)).mul(new Matrix3d(rot0).transpose());
		Matrix3d invInertiaW1 = new Matrix3d(rot1).mul(Maths.diagonalFromVector(body1.invInertia)).mul(new Matrix3d(rot1).transpose());

		Matrix3d K = computeMatrixK(contactPoint0, body0.invMass, body0.pose.position, invInertiaW0).add(computeMatrixK(contactPoint1, body1.invMass, body1.pose.position, invInertiaW1));

		this.normalT_K_normal_inv = 1 / normal.dot(new Vector3d(normal).mul(K));
		this.maxImpulse = 1 / t.dot(new Vector3d(t).mul(K)) * u_rel.dot(t);
		if (u_rel_n < 0) {
			this.goalVelocity = -restitution * u_rel_n;
		} else {
			this.goalVelocity = 0;
		}
	}

	public static Matrix3d computeMatrixK(Vector3d connector, double invMass, Vector3d x, Matrix3d invInertiaW) {
		final Matrix3d ret = new Matrix3d().zero();

		if (invMass != 0) {
			final Vector3d v = new Vector3d(connector).sub(x);

			ret.m00 = v.z * v.z * invInertiaW.m11 - v.y * v.z * (invInertiaW.m12 + invInertiaW.m12) + v.y * v.y * invInertiaW.m22 + invMass;
			ret.m01 = -v.z * v.z * invInertiaW.m01 + v.x * v.z * invInertiaW.m12 + v.y * v.z * invInertiaW.m02 - v.x * v.y * invInertiaW.m22;
			ret.m02 = v.y * v.z * invInertiaW.m01 - v.x * v.z * invInertiaW.m11 - v.y * v.y * invInertiaW.m02 + v.x * v.y * invInertiaW.m12;
			ret.m10 = ret.m01;
			ret.m11 = v.z * v.z * invInertiaW.m00 - v.x * v.z * (invInertiaW.m02 + invInertiaW.m02) + v.x * v.x * invInertiaW.m22 + invMass;
			ret.m12 = -v.y * v.z * invInertiaW.m00 + v.x * v.z * invInertiaW.m01 + v.x * v.y * invInertiaW.m02 - v.x * v.x * invInertiaW.m12;
			ret.m20 = ret.m02;
			ret.m21 = ret.m12;
			ret.m22 = v.y * v.y * invInertiaW.m00 - v.x * v.y * (invInertiaW.m01 + invInertiaW.m01) + v.x * v.x * invInertiaW.m11 + invMass;
		}

		return ret;
	}

	@Override
	public void solveVel(double dt) {
		if (body0.invMass == 0 && body1.invMass == 0) { return; }

		final double penetrationDepth = normal.dot(new Vector3d(contactPoint0).sub(contactPoint1));

		final Vector3d u_rel = calcURel();
		final double u_rel_n = normal.dot(u_rel);
		final double delta_u_rel_n = goalVelocity - u_rel_n;
		double correctionMagnitude = normalT_K_normal_inv * delta_u_rel_n;

		if (correctionMagnitude < -sumOfImpulses) {
			correctionMagnitude = -sumOfImpulses;
		}

		if (penetrationDepth < 0) {
			correctionMagnitude -= stiffness * normalT_K_normal_inv * penetrationDepth;
		}

		final Vector3d p = new Vector3d(normal).mul(correctionMagnitude);
		sumOfImpulses += correctionMagnitude;

		final double pn = p.dot(normal);
		final double frPn = friction * pn;

		if (frPn > maxImpulse) {
			p.sub(new Vector3d(tangentDirection).mul(maxImpulse));
		} else if (frPn < -maxImpulse) {
			p.add(new Vector3d(tangentDirection).mul(maxImpulse));
		} else {
			p.sub(new Vector3d(tangentDirection).mul(frPn));
		}

		if (body0.invMass != 0) {
			final Vector3d r0 = new Vector3d(contactPoint0).sub(body0.pose.position);
			body0.velocity.add(new Vector3d(p).mul(body0.invMass));
			body0.angularVelocity.add(new Vector3d(r0.cross(p).mul(body0.getInertiaTensorInverseW())));
		}

		if (body1.invMass != 0) {
			final Vector3d r1 = new Vector3d(contactPoint1).sub(body1.pose.position);
			body1.velocity.add(new Vector3d(p).mul(-body1.invMass));
			body1.angularVelocity.add(new Vector3d(r1.cross(new Vector3d(p).negate()).mul(body1.getInertiaTensorInverseW())));
		}
	}

	@Override
	public void solvePos(double dt) {

	}

	private Vector3d calcURel() {
		final Vector3d r0 = new Vector3d(contactPoint0).sub(body0.pose.position);
		final Vector3d r1 = new Vector3d(contactPoint1).sub(body1.pose.position);

		final Vector3d u0 = new Vector3d(body0.angularVelocity).cross(r0).add(body0.velocity);
		final Vector3d u1 = new Vector3d(body1.angularVelocity).cross(r1).add(body1.velocity);

		return u0.sub(u1);
	}
}
