package xyz.sathro.factory.test.xpbd.body;

import org.joml.Vector3d;

public interface PhysicsBody {
	Vector3d getPosition();
	Vector3d getPrevPosition();

	Vector3d getVelocity();

	double getMass();
	double getInvMass();

	void integrate(double dt, Vector3d externalForce);

	void updateVelocity(double dtInv);

	int getIndex();
	void setIndex(int index);
}
