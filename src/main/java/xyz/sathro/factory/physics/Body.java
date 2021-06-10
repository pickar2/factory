package xyz.sathro.factory.physics;

import lombok.Getter;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import xyz.sathro.factory.collision.AABB;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.vulkan.models.Mesh;

public class Body implements IPhysicsEntity {
	private static final double MAX_ROTATION_PER_SUBSTEP = 0.5;

	@Getter public final Pose pose;
	@Getter public final Pose prevPose;
	public final Pose origPose;

	public final Vector3d velocity = new Vector3d(0);
	public final Vector3d angularVelocity = new Vector3d(0);
	public final Vector3d invInertia = new Vector3d(1);
	public final Mesh mesh;
	public double invMass = 1;

	public Vector3d size = new Vector3d(1);

	public Body(Pose pose, Mesh mesh) {
		this.pose = pose.clone();
		this.prevPose = pose.clone();
		this.origPose = pose.clone();

		this.mesh = mesh;
	}

	public Matrix3d getInertiaTensorInverseW() {
		final Matrix3d inertia = new Matrix3d();
		if (invMass != 0) {
			final Matrix3d rotMatrix = new Matrix3d().rotation(pose.rotation);

			inertia.set(rotMatrix).mul(Maths.diagonalFromVector(invInertia)).mul(rotMatrix.transpose());
		}

		return inertia;
	}

	public void setBox(Vector3d size, double density) {
		this.size.set(size);
		double mass = size.x * size.y * size.z * density;
		this.invMass = 1 / mass;
		mass /= 12;
		this.invInertia.set(1.0 / (size.y * size.y + size.z * size.z) / mass,
		                    1.0 / (size.z * size.z + size.x * size.x) / mass,
		                    1.0 / (size.x * size.x + size.y * size.y) / mass);
	}

	public void setBox(Vector3d size) {
		setBox(size, 1);
	}

	public void applyRotation(Vector3d rot, double scale) {
		final double phi = rot.length();
		if (phi * scale > MAX_ROTATION_PER_SUBSTEP) {
			scale = MAX_ROTATION_PER_SUBSTEP / phi;
		}

		final Quaterniond dq = new Quaterniond(rot.x * scale, rot.y * scale, rot.z * scale, 0).mul(pose.rotation);

		pose.rotation.set(pose.rotation.x + 0.5 * dq.x, pose.rotation.y + 0.5 * dq.y, pose.rotation.z + 0.5 * dq.z, pose.rotation.w + 0.5 * dq.w);
		pose.rotation.normalize();
	}

	public void applyRotation(Vector3d rot) {
		applyRotation(rot, 1);
	}

	public void integrate(double dt, Vector3d gravity) {
		prevPose.set(pose);
		velocity.fma(dt, gravity);
		pose.position.fma(dt, velocity);
		applyRotation(angularVelocity, dt);
	}

	public void update(double invDt) {
		velocity.set(pose.position).sub(prevPose.position).mul(invDt);
		final Quaterniond dq = new Quaterniond(prevPose.rotation).conjugate().premul(pose.rotation);
		angularVelocity.set(dq.x * 2.0 * invDt, dq.y * 2.0 * invDt, dq.z * 2.0 * invDt);
		if (dq.w < 0) {
			angularVelocity.mul(-1);
		}
	}

	public Vector3d getVelocityAt(Vector3d pos) {
		final Vector3d velocity = new Vector3d(pos).sub(pose.position);
		velocity.cross(angularVelocity);
		this.velocity.sub(velocity, velocity);

		return velocity;
	}

	public double getInverseMass(Vector3d normal, Vector3d pos) {
		if (pos == null) {
			return getInverseMass(normal);
		}
		final Vector3d n = pose.invRotateVector(new Vector3d(pos).sub(pose.position).cross(normal));

		return n.x * n.x * invInertia.x +
		       n.y * n.y * invInertia.y +
		       n.z * n.z * invInertia.z +
		       invMass;
	}

	public double getInverseMass(Vector3d normal) {
		final Vector3d n = pose.invRotateVector(new Vector3d(normal));

		return n.x * n.x * invInertia.x +
		       n.y * n.y * invInertia.y +
		       n.z * n.z * invInertia.z;
	}

	public void applyCorrection(Vector3d corr, Vector3d pos, boolean velocityLevel) {
		final Vector3d dq;
		if (pos == null) {
			dq = new Vector3d(corr);
		} else {
			if (velocityLevel) {
				velocity.fma(invMass, corr);
			} else {
				pose.position.fma(invMass, corr);
			}
			dq = new Vector3d(pos).sub(pose.position).cross(corr);
		}

		pose.invRotateVector(dq);
		dq.mul(invInertia);
		pose.rotateVector(dq);

		if (velocityLevel) {
			angularVelocity.add(dq);
		} else {
			applyRotation(dq);
		}
	}

	@Override
	public AABB getAABB() {
		return new AABB(new Vector3d(size).mul(-0.5), new Vector3d(size).mul(0.5));
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean isMovable() {
		return true;
	}

	@Override
	public boolean movedLastTick() {
		return true;
	}

	@Override
	public String toString() {
		return "Body{" +
		       "pose=" + pose +
		       ", size=" + size +
		       '}';
	}
}
