package xyz.sathro.factory.util;

public enum Side {
	X_PLUS(1, 0, 0, 0),
	X_MINUS(-1, 0, 0, 0),
	Y_PLUS(0, 1, 0, 1),
	Y_MINUS(0, -1, 0, 1),
	Z_PLUS(0, 0, 1, 2),
	Z_MINUS(0, 0, -1, 2);

	public final int x, y, z, d;

	Side(int x, int y, int z, int d) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.d = d;
	}
}
