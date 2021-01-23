package xyz.sathro.factory.physics.constraints;

import xyz.sathro.factory.physics.Body;

public abstract class Constraint {
	protected final Body body0;
	protected final Body body1;

	public double compliance = 0;

	public Constraint(Body body0, Body body1) {
		this.body0 = body0;
		this.body1 = body1;
	}

	public abstract void solveVel(double dt);

	public abstract void solvePos(double dt);
}
