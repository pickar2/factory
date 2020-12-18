package xyz.sathro.factory.physics;

import lombok.Getter;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import xyz.sathro.factory.collision.AABB;
import xyz.sathro.factory.vulkan.models.Mesh;

public class Body implements IPhysicsEntity {
	private static final double MAX_ROTATION_PER_SUBSTEP = 0.5f;

	@Getter public final Pose pose;
	@Getter public final Pose prevPose;
	public final Pose origPose;

	public final Vector3d velocity = new Vector3d(0);
	public final Vector3d omega = new Vector3d(0);
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

		final Quaterniond dq = new Quaterniond(rot.x * scale, rot.y * scale, rot.z * scale, 0);
		dq.mul(pose.rotation);

		pose.rotation.set(pose.rotation.x + 0.5 * dq.x, pose.rotation.y + 0.5 * dq.y, pose.rotation.z + 0.5 * dq.z, pose.rotation.w + 0.5 * dq.w);
		pose.rotation.normalize();
	}

	public void applyRotation(Vector3d rot) {
		applyRotation(rot, 1);
	}

	public void integrate(double dt, Vector3d gravity) {
		prevPose.set(pose);
		velocity.add(new Vector3d(gravity).mul(dt));
		pose.position.add(new Vector3d(velocity).mul(dt));
		applyRotation(omega, dt);
	}

	public void update(double dt) {
		velocity.set(pose.position).sub(prevPose.position);
		velocity.mul(1 / dt);
		final Quaterniond dq = new Quaterniond();
		dq.set(new Quaterniond(pose.rotation).mul(new Quaterniond(prevPose.rotation).conjugate()));
		omega.set(dq.x * 2.0 / dt, dq.y * 2.0 / dt, dq.z * 2.0 / dt);
		if (dq.w < 0) {
			omega.mul(-1);
		}

//         this.omega.mul(1.0f - 1.0f * dt);
//         this.velocity.mul(1.0f - 1.0f * dt);
//
//        this.mesh.position.copy(this.pose.p);
//        this.mesh.quaternion.copy(this.pose.q);
	}

	public Vector3d getVelocityAt(Vector3d pos) {
		final Vector3d velocity = new Vector3d();
		pos.sub(pose.position, velocity);
		velocity.cross(omega);
		this.velocity.sub(velocity, velocity);

		return velocity;
	}

	public double getInverseMass(Vector3d normal, Vector3d pos) {
		if (pos == null) {
			return getInverseMass(normal); // TODO: remove this ?
		}
		final Vector3d n = new Vector3d();

		pos.sub(pose.position, n);
		n.cross(normal);
		pose.invRotate(n);

		return n.x * n.x * invInertia.x +
		       n.y * n.y * invInertia.y +
		       n.z * n.z * invInertia.z + invMass;
	}

	public double getInverseMass(Vector3d normal) {
		final Vector3d n = new Vector3d(normal);

		pose.invRotate(n);

		return n.x * n.x * invInertia.x +
		       n.y * n.y * invInertia.y +
		       n.z * n.z * invInertia.z;
	}

	public void applyCorrection(Vector3d corr, Vector3d pos, boolean velocityLevel) {
		Vector3d dq = new Vector3d();
		if (pos == null) {
			dq = new Vector3d(corr);
		} else {
			if (velocityLevel) {
				velocity.add(new Vector3d(corr).mul(invMass));
			} else {
				pose.position.add(new Vector3d(corr).mul(invMass));
			}
			pos.sub(pose.position, dq);
			dq.cross(corr);
		}

		pose.invRotate(dq);
		dq.set(this.invInertia.x * dq.x, this.invInertia.y * dq.y, this.invInertia.z * dq.z);
		pose.rotate(dq);

		if (velocityLevel) {
			omega.add(dq);
		} else {
			applyRotation(dq);
		}
	}

	@Override
	public AABB getAABB() {
		return new AABB(new Vector3d(size).div(2).negate(), new Vector3d(size).div(2));
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
