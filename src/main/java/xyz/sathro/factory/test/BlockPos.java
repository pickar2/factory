package xyz.sathro.factory.test;

import lombok.ToString;
import xyz.sathro.factory.util.Maths;

@ToString
public class BlockPos {
	public static final int CHUNK_SIZE = 16;
	public static final int CHUNK_SIZE_LOG2 = Maths.log2(CHUNK_SIZE);
	public static final int CHUNK_SIZE_POW_2 = CHUNK_SIZE * CHUNK_SIZE;

	private static final BlockPos[] blockPosArray = new BlockPos[CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE];

	public final int x, y, z;
	public final int index;

	static {
		int index = 0;
		for (int x = 0; x < CHUNK_SIZE; x++) {
			for (int y = 0; y < CHUNK_SIZE; y++) {
				for (int z = 0; z < CHUNK_SIZE; z++) {
					blockPosArray[index++] = new BlockPos(x, y, z);
				}
			}
		}
	}

	private BlockPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;

		index = getBlockIndex(x, y, z);
	}

	public static BlockPos of(int x, int y, int z) {
		return blockPosArray[getBlockIndex(x, y, z)];
	}

	public BlockPos plusY() {
		return blockPosArray[index + CHUNK_SIZE];
	}

	public BlockPos minusY() {
		return blockPosArray[index - CHUNK_SIZE];
	}

	public BlockPos plusZ() {
		return blockPosArray[index + 1];
	}

	public BlockPos minusZ() {
		return blockPosArray[index - 1];
	}

	public BlockPos plusX() {
		return blockPosArray[index + CHUNK_SIZE_POW_2];
	}

	public BlockPos minusX() {
		return blockPosArray[index - CHUNK_SIZE_POW_2];
	}

	public static int getBlockIndex(int x, int y, int z) {
		return (x << (CHUNK_SIZE_LOG2 << 1)) + (y << CHUNK_SIZE_LOG2) + z;
	}
}
