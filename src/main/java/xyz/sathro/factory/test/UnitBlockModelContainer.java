package xyz.sathro.factory.test;

import org.joml.Vector2i;
import org.joml.Vector3i;

public class UnitBlockModelContainer extends BlockModelContainer {
	private final BlockTexture[] textures;

	public UnitBlockModelContainer() {
		super(0);
		this.textures = new BlockTexture[SideNew.values().length];
	}

	public UnitBlockModelContainer(BlockTexture[] textures) {
		super(0);
		this.textures = textures;
	}

	public void setSideTexture(SideNew side, BlockTexture texture) {
		textures[side.ordinal()] = texture;
	}

	@Override
	public void makeQuads(int offsetX, int offsetY, int offsetZ) {
		final int quadSize = getQuadSize();
		final Vector3i pos = new Vector3i(offsetX, offsetY, offsetZ);
		for (int i = 0; i < textures.length; i++) {
			if (textures[i] != null) {
				final SideNew side = SideNew.values()[i];
				final TexturedQuad quad = new TexturedQuad(side, new Vector3i(pos), new Vector2i(quadSize), textures[i], new Vector2i(pos.get(side.textureX), pos.get(side.textureY)), new Vector2i(quadSize));
				if (side.isIncrease) {
					quad.getPos().setComponent(side.d, quad.getPos().get(side.d) + quadSize);
				}

				maskQuads[i] = new TexturedQuad[] { quad };
			}
		}

		dirty = false;
	}

	@Override
	public UnitBlockModelContainer copy() {
		final UnitBlockModelContainer copy = new UnitBlockModelContainer(this.textures);
		copy.deepness = deepness;
		return copy;
	}
}
