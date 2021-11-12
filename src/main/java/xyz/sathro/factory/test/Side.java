package xyz.sathro.factory.test;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Side {
	FRONT(0, 1, 0, 0, +1, 2, true), // z+
	BACK(0, 1, 0, 0, -1, 2, false), // z-
	TOP(0, 2, 0, +1, 0, 1, true), // y+
	BOTTOM(0, 2, 0, -1, 0, 1, false), // y-
	RIGHT(2, 1, +1, 0, 0, 0, true), // x+
	LEFT(2, 1, -1, 0, 0, 0, false); // x-

	private static final Side[] opposites = { BACK, FRONT, BOTTOM, TOP, LEFT, RIGHT };

	public final int textureX, textureY;
	public final int x, y, z, d;
	public final boolean isIncrease;

	public Side opposite() {
		return opposites[this.ordinal()];
	}
}
