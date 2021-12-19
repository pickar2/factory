package xyz.sathro.factory.test;

import org.jetbrains.annotations.NotNull;
import xyz.sathro.vulkan.models.CombinedBuffer;

import java.util.function.Function;

public abstract class BasicBlockModel implements IBlockModel {
	public abstract @NotNull BlockTexture getTextureForSide(@NotNull SideNew side);

	@Override
	public @NotNull CombinedBuffer.Builder getCombinedBufferBuilder(@NotNull Function<RelativeBlockPos, IBlockType> blockAccess) {
		final CombinedBuffer.Builder builder = CombinedBuffer.builder();

		for (final SideNew side : SideNew.values()) {
			final IBlockType blockType = blockAccess.apply(new RelativeBlockPos(side.x, side.y, side.z)); // TODO: cache default relative block pos in sides and in RelativeBlockPos class
			if (blockType == null || !blockType.getModel().isSideOpaque(side)) {
				// add block vertices and indices to builder
			}
		}

		return builder;
	}
}
