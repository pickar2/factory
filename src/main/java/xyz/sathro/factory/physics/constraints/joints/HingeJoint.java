package xyz.sathro.factory.physics.constraints.joints;

import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.Pose;
import xyz.sathro.factory.physics.constraints.Constraint;

import static xyz.sathro.factory.physics.XPBD.*;

public class HingeJoint extends Joint {
	public HingeJoint(Body body0, Body body1, Pose localPose0, Pose localPose1) {
		super(body0, body1, localPose0, localPose1);
	}

	@Override
	public void solveOrientation(double dt) {
		// align axes
		Vector3d a0 = getQuatAxis0(this.globalPose0.rotation);
//		Vector3d b0 = getQuatAxis1(this.globalPose0.q);
//		Vector3d c0 = getQuatAxis2(this.globalPose0.q);
		Vector3d a1 = getQuatAxis0(this.globalPose1.rotation);
		a0.cross(a1);
		applyBodyPairCorrection(this.body0, this.body1, a0, 0.0f, dt, null, null, false);

		// limits
		if (this.hasSwingLimits) {
			this.updateGlobalPoses();
			Vector3d n = getQuatAxis0(this.globalPose0.rotation);
			Vector3d b0 = getQuatAxis1(this.globalPose0.rotation);
			Vector3d b1 = getQuatAxis1(this.globalPose1.rotation);

			limitAngle(this.body0, this.body1, n, b0, b1, this.minSwingAngle, this.maxSwingAngle, this.swingLimitsCompliance, dt);
		}
	}
}
