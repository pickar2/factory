package xyz.sathro.factory.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BlockModelContainerTest {
	private static final int MAX_SIZE = 1 << BlockModelContainer.MAX_DEEPNESS + 1;
	private static final int HALF_SIZE = MAX_SIZE >> 1;

	@Test
	public void testFilledContainersWithOneTexture() {
		final BlockTexture texture = new BlockTexture();
		texture.id = 42;

		final UnitBlockModelContainer unit = new UnitBlockModelContainer();

		unit.setSideTexture(Side.TOP, texture);
		unit.setSideTexture(Side.BOTTOM, texture);
		unit.setSideTexture(Side.LEFT, texture);
		unit.setSideTexture(Side.RIGHT, texture);
		unit.setSideTexture(Side.FRONT, texture);
		unit.setSideTexture(Side.BACK, texture);

		final BlockModelContainer[] blocks = new BlockModelContainer[2];

		BlockModelContainer block;
		BlockModelContainer filler = unit;
		for (int index = 0; index < blocks.length; index++) {
			blocks[index] = new BlockModelContainer(2);
			block = blocks[index];
			block.fill(filler);
			block.makeQuads(0, 0, 0);

			for (int i = 0; i < Side.values().length; i++) {
				assertEquals(1, block.getMaskQuads()[i].length);

				final TexturedQuad quad = block.getMaskQuads()[i][0];
				assertEquals(texture, quad.getTexture());

				assertFullSide(quad);

				final Side side = quad.getSide();
				if (side.isIncrease) {
					assertEquals(MAX_SIZE, quad.getPos().get(side.d));
				} else {
					assertEquals(0, quad.getPos().get(side.d));
				}
			}

			filler = block;
		}
	}

	@Test
	public void testSlab() {
		final BlockTexture texture = new BlockTexture();
		texture.id = 42;

		final UnitBlockModelContainer unit = new UnitBlockModelContainer();

		unit.setSideTexture(Side.TOP, texture);
		unit.setSideTexture(Side.BOTTOM, texture);
		unit.setSideTexture(Side.LEFT, texture);
		unit.setSideTexture(Side.RIGHT, texture);
		unit.setSideTexture(Side.FRONT, texture);
		unit.setSideTexture(Side.BACK, texture);

		int[] pos = new int[3];
		int u, v;
		for (Side slabSide : Side.values()) {
			if (!slabSide.isIncrease) { continue; }
			u = (slabSide.d + 1) % 3;
			v = (slabSide.d + 2) % 3;
			for (int d = 0; d <= 1; d++) {
				pos[slabSide.d] = d;
				final BlockModelContainer slab = new BlockModelContainer(1);

				for (int i = 0; i <= 1; i++) {
					for (int j = 0; j <= 1; j++) {
						pos[u] = i;
						pos[v] = j;
						slab.insert(pos[0], pos[1], pos[2], unit.copy());
					}
				}

				slab.makeQuads(0, 0, 0);

				for (int i = 0; i < Side.values().length; i++) {
					final Side checkSide = Side.values()[i];
					final TexturedQuad quad;
					if (d == 0 && checkSide == slabSide || d == 1 && checkSide == slabSide.opposite()) {
						assertEquals(1, slab.getOuterQuads()[i].length);
						quad = slab.getOuterQuads()[i][0];
					} else {
						assertEquals(1, slab.getMaskQuads()[i].length);
						quad = slab.getMaskQuads()[i][0];
					}

					assertEquals(texture, quad.getTexture());

					if (slabSide.d == checkSide.d) {
						assertFullSide(quad);
						if (d == 0 && checkSide == slabSide || d == 1 && checkSide == slabSide.opposite()) {
							assertEquals(HALF_SIZE, quad.getPos().get(slabSide.d));
						} else {
							assertEquals(d == 0 ? 0 : MAX_SIZE, quad.getPos().get(slabSide.d));
						}
					} else {
						assertHalfSide(quad);
					}
				}
			}
		}
	}

	// TODO: add stairs test

	// TODO: add custom blockModel test

	public void assertFullSide(TexturedQuad quad) {
		final Side side = quad.getSide();

		assertEquals(0, quad.getPos().get((side.d + 1) % 3));
		assertEquals(0, quad.getPos().get((side.d + 2) % 3));

		assertEquals(MAX_SIZE, quad.getSize().x);
		assertEquals(MAX_SIZE, quad.getSize().y);
	}

	public void assertHalfSide(TexturedQuad quad) {
		final Side side = quad.getSide();

		final int a = quad.getSize().x > quad.getSize().y ? side.textureX : side.textureY; // longest coordinate
		final int b = a == side.textureX ? side.textureY : side.textureX;

		assertNotEquals(quad.getSize().y, quad.getSize().x);

		assertEquals(0, quad.getPos().get(a));
		assertTrue(quad.getPos().get(b) == 0 || quad.getPos().get(b) == HALF_SIZE);
		if (side.isIncrease) {
			assertEquals(MAX_SIZE, quad.getPos().get(side.d));
		} else {
			assertEquals(0, quad.getPos().get(side.d));
		}

		assertEquals(HALF_SIZE, quad.getSize().get(quad.getSize().minComponent()));
		assertEquals(MAX_SIZE, quad.getSize().get(quad.getSize().maxComponent()));
	}
}