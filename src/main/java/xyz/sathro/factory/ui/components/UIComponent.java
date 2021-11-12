package xyz.sathro.factory.ui.components;

import org.joml.Vector2i;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.vulkan.models.CombinedBuffer;
import xyz.sathro.vulkan.models.VulkanImage;

import java.awt.*;
import java.nio.ByteBuffer;

public class UIComponent implements IUIComponent {
	public static final int SIZEOF = 8 * Integer.BYTES;

	public static final int OFFSETOF_POS_XY = 0;
	public static final int OFFSETOF_POS_Z_TEX_ID = Integer.BYTES;
	public static final int OFFSETOF_SIZE_XY = 2 * Integer.BYTES;
	public static final int OFFSETOF_CLIP_POS_XY = 3 * Integer.BYTES;
	public static final int OFFSETOF_CLIP_SIZE_XY = 4 * Integer.BYTES;
	public static final int OFFSETOF_COLOR = 5 * Integer.BYTES;
	public static final int OFFSETOF_ROUNDING = 6 * Integer.BYTES;

	public static final int HALF_INT16 = 32768;

	public final Vector2i position = new Vector2i();
	public final Vector2i size = new Vector2i();

	public final Vector2i clipPos = new Vector2i();
	public final Vector2i clipSize = new Vector2i();

	public Color color = Color.WHITE;
	public int zIndex;
	public int rounding, clipRounding;
	public VulkanImage texture;

	public int textureID = 0;
	public boolean hasTexture = false;
	public boolean textureChanged = false;
	public boolean customZIndex = false;

	@Override
	public CombinedBuffer.Builder appendToBufferBuilder(CombinedBuffer.Builder builder) {
		return builder;
	}

	@Override
	public boolean isSimpleQuad() {
		return true;
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@Override
	public void get(int index, ByteBuffer buffer) {
		buffer.putInt(index + OFFSETOF_POS_XY, Maths.asTwoInt16(position.x + HALF_INT16, position.y + HALF_INT16));
		buffer.putInt(index + OFFSETOF_POS_Z_TEX_ID, Maths.asTwoInt16(zIndex, textureID));
		buffer.putInt(index + OFFSETOF_SIZE_XY, Maths.asTwoInt16(size.x, size.y));
		buffer.putInt(index + OFFSETOF_CLIP_POS_XY, Maths.asTwoInt16(clipPos.x + HALF_INT16, clipPos.y + HALF_INT16));
		buffer.putInt(index + OFFSETOF_CLIP_SIZE_XY, Maths.asTwoInt16(clipSize.x, clipSize.y));
		buffer.putInt(index + OFFSETOF_COLOR, color.getRGB());
		buffer.putInt(index + OFFSETOF_ROUNDING, Maths.asTwoInt16(rounding, clipRounding));
	}
}
