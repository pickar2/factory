package xyz.sathro.factory.test.xpbd.constraint;

import lombok.Getter;
import org.joml.Vector3d;
import xyz.sathro.factory.test.xpbd.body.Particle;
import xyz.sathro.factory.test.xpbd.util.BodyUtils;

import java.util.Arrays;
import java.util.List;

public class TetrahedralVolumeConstraint implements Constraint {
	private static final int[][] idOrder = new int[][] { { 1, 3, 2 },
	                                                     { 0, 2, 3 },
	                                                     { 0, 3, 1 },
	                                                     { 0, 1, 2 } };

	public static final double ONE_OVER_SIX = 1 / 6d;

	private final Vector3d[] grads = new Vector3d[] { new Vector3d(), new Vector3d(), new Vector3d(), new Vector3d() };
	@Getter private final Particle[] particles;

	private final Vector3d temp1 = new Vector3d();
	private final Vector3d temp2 = new Vector3d();
	private final Vector3d temp3 = new Vector3d();

	@Getter private final double restVolume;
	private final double compliance;

	public TetrahedralVolumeConstraint(Particle p1, Particle p2, Particle p3, Particle p4, double restVolume, double compliance) {
		this.particles = new Particle[] { p1, p2, p3, p4 };
		this.restVolume = restVolume;
		this.compliance = compliance;
	}

	public TetrahedralVolumeConstraint(Particle p1, Particle p2, Particle p3, Particle p4, double compliance) {
		this.particles = new Particle[] { p1, p2, p3, p4 };
		this.restVolume = BodyUtils.getTetVolume(p1, p2, p3, p4);
		this.compliance = compliance;
	}

	static boolean printed = false;

	@Override
	public void solvePosition(double dt) {
		double w = 0;
		for (int i = 0; i < 4; i++) {
			final Particle p0 = particles[idOrder[i][0]];
			final Particle p1 = particles[idOrder[i][1]];
			final Particle p2 = particles[idOrder[i][2]];

			p1.getPosition().sub(p0.getPosition(), grads[i]);
			final Vector3d diff2 = p2.getPosition().sub(p0.getPosition(), new Vector3d());

			grads[i].cross(diff2).mul(ONE_OVER_SIX);
//			System.out.println("grads" + i + "=" + grads[i]);

			w += particles[i].getInvMass() * grads[i].lengthSquared();
		}
		if (w == 0) { return; }
//		System.out.println("w=" + w);

		final Vector3d diff0 = particles[1].getPosition().sub(particles[0].getPosition(), temp1);
		final Vector3d diff1 = particles[2].getPosition().sub(particles[0].getPosition(), temp2);
		final Vector3d diff2 = particles[3].getPosition().sub(particles[0].getPosition(), temp3);

		final double currentVolume = diff0.cross(diff1).dot(diff2) * ONE_OVER_SIX;
//		System.out.println("currentVolume=" + currentVolume);
//		System.out.println("restVolume=" + restVolume);

		final double s = (restVolume - currentVolume) / (w + compliance / (dt * dt));
//		if (Math.abs(s) < 0.0000001) { return; }
//		System.out.println("s=" + s);
		if (!printed) {
			printed = true;
			for (Particle particle : particles) {
				System.out.println(particle.getIndex());
			}
		}

		for (int i = 0; i < 4; i++) {
//			final Vector3d change = grads[i].mul(s * particles[i].getInvMass());
//			System.out.println("change" + i + "=" + change);
			particles[i].getPosition().fma(s * particles[i].getInvMass(), grads[i]);
		}
	}

	@Override
	public List<Particle> getConstrainedParticles() {
		return Arrays.asList(particles);
	}
}
