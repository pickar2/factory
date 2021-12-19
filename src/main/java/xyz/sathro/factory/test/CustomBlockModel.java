package xyz.sathro.factory.test;

import org.jetbrains.annotations.NotNull;
import xyz.sathro.vulkan.models.CombinedBuffer;

import java.util.function.Function;

public class CustomBlockModel implements IBlockModel {
	@Override
	public @NotNull CombinedBuffer.Builder getCombinedBufferBuilder(@NotNull Function<RelativeBlockPos, IBlockType> blockAccess) {
		final CombinedBuffer.Builder builder = CombinedBuffer.builder();

		for (final SideNew side : SideNew.values()) {
			// skip checks if model does not have this side, because block access slow
			// may be change blockAccess function to something that takes 3 ints as argument? to remove RelativeBlockPos thing
			final IBlockType blockType = blockAccess.apply(new RelativeBlockPos(side.x, side.y, side.z)); // TODO: cache default relative block pos in sides and in RelativeBlockPos class
			if (blockType != null) {
				final IBlockModel model = blockType.getModel();
				if (!model.isSideOpaque(side)) {
					if (model.isSimpleBlock()) {
						// add full side
						// mark side as valid
					} else {
						// do custom check (mask?) and add only needed vertices
						// mark side as valid only if vertices were added
						// ^ is wrong, because there is case when no vertices were added but inner model still can be seen through gap in outer model
						// so we must mark side as valid only when other model does not hide the gap
						// gap mask == inverted mask of side
						// check should be easy
					}
				}
			}
		}

		// if any side was valid -> render inner part of BlockModel
		// we can store which sides are connected to inner model and render it only when one of those sides were valid
		// or should we render inner model anyway? because it can be seen from inside even when all sides of block are hidden
		// maybe make a switch somewhere in settings to turn inner model culling on and off

		return builder;
	}
}
