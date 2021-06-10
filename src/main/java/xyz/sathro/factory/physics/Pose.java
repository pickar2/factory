package xyz.sathro.factory.physics;

import lombok.ToString;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
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

	@Contract(mutates = "this")
	public void set(Pose other) {
		position.set(other.position);
		rotation.set(other.rotation);
	}

	@Contract(mutates = "this")
	public @NotNull Pose rotate(@NotNull Quaterniond rotation) {
		rotation.transform(this.position);
		return this;
	}

	@Contract(mutates = "this")
	public @NotNull Pose invRotate(@NotNull Quaterniond rotation) {
		rotation.transformInverse(this.position);
		return this;
	}

	@Contract(mutates = "param")
	public @NotNull Vector3d rotateVector(@NotNull Vector3d vector) {
		rotation.transform(vector);
		return vector;
	}

	@Contract(mutates = "param")
	public @NotNull Vector3d invRotateVector(@NotNull Vector3d vector) {
		rotation.transformInverse(vector);
		return vector;
	}

	/**
	 * Applies other pose's rotation to this rotation, rotates this pose around other rotation, and moves this pose according to other pose.
	 */
	@Contract(mutates = "this")
	public @NotNull Pose transform(@NotNull Pose other) {
		this.rotation.premul(other.rotation);
		this.rotate(other.rotation);
		this.position.add(other.position);

		return this;
	}

	@Override
	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public Pose clone() {
		return new Pose(new Vector3d(position), new Quaterniond(rotation));
	}
}
