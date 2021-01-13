package xyz.sathro.factory.physics.constraints.contact;

import org.joml.Vector3d;
import xyz.sathro.factory.physics.Body;
import xyz.sathro.factory.physics.constraints.Constraint;

public class RigidBodyContactConstraint extends Constraint {
	private double stiffness, friction, sumOfImpulses;
	private Vector3d contactPointBody0, contactPointBody1, contactNormalBody1, contactTangent;
	private double nKn_inv, pMax, goalVelocity;

	public RigidBodyContactConstraint(Body body0, Body body1, double stiffness, double friction, double sumOfImpulses) {
		super(body0, body1);
	}

	@Override
	public void solveVel(double dt) {

	}

	@Override
	public void solvePos(double dt) {

	}
}
