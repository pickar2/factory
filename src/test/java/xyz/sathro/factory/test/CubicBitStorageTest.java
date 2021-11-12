package xyz.sathro.factory.test;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CubicBitStorageTest {
	@Test
	public void testRandomValues() {
		final int SIZE = 16;
		final CubicBitStorage storage = new CubicBitStorage(SIZE, 29);

		final long[] values = new long[SIZE * SIZE * SIZE];
		final Random random = new Random(1);

		int index = 0;
		for (int z = 0; z < SIZE; z++) {
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					values[index] = random.nextInt(1 << storage.stride);
					storage.set(index, values[index++]);
				}
			}
		}

		index = 0;
		for (int z = 0; z < SIZE; z++) {
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					assertEquals(values[index++], storage.get(x, y, z));
				}
			}
		}
	}
}
