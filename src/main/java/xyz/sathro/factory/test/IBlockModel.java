package xyz.sathro.factory.test;

import org.jetbrains.annotations.NotNull;
import xyz.sathro.vulkan.models.CombinedBuffer;

import java.util.function.Function;

public interface IBlockModel {
	// TODO: impl
	/* Ability to take vertex and index buffer (not compiled)
	 * May have states (some sides can be occluded and disabled from rendering)
	 * States may depend on blocks around it
	 * You can't guess which blocks around are actually needed
	 * So need to send all of them?
	 * Or send Function<RelativeBlockPos, Block>
	 *
	 */

	/**
	 * @return True if model does not change depending on other blocks around it.
	 */
	default boolean isStatelessModel() {
		return false;
	}

	/**
	 * @return True if model is just 6 quads covering full block.
	 */
	default boolean isSimpleBlock() {
		return true;
	}

	/**
	 * @return True if side takes full block and don't have any transparent bits in texture.
	 */
	default boolean isSideOpaque(@NotNull Side side) {
		return true;
	}

	@NotNull CombinedBuffer.Builder getCombinedBufferBuilder(@NotNull Function<RelativeBlockPos, IBlockType> blockAccess);

	default CombinedBuffer getCombinedBuffer() {
		return null;
	}
}
