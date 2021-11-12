package xyz.sathro.factory.physics.constraints.joints;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.Pose;
import xyz.sathro.factory.physics.XPBD;

public class FixedJoint extends Joint {
	public FixedJoint(Body body0, Body body1, Pose localPose0, Pose localPose1) {
		super(body0, body1, localPose0, localPose1);
	}

	@Override
	public void solveOrientation(double dt) {
		Quaterniond q = globalPose0.rotation;
		q.conjugate();
		q.premul(globalPose1.rotation);
		Vector3d omega = new Vector3d();
		omega.set(2.0 * q.x, 2.0 * q.y, 2.0 * q.z);
		if (q.w < 0.0) {
			omega.mul(-1.0f);
		}
		XPBD.applyBodyPairCorrection(body0, body1, omega, this.compliance, dt, null, null, false);
	}
}
