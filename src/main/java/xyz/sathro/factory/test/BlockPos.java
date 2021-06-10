package xyz.sathro.factory.test;

import xyz.sathro.factory.util.Maths;

public class BlockPos {
	public static final int CHUNK_SIZE = 16;
	public static final int CHUNK_SIZE_LOG2 = Maths.log2(CHUNK_SIZE);

	private static final BlockPos[] blockPosArray = new BlockPos[CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE];

	public final int x, y, z;

	static {
		int index = 0;
		for (int i = 0; i < CHUNK_SIZE; i++) {
			for (int j = 0; j < CHUNK_SIZE; j++) {
				for (int k = 0; k < CHUNK_SIZE; k++) {
					blockPosArray[index++] = new BlockPos(i, j, k);
				}
			}
		}
	}

	private BlockPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public static BlockPos of(int x, int y, int z) {
		return blockPosArray[getBlockIndex(x, y, z)];
	}

	public static int getBlockIndex(int x, int y, int z) {
		return (x << (CHUNK_SIZE_LOG2 << 1)) + (y << CHUNK_SIZE_LOG2) + z;
	}
}
