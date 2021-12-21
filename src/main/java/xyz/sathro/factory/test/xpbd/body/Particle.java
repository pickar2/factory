package xyz.sathro.factory.test.xpbd.body;

import lombok.Getter;
import lombok.ToString;
import org.joml.Vector3d;

@ToString
public class Particle implements PhysicsBody {
	@Getter private final Vector3d velocity = new Vector3d();

	@Getter private final Vector3d position = new Vector3d();
	@Getter private final Vector3d prevPosition = new Vector3d();

	@Getter private double mass = 0.1d;
	@Getter private double invMass = 1 / mass;

	@Getter private int index;

	public Particle(Vector3d position) {
		this.position.set(position);
		this.prevPosition.set(position);
	}

	public void setMass(double mass) {
		this.mass = mass;
		invMass = 1.0 / mass;
	}

	public void setInvMass(double value) {
		this.invMass = value;
		this.mass = 1 / value;
	}

	// TODO: pre multiply external force by dt?
	@Override
	public void integrate(double dt, Vector3d externalForce) {
		prevPosition.set(position);

		velocity.fma(dt, externalForce);
		position.fma(dt, velocity);

		if (position.y < 0) {
			position.set(prevPosition);
			position.y = 0;
		}
	}

	@Override
	public void updateVelocity(double dtInv) {
		velocity.set(position).sub(prevPosition).mul(dtInv);
	}

	public void setIndex(int index) { this.index = index; }
}
