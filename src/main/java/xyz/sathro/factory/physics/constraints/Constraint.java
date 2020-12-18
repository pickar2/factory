package xyz.sathro.factory.physics.constraints;

import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.Pose;

import static xyz.sathro.factory.physics.XPBD.applyBodyPairCorrection;

public abstract class Constraint {
	protected final Body body0;
	protected final Body body1;

	protected final Pose localPose0;
	protected final Pose localPose1;
	protected final Pose globalPose0;
	protected final Pose globalPose1;

	public double compliance = 0;
	public double rotDamping = 0;
	public double posDamping = 0;
	protected boolean hasSwingLimits = false;
	protected double minSwingAngle = -2 * Math.PI;
	protected double maxSwingAngle = 2 * Math.PI;
	public double swingLimitsCompliance = 0;
	protected boolean hasTwistLimits = false;
	protected double minTwistAngle = -2 * Math.PI;
	protected double maxTwistAngle = 2 * Math.PI;
	public double twistLimitCompliance = 0;

	public Constraint(Body body0, Body body1, Pose localPose0, Pose localPose1) {
		this.body0 = body0;
		this.body1 = body1;
		this.localPose0 = localPose0.clone();
		this.localPose1 = localPose1.clone();
		this.globalPose0 = localPose0.clone();
		this.globalPose1 = localPose1.clone();
	}

	public void updateGlobalPoses() {
		globalPose0.set(localPose0);
		if (body0 != null) {
			body0.pose.transformPose(globalPose0);
		}
		globalPose1.set(localPose1);
		if (body1 != null) {
			body1.pose.transformPose(globalPose1);
		}
	}

	public void solvePos(double dt) {
		updateGlobalPoses();

		solveOrientation(dt);
		solvePosition(dt);

		updateGlobalPoses();
		final Vector3d corr = new Vector3d();
		globalPose1.position.sub(globalPose0.position, corr);
		applyBodyPairCorrection(this.body0, this.body1, corr, this.compliance, dt, this.globalPose0.position, this.globalPose1.position, false);
	}

	public void solveOrientation(double dt) { }

	public void solvePosition(double dt) { }

	public void solveVel(double dt) {
		if (this.rotDamping > 0.0) {
			Vector3d omega = new Vector3d(0);

			if (this.body0 != null) {
				omega.sub(this.body0.omega);
			}
			if (this.body1 != null) {
				omega.add(this.body1.omega);
			}

			omega.mul(Math.min(1.0f, this.rotDamping * dt));
			applyBodyPairCorrection(this.body0, this.body1, omega, 0.0f, dt, null, null, true);
		}

		if (this.posDamping > 0.0) {
			this.updateGlobalPoses();
			Vector3d vel = new Vector3d(0);

			if (this.body0 != null) {
				vel.sub(this.body0.getVelocityAt(this.globalPose0.position));
			}
			if (this.body1 != null) {
				vel.add(this.body1.getVelocityAt(this.globalPose1.position));
			}

			vel.mul(Math.min(1.0f, this.posDamping * dt));
			applyBodyPairCorrection(this.body0, this.body1, vel, 0.0f, dt, this.globalPose0.position, this.globalPose1.position, true);
			this.updateGlobalPoses();
		}
	}
}
