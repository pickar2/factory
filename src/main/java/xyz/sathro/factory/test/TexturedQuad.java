package xyz.sathro.factory.test;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector3i;

@AllArgsConstructor
@ToString
public class TexturedQuad {
	public static final int MAX_POS = 1 << (BlockModelContainer.MAX_DEEPNESS + 1);
	public static final int MAX_POS_MINUS_ONE = MAX_POS - 1;

	@Getter private final Side side;
	@Getter private final Vector3i pos;
	@Getter private final Vector2i size;

	@Getter private final BlockTexture texture;
	@Getter private final Vector2i texturePos;
	@Getter private final Vector2i textureSize;

	public TexturedQuad(Side side, BlockTexture texture) {
		this.side = side;
		pos = new Vector3i(0);
		size = new Vector2i(MAX_POS);

		this.texture = texture;
		texturePos = new Vector2i(0);
		textureSize = new Vector2i(MAX_POS);
	}

	public TexturedQuad(TexturedQuad quad) {
		this.side = quad.side;
		this.pos = new Vector3i(quad.pos);
		this.size = new Vector2i(quad.size);

		this.texture = quad.texture;
		this.texturePos = new Vector2i(quad.texturePos);
		this.textureSize = new Vector2i(quad.textureSize);
	}

	public boolean compatible(TexturedQuad other) {
		if (side != other.side) { return false; }
		if (pos.get(side.d) != other.pos.get(side.d)) { return false; }
		return texture.equals(other.texture);
	}

	public @NotNull MeshSide getMeshSide(@NotNull TexturedQuad other) {
		MeshSide ret = MeshSide.NONE;
		if ((pos.get(side.textureX) + size.x) == other.pos.get(side.textureX) && ((texturePos.x + textureSize.x) & MAX_POS_MINUS_ONE) == other.texturePos.x) {
			ret = MeshSide.X_INC;
		}
		if (pos.get(side.textureX) == other.pos.get(side.textureX) + other.size.x && texturePos.x == ((other.texturePos.x + other.textureSize.x) & MAX_POS_MINUS_ONE)) {
			if (ret != MeshSide.NONE) { return MeshSide.NONE; }
			ret = MeshSide.X_DEC;
		}
		if (pos.get(side.textureY) + size.y == other.pos.get(side.textureY) && ((texturePos.y + textureSize.y) & MAX_POS_MINUS_ONE) == other.texturePos.y) {
			if (ret != MeshSide.NONE) { return MeshSide.NONE; }
			ret = MeshSide.Y_INC;
		}
		if (pos.get(side.textureY) == other.pos.get(side.textureY) + other.size.y && texturePos.y == ((other.texturePos.y + other.textureSize.y) & MAX_POS_MINUS_ONE)) {
			if (ret != MeshSide.NONE) { return MeshSide.NONE; }
			ret = MeshSide.Y_DEC;
		}

		return ret;
	}

	/**
	 * Changes this quad to fill area of this quad and other quad.
	 *
	 * @param other    Quad to be meshed with.
	 * @param meshSide Side of meshing.
	 */
	public void mesh(@NotNull TexturedQuad other, @NotNull MeshSide meshSide) {
		switch (meshSide) {
			case X_INC -> {
				this.size.x += other.size.x;
				this.textureSize.x += other.textureSize.x;
			}
			case X_DEC -> {
				this.size.x += other.size.x;
				this.textureSize.x += other.textureSize.x;

				this.pos.setComponent(side.textureX, other.pos.get(side.textureX));
				this.texturePos.x = other.texturePos.x;
			}
			case Y_INC -> {
				this.size.y += other.size.y;
				this.textureSize.y += other.textureSize.y;
			}
			case Y_DEC -> {
				this.size.y += other.size.y;
				this.textureSize.y += other.textureSize.y;

				this.pos.setComponent(side.textureY, other.pos.get(side.textureY));
				this.texturePos.y = other.texturePos.y;
			}
			case NONE -> throw new IllegalArgumentException();
		}
	}

	public boolean isOuter(int[] offset, int[] maxQuadStart, int[] maxQuadEnd) {
		// TODO: is `pos.get(side.textureX) == maxQuadStart[side.textureX]` even valid check?
		if (pos.get(side.textureX) == offset[side.textureX]) {
			return true;
		}
		if (pos.get(side.textureY) == offset[side.textureY]) {
			return true;
		}

		return pos.get(side.textureX) + size.x == maxQuadEnd[side.textureX] || pos.get(side.textureY) + size.y == maxQuadEnd[side.textureY];
	}

	public boolean isMask(int[] offset, int[] maxQuadEnd) {
		return (side.isIncrease && pos.get(side.d) == maxQuadEnd[side.d]) || (!side.isIncrease && pos.get(side.d) == offset[side.d]);
	}

	//TODO: support for semi-transparent quads
	public @NotNull RemoveMask getRemoveMask(@NotNull TexturedQuad other) {
		if (this.pos.get(side.d) != other.pos.get(side.d)) { return RemoveMask.NONE; }

		if (this.pos.get(side.textureX) == other.pos.get(side.textureX) && this.size.x == other.size.x &&
		    this.pos.get(side.textureY) == other.pos.get(side.textureY) && this.size.y == other.size.y) {
			return RemoveMask.BOTH;
		}

		// TODO: add culling when some quad is bigger and fully closes other quad

		return RemoveMask.NONE;
	}

	public enum MeshSide {
		NONE, X_INC, X_DEC, Y_INC, Y_DEC
	}

	public enum RemoveMask {
		NONE, FIRST, SECOND, BOTH
	}

	@FunctionalInterface
	public interface CompatibilityFunction {
		boolean check(TexturedQuad first, TexturedQuad second);
	}
}
