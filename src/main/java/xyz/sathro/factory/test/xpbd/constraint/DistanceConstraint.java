package xyz.sathro.factory.test.xpbd.constraint;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.joml.Vector3d;
import xyz.sathro.factory.test.xpbd.body.Particle;

import java.util.List;

@AllArgsConstructor
public class DistanceConstraint implements Constraint {
	private final Vector3d distanceVector = new Vector3d();

	@Getter private final Particle body1;
	@Getter private final Particle body2;

	@Getter private double restDistance;
	@Getter private double compliance;

	@Override
	public void solvePosition(double dt) {
		distanceVector.set(body2.getPosition()).sub(body1.getPosition());

		final double distanceDifference = distanceVector.length() - restDistance;

		if (Math.abs(distanceDifference) < 0.001) { return; }

		final double lambda = 1.0 / (body1.getInvMass() + body2.getInvMass() + compliance / (dt * dt));
		final Vector3d normalizedPositionDifference = distanceVector.normalize().mul(lambda * distanceDifference);

		if (body1.getInvMass() != 0) {
			body1.getPosition().fma(body1.getInvMass(), normalizedPositionDifference);
		}

		if (body2.getInvMass() != 0) {
			body2.getPosition().fma(-body2.getInvMass(), normalizedPositionDifference);
		}
	}

	@Override
	public List<Particle> getConstrainedParticles() {
		return List.of(body1, body2);
	}

}
