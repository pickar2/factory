package xyz.sathro.factory.physics.constraints.joints;

import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.Pose;
import xyz.sathro.factory.physics.constraints.Constraint;

import static xyz.sathro.factory.physics.XPBD.*;

public class SphericalJoint extends Constraint {
	public SphericalJoint(Body body0, Body body1, Pose localPose0, Pose localPose1) {
		super(body0, body1, localPose0, localPose1);
	}

	@Override
	public void solveOrientation(double dt) {
		// swing limits
		if (this.hasSwingLimits) {
			this.updateGlobalPoses();
			Vector3d a0 = getQuatAxis0(this.globalPose0.rotation);
			Vector3d a1 = getQuatAxis0(this.globalPose1.rotation);
			Vector3d n = new Vector3d();
			a0.cross(a1, n);
			n.normalize();
			limitAngle(this.body0, this.body1, n, a0, a1, this.minSwingAngle, this.maxSwingAngle, this.swingLimitsCompliance, dt);
		}
		// twist limits
		if (this.hasTwistLimits) {
			this.updateGlobalPoses();
			Vector3d n0 = getQuatAxis0(this.globalPose0.rotation);
			Vector3d n1 = getQuatAxis0(this.globalPose1.rotation);
			Vector3d n = new Vector3d();
			n0.add(n1, n);
			n.normalize();
			Vector3d a0 = getQuatAxis1(this.globalPose0.rotation);
			a0.add(new Vector3d(n).mul(-n.dot(a0)));
			a0.normalize();
			Vector3d a1 = getQuatAxis1(this.globalPose1.rotation);
			a1.add(new Vector3d(n).mul(-n.dot(a1)));
			a1.normalize();

			// handling gimbal lock problem
			double maxCorr = n0.dot(n1) > -0.5 ? (2.0f * Math.PI) : 1.0f * dt;

			limitAngle(this.body0, this.body1, n, a0, a1, this.minTwistAngle, this.maxTwistAngle, this.twistLimitCompliance, dt, maxCorr);
		}
	}
}
