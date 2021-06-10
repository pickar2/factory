package xyz.sathro.factory.test;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RelativeBlockPos {
	byte x, y, z;

	public RelativeBlockPos(int x, int y, int z) {
		this.x = (byte) x;
		this.y = (byte) y;
		this.z = (byte) z;
	}
}
