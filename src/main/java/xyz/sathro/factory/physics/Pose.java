package xyz.sathro.factory.physics;

import lombok.ToString;
import org.joml.Quaterniond;
import org.joml.Vector3d;

@ToString
public class Pose {
	public Vector3d position;
	public Quaterniond rotation;

	public Pose() {
		position = new Vector3d(0, 0, 0);
		rotation = new Quaterniond(0, 0, 0, 1);
	}

	public Pose(Vector3d position, Quaterniond rotation) {
		this.position = position;
		this.rotation = rotation;
	}

	public void set(Pose other) {
		position.set(other.position);
		rotation.set(other.rotation);
	}

	public void rotate(Vector3d v) {
		v.rotate(rotation);
	}

	public void invRotate(Vector3d v) {
		Quaterniond inv = new Quaterniond(rotation).conjugate();
		v.rotate(inv);
	}

	public void transform(Vector3d v) {
		v.rotate(rotation);
		v.add(position);
	}

	public void invTransform(Vector3d v) {
		v.sub(position);
		invRotate(v);
	}

	public void transformPose(Pose pose) {
		pose.rotation.set(new Quaterniond(this.rotation).mul(pose.rotation));
		rotate(pose.position);
		pose.position.add(position);
	}

	public Pose clone() {
		return new Pose(new Vector3d(position), new Quaterniond(rotation));
	}
}
