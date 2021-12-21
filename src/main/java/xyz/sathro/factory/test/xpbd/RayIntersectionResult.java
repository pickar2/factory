package xyz.sathro.factory.test.xpbd;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RayIntersectionResult {
	private static final RayIntersectionResult notIntersected = new RayIntersectionResult(false, 0);

	public final boolean intersected;
	public final double distance;

	public static RayIntersectionResult notIntersected() {
		return notIntersected;
	}
}
