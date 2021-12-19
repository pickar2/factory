package xyz.sathro.factory.test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

// TODO: if this will be used as Chunk / Sector think where will be:
// TODO: 0. Quads need to be saved with ability to be moved when needed to position them in chunk
// TODO: 1. AmbientOcclusion
// TODO: 2. Lighting
// TODO: 2.1 Light coloring when it passes colored glass?
// TODO: 3. Transparent quads
// TODO: 4. Quad rotation
// TODO: 5. Merge different block textures like in hytale (?)
// TODO: 6. Quick remesh when only one block changed
// TODO: 7. Cache of vulkan-ready info (vertices and indices) for every container with non-empty innerMesh
// TODO: 8. Abstract quads and quad compatibility function
// TODO: 9. VoxelShape for collision/selection detection
public class BlockModelContainer {
	public static final int MAX_DEEPNESS = 14;

	public final byte divisionLevel;
	public final byte divisionLevelDoubled;
	public final int blocksPerSide;

	public final int blockCount;
	public final BlockModelContainer[] blocks;
	@NotNull @Getter protected final TexturedQuad[][] innerQuads;
	@NotNull @Getter protected final TexturedQuad[][] outerQuads;
	@NotNull @Getter protected final TexturedQuad[][] maskQuads;
	protected int deepness = 0;

	@Getter protected boolean dirty = true;

	public BlockModelContainer(int divisionLevel) {
		this.divisionLevel = (byte) divisionLevel;
		this.divisionLevelDoubled = (byte) (divisionLevel << 1);

		this.blocksPerSide = 1 << divisionLevel;

		this.blockCount = blocksPerSide * blocksPerSide * blocksPerSide;
		this.blocks = new BlockModelContainer[blockCount];

		this.outerQuads = new TexturedQuad[SideNew.values().length][];
		this.innerQuads = new TexturedQuad[SideNew.values().length][];
		this.maskQuads = new TexturedQuad[SideNew.values().length][];
	}

	private BlockModelContainer(int divisionLevel, BlockModelContainer[] blocks) {
		this(divisionLevel);
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i] != null) {
				this.blocks[i] = blocks[i].copy();
			}
		}
	}

	public static void main(String[] args) {
		System.setProperty("joml.format", "false");

		BlockTexture texture = new BlockTexture();
		texture.id = 42;
		UnitBlockModelContainer unit = new UnitBlockModelContainer();
		UnitBlockModelContainer unit_bottom = new UnitBlockModelContainer();
		unit.setSideTexture(SideNew.TOP, texture);
		unit.setSideTexture(SideNew.BOTTOM, texture);
		unit.setSideTexture(SideNew.LEFT, texture);
		unit.setSideTexture(SideNew.RIGHT, texture);
		unit.setSideTexture(SideNew.FRONT, texture);
		unit.setSideTexture(SideNew.BACK, texture);

//		unit_bottom.setSideTexture(Side.BOTTOM, texture);

		BlockModelContainer block = new BlockModelContainer(1);
		BlockModelContainer block_inner = new BlockModelContainer(1);
		BlockModelContainer block_inner_inner = new BlockModelContainer(1);

//		int blockCount = 4;
//		for (int x = 0; x < blockCount; x++) {
//			for (int z = 0; z < blockCount; z++) {
//				block.insert(x, 0, z, unit.copy());
//			}
//		}

//		block_inner_inner.insert(0, 0, 0, unit.copy());
//		block_inner_inner.insert(0, 0, 1, unit.copy());
//		block_inner_inner.insert(1, 0, 0, unit.copy());
//		block_inner_inner.insert(1, 0, 1, unit.copy());

//		block_inner.insert(0, 0, 0, unit.copy());
//		block_inner.insert(0, 0, 1, unit.copy());
//		block_inner.insert(1, 0, 0, unit.copy());
//		block_inner.insert(1, 0, 1, unit.copy());
//
//		block_inner.insert(0, 1, 0, unit.copy());
//		block_inner.insert(0, 1, 1, unit.copy());

//		unit.setDeepness(2);
		block_inner.fill(unit);
//		block_inner.setDeepness(1);

		block.insert(0, 0, 0, block_inner.copy());
		block.insert(0, 0, 1, block_inner.copy());
		block.insert(1, 0, 0, block_inner.copy());
		block.insert(1, 0, 1, block_inner.copy());

		block.insert(0, 1, 0, block_inner.copy());
		block.insert(0, 1, 1, block_inner.copy());
		block.insert(1, 1, 0, block_inner.copy());
		block.insert(1, 1, 1, block_inner.copy());

//		block.insert(2, 3, 1, unit_bottom.copy());

		block.makeQuads(0, 0, 0);

		System.out.println(block.deepness);
		System.out.println(block.blocks[0].deepness);
		System.out.println(block.blocks[0].blocks[0].deepness);

		System.out.println(Arrays.deepToString(block.getInnerQuads()));
		System.out.println(Arrays.deepToString(block.getOuterQuads()));
		System.out.println(Arrays.deepToString(block.getMaskQuads()));
	}

	public BlockModelContainer copy() {
		final BlockModelContainer copy = new BlockModelContainer(divisionLevel, blocks);
		copy.deepness = this.deepness;
		for (int i = 0; i < SideNew.values().length; i++) {
			if (this.outerQuads[i] != null && this.outerQuads[i].length > 0) {
				copy.outerQuads[i] = new TexturedQuad[this.outerQuads[i].length];
				System.arraycopy(this.outerQuads[i], 0, copy.outerQuads[i], 0, this.outerQuads[i].length);
			}
			if (this.innerQuads[i] != null && this.innerQuads[i].length > 0) {
				copy.innerQuads[i] = new TexturedQuad[this.innerQuads[i].length];
				System.arraycopy(this.innerQuads[i], 0, copy.innerQuads[i], 0, this.innerQuads[i].length);
			}
			if (this.maskQuads[i] != null && this.maskQuads[i].length > 0) {
				copy.maskQuads[i] = new TexturedQuad[this.maskQuads[i].length];
				System.arraycopy(this.maskQuads[i], 0, copy.maskQuads[i], 0, this.maskQuads[i].length);
			}
		}
		copy.dirty = this.dirty;

		return copy;
	}

	public List<TexturedQuad> greedyMesh(TexturedQuad[][] allQuads, SideNew side, TexturedQuad.CompatibilityFunction compatibilityFunction) {
		final List<TexturedQuad> quads = new ObjectArrayList<>();
		int n, w, h;

		final int[] x = new int[] { 0, 0, 0 };

		final int d = side.d;
		final int u = (d + 1) % 3;
		final int v = (d + 2) % 3;

		final TexturedQuad[][] mask = new TexturedQuad[blocksPerSide * blocksPerSide][];

		final IntList indices = new IntArrayList();
		final IntList indicesH = new IntArrayList();
		int index;
		TexturedQuad fullQuad, lineQuad;
		TexturedQuad[] currentQuads, face;
		TexturedQuad.MeshSide meshSide;
		boolean found;
		for (x[d] = 0; x[d] < blocksPerSide; x[d]++) {
			n = 0;

			for (x[v] = 0; x[v] < blocksPerSide; x[v]++) {
				for (x[u] = 0; x[u] < blocksPerSide; x[u]++) {
					mask[n++] = allQuads[index(x[0], x[1], x[2])];
				}
			}

			n = 0;
			for (int j = 0; j < blocksPerSide; j++) {
				for (int i = 0; i < blocksPerSide; ) {
					if (mask[n] != null && mask[n].length > 0) {
						currentQuads = mask[n];
						for (int fullQuadIndex = 0; fullQuadIndex < currentQuads.length; fullQuadIndex++) {
							if (currentQuads[fullQuadIndex] == null) {
								continue;
							}
							indices.add(fullQuadIndex);
							fullQuad = new TexturedQuad(currentQuads[fullQuadIndex]);

							for (w = 1; w < blocksPerSide - i; w++) {
								face = mask[n + w];

								if (face != null && face.length > 0) {
									found = false;

									for (int quadIndex = 0; quadIndex < face.length; quadIndex++) {
										if (face[quadIndex] != null) {
											meshSide = fullQuad.getMeshSide(face[quadIndex]);
											if (meshSide != TexturedQuad.MeshSide.NONE) {
												fullQuad.mesh(face[quadIndex], meshSide);

												indices.add(quadIndex);
												found = true;
												break;
											}
										}
									}
									if (!found) { break; }
								} else {
									break;
								}
							}

							out:
							for (h = 1; h < blocksPerSide - j; h++) {
								face = mask[n + h * blocksPerSide];
								if (face == null || face.length == 0) { break; }
								lineQuad = null;
								indicesH.clear();
								for (int quadIndex = 0; quadIndex < face.length; quadIndex++) {
									final TexturedQuad quad = face[quadIndex];
									if (quad != null && compatibilityFunction.check(quad, fullQuad)) { // TODO: there should be only one fully compatible quad, we should check quad pos too
										indicesH.add(quadIndex);
										lineQuad = new TexturedQuad(quad);
										break;
									}
								}
								if (lineQuad == null) { break; }
								for (int w1 = 1; w1 < w; w1++) {
									face = mask[n + w1 + h * blocksPerSide];

									if (face != null && face.length > 0) {
										found = false;

										for (int quadIndex = 0; quadIndex < face.length; quadIndex++) {
											if (face[quadIndex] != null) {
												meshSide = lineQuad.getMeshSide(face[quadIndex]);

												if (meshSide != TexturedQuad.MeshSide.NONE) {
													lineQuad.mesh(face[quadIndex], meshSide);

													indicesH.add(quadIndex);
													found = true;
													break;
												}
											}
										}
										if (!found) {
											break out;
										}
									} else {
										break out;
									}
								}

								meshSide = fullQuad.getMeshSide(lineQuad);
								if (meshSide != TexturedQuad.MeshSide.NONE) {
									fullQuad.mesh(lineQuad, meshSide);
									indices.addAll(indicesH);
								} else {
									break;
								}
							}

							quads.add(fullQuad);
							index = 0;
							for (int l = 0; l < h; ++l) {
								for (int k = 0; k < w; ++k) {
									mask[n + k + l * blocksPerSide][indices.getInt(index++)] = null;
								}
							}
							indices.clear();
						}
					}
					i++;
					n++;
				}
			}
		}

		return quads;
	}

	public void updateDeepness(int parentDeepness) {
		this.deepness += parentDeepness;
		if (deepness + divisionLevel > MAX_DEEPNESS) { throw new IllegalStateException("Deepness must be less then or equal to " + MAX_DEEPNESS); }
		for (BlockModelContainer container : blocks) {
			if (container != null) {
				container.updateDeepness(parentDeepness);
			}
		}
		this.dirty = true;
	}

	public void insert(int x, int y, int z, @NotNull BlockModelContainer block) {
		block.updateDeepness(deepness + divisionLevel);
		blocks[index(x, y, z)] = block;
		this.dirty = true;
	}

	public void insertWithoutUpdatingDeepness(int x, int y, int z, BlockModelContainer block) {
		blocks[index(x, y, z)] = block;
		this.dirty = true;
	}

	public void fill(@NotNull BlockModelContainer block) {
		block = block.copy();
		block.updateDeepness(deepness + divisionLevel);
		for (int i = 0; i < blockCount; i++) {
			blocks[i] = block.copy();
		}
		this.dirty = true;
	}

	public void fillWithoutUpdatingDeepness(@NotNull BlockModelContainer block) {
		for (int i = 0; i < blockCount; i++) {
			blocks[i] = block.copy();
		}
		this.dirty = true;
	}

	public int offset(int number) {
		return number << (MAX_DEEPNESS - divisionLevel + 1 - deepness); // TODO: save max-divLevel+1 as const?
	}

	public int getQuadSize() {
		return 1 << (MAX_DEEPNESS - divisionLevel + 1 - deepness);
	}

	public int index(int x, int y, int z) {
		return z << divisionLevelDoubled | y << divisionLevel | x;
	}

	public TexturedQuad[][][] getMaskQuads(int offsetX, int offsetY, int offsetZ) {
		final TexturedQuad[][][] maskQuads = new TexturedQuad[SideNew.values().length][blockCount][];
		BlockModelContainer block;
		int index = 0;
		for (int z = 0; z < blocksPerSide; z++) {
			for (int y = 0; y < blocksPerSide; y++) {
				for (int x = 0; x < blocksPerSide; x++) {
					block = blocks[index];
					if (block != null) {
						if (block.isDirty()) {
							block.makeQuads(offsetX + offset(x), offsetY + offset(y), offsetZ + offset(z));
						}
						for (int i = 0; i < SideNew.values().length; i++) {
							maskQuads[i][index] = new TexturedQuad[block.maskQuads[i].length];
							System.arraycopy(block.maskQuads[i], 0, maskQuads[i][index], 0, block.maskQuads[i].length);
						}
					}
					index++;
				}
			}
		}

		return maskQuads;
	}

	public TexturedQuad[][][] processQuads(TexturedQuad[][][] maskQuads) {
		TexturedQuad[] quadArray, quadArray2;
		int index = 0;
		int newX, newY, newZ, newIndex;
		final Set<TexturedQuad> removeThis = new ObjectOpenHashSet<>(), removeOther = new ObjectOpenHashSet<>();
		for (int z = 0; z < blocksPerSide; z++) {
			for (int y = 0; y < blocksPerSide; y++) {
				for (int x = 0; x < blocksPerSide; x++) {
					for (SideNew side : SideNew.values()) {
						quadArray = maskQuads[side.ordinal()][index];
						if (quadArray == null || quadArray.length <= 0) { continue; }
						newX = x + side.x;
						if (newX < 0 || newX >= blocksPerSide) { continue; }
						newY = y + side.y;
						if (newY < 0 || newY >= blocksPerSide) { continue; }
						newZ = z + side.z;
						if (newZ < 0 || newZ >= blocksPerSide) { continue; }
						newIndex = index(newX, newY, newZ);
						quadArray2 = maskQuads[side.opposite().ordinal()][newIndex];
						if (quadArray2 == null || quadArray2.length == 0) { continue; }

						removeThis.clear();
						removeOther.clear();
						for (TexturedQuad quad : quadArray) {
							for (TexturedQuad otherQuad : quadArray2) {
								switch (quad.getRemoveMask(otherQuad)) {
									case FIRST -> removeThis.add(quad);
									case SECOND -> removeOther.add(otherQuad);
									case BOTH -> {
										removeThis.add(quad);
										removeOther.add(otherQuad);
									}
								}
							}
						}

						maskQuads[side.ordinal()][index] = Arrays.stream(maskQuads[side.ordinal()][index]).filter(q -> !removeThis.contains(q)).toArray(TexturedQuad[]::new);
						maskQuads[side.opposite().ordinal()][newIndex] = Arrays.stream(maskQuads[side.opposite().ordinal()][newIndex]).filter(q -> !removeOther.contains(q)).toArray(TexturedQuad[]::new);
					}
					index++;
				}
			}
		}

		return maskQuads;
	}

	public TexturedQuad[][][] combineQuads(TexturedQuad[][][] processedQuads) {
		final TexturedQuad[][][] combinedQuads = new TexturedQuad[SideNew.values().length][blockCount][];
		int length, writeIndex;
		BlockModelContainer block;
		TexturedQuad[] quadArray, quadArray2;
		for (int index = 0; index < blocks.length; index++) {
			block = blocks[index];
			if (block == null) { continue; }
			for (SideNew side : SideNew.values()) {
				length = 0;
				quadArray = block.outerQuads[side.ordinal()];
				if (quadArray != null) { length += quadArray.length; }
				quadArray2 = processedQuads[side.ordinal()][index];
				if (quadArray2 != null) { length += quadArray2.length; }

				if (length == 0) { continue; }

				combinedQuads[side.ordinal()][index] = new TexturedQuad[length];
				writeIndex = 0;
				if (quadArray != null && quadArray.length > 0) {
					System.arraycopy(quadArray, 0, combinedQuads[side.ordinal()][index], 0, quadArray.length);
					writeIndex = quadArray.length;
				}
				if (quadArray2 != null && quadArray2.length > 0) {
					System.arraycopy(quadArray2, 0, combinedQuads[side.ordinal()][index], writeIndex, quadArray2.length);
				}
			}
		}

		return combinedQuads;
	}

	public void saveQuads(TexturedQuad[][][] combinedQuads, int offsetX, int offsetY, int offsetZ, TexturedQuad.CompatibilityFunction compatibilityFunction) {
		final List<TexturedQuad> inner = new ObjectArrayList<>();
		final List<TexturedQuad> outer = new ObjectArrayList<>();
		final List<TexturedQuad> mask = new ObjectArrayList<>();

		final int[] offset = new int[] { offsetX, offsetY, offsetZ };
		final int quadStart = offset(blocksPerSide - 1);
		final int quadEnd = offset(blocksPerSide);
		final int[] maxQuadStart = new int[] { offsetX + quadStart, offsetY + quadStart, offsetZ + quadStart };
		final int[] maxQuadEnd = new int[] { offsetX + quadEnd, offsetY + quadEnd, offsetZ + quadEnd };

		for (final SideNew side : SideNew.values()) {
			inner.clear();
			outer.clear();
			mask.clear();

			final List<TexturedQuad> mesh = greedyMesh(combinedQuads[side.ordinal()], side, compatibilityFunction);

			for (TexturedQuad quad : mesh) {
				if (quad.isMask(offset, maxQuadEnd)) {
					mask.add(quad);
				} else if (quad.isOuter(offset, maxQuadStart, maxQuadEnd)) {
					outer.add(quad);
				} else {
					inner.add(quad);
				}
			}

			this.outerQuads[side.ordinal()] = outer.toArray(new TexturedQuad[0]);
			this.innerQuads[side.ordinal()] = inner.toArray(new TexturedQuad[0]);
			this.maskQuads[side.ordinal()] = mask.toArray(new TexturedQuad[0]);
		}
	}

	public void makeQuads(int offsetX, int offsetY, int offsetZ) {
		// TODO: extend this to have support for non-sided quads
		// TODO: extend this to have support for abstract vertices and indices
		// TODO: force every implementation of BlockModel to have maskQuads (?)
		final TexturedQuad[][][] maskQuads = getMaskQuads(offsetX, offsetY, offsetZ);
		final TexturedQuad[][][] processedQuads = processQuads(maskQuads);
		final TexturedQuad[][][] combinedQuads = combineQuads(processedQuads);

//		for (Side side : Side.values()) {
//			System.out.println(side);
//			final TexturedQuad[][] sideQuads = combinedQuads[side.ordinal()];
//
//			for (int z = 0; z < blocksPerSide; z++) {
//				for (int y = 0; y < blocksPerSide; y++) {
//					for (int x = 0; x < blocksPerSide; x++) {
//						final int index = index(x, y, z);
//
//						if (sideQuads[index] == null) {
//							System.out.printf("(%d, %d, %d): null%n", x, y, z);
//						} else {
//							System.out.printf("(%d, %d, %d): %s%n", x, y, z, Arrays.toString(sideQuads[index]));
//						}
//					}
//				}
//			}
//		}
//		System.out.println();

		saveQuads(combinedQuads, offsetX, offsetY, offsetZ, TexturedQuad::compatible);

		this.dirty = false;
	}
}
