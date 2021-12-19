package xyz.sathro.factory.test.xpbd.constraint;

import xyz.sathro.factory.test.xpbd.body.Particle;

import java.util.List;

public interface Constraint {
	default void solvePosition(double dt) { }

	default void solveVelocity(double dt) { }

	List<Particle> getConstrainedParticles();
}
