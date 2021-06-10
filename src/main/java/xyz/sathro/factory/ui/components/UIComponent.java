package xyz.sathro.factory.ui.components;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2i;
import xyz.sathro.factory.ui.constraints.PositionConstraint;
import xyz.sathro.factory.ui.constraints.SizeConstraint;
import xyz.sathro.factory.ui.constraints.UIConstraint;
import xyz.sathro.factory.util.Maths;
import xyz.sathro.vulkan.models.CombinedBuffer;
import xyz.sathro.vulkan.models.IBufferObject;
import xyz.sathro.vulkan.models.VulkanImage;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class UIComponent implements IBufferObject {
	public static final int SIZEOF = 8 * Integer.BYTES;

	public static final int OFFSETOF_POS_XY = 0;
	public static final int OFFSETOF_POS_Z_TEX_ID = Integer.BYTES;
	public static final int OFFSETOF_SIZE_XY = 2 * Integer.BYTES;
	public static final int OFFSETOF_CLIP_POS_XY = 3 * Integer.BYTES;
	public static final int OFFSETOF_CLIP_SIZE_XY = 4 * Integer.BYTES;
	public static final int OFFSETOF_COLOR = 5 * Integer.BYTES;
	public static final int OFFSETOF_ROUNDING = 6 * Integer.BYTES;

	public final Vector2i position = new Vector2i();
	public final Vector2i size = new Vector2i();

	private final ObjectSet<UIConstraint> sizeConstraints = new ObjectOpenHashSet<>();
	private final ObjectSet<UIConstraint> positionConstraints = new ObjectOpenHashSet<>();
	private final ObjectSet<UIConstraint> otherConstraints = new ObjectOpenHashSet<>();

	public final ObjectList<UIComponent> children = new ObjectArrayList<>();
	@Getter @Setter private UIComponent parent;

	public final Vector2i clipPos = new Vector2i();
	public final Vector2i clipSize = new Vector2i();
	@Getter @Setter private Color backgroundColor = Color.WHITE;
	public int zIndex, textureID = -1;
	public int rounding, clipRounding;
	public VulkanImage texture;
	public boolean hasTexture = false;

	@Override
	public String toString() {
		return "[" +
		       "position=(" + position.x + ", " + position.y + ")" +
		       ", size=(" + size.x + ", " + size.y + ")" +
		       (children.isEmpty() ? "" : (", children=" + children)) +
		       "]";
	}

	public void addComponent(UIComponent component) {
		component.setParent(this);
		children.add(component);
	}

	public void removeComponent(UIComponent component) {
		if (children.remove(component)) {
			component.setParent(null);
		}
	}

	public void addConstraint(UIConstraint constraint) {
		if (constraint instanceof SizeConstraint) {
			sizeConstraints.add(constraint);
		} else if (constraint instanceof PositionConstraint) {
			positionConstraints.add(constraint);
		} else {
			otherConstraints.add(constraint);
		}
	}

	public boolean removeConstraint(UIConstraint constraint) {
		if (constraint instanceof SizeConstraint) {
			return sizeConstraints.remove(constraint);
		} else if (constraint instanceof PositionConstraint) {
			return positionConstraints.remove(constraint);
		} else {
			return otherConstraints.remove(constraint);
		}
	}

	public void applyConstraints() {
		for (UIConstraint constraint : sizeConstraints) {
			constraint.apply(this);
		}
		for (UIConstraint constraint : positionConstraints) {
			constraint.apply(this);
		}
		for (UIConstraint constraint : otherConstraints) {
			constraint.apply(this);
		}
		children.forEach(UIComponent::applyConstraints);
	}

	public CombinedBuffer.Builder appendToBufferBuilder(CombinedBuffer.Builder builder) {
		return builder;
	}

	public boolean isSimpleQuad() {
		return true;
	}

	private Consumer<UIComponent> onHoverInCallback;
	private Consumer<UIComponent> onHoverOutCallback;

	public void setOnHoverCallback(Consumer<UIComponent> in, Consumer<UIComponent> out) {
		onHoverInCallback = in;
		onHoverOutCallback = out;
	}

	public void onHoverIn() {
		if (onHoverInCallback != null) {
			onHoverInCallback.accept(this);
		}
	}

	public void onHoverOut() {
		if (onHoverOutCallback != null) {
			onHoverOutCallback.accept(this);
		}
	}

	@Override
	public int sizeof() {
		return SIZEOF;
	}

	@SuppressWarnings("SuspiciousNameCombination")
	@Override
	public void get(int index, ByteBuffer buffer) {
		buffer.putInt(index + OFFSETOF_POS_XY, Maths.asTwoInt16(position.x, position.y));
		buffer.putInt(index + OFFSETOF_POS_Z_TEX_ID, Maths.asTwoInt16(zIndex, textureID + 1));
		buffer.putInt(index + OFFSETOF_SIZE_XY, Maths.asTwoInt16(size.x, size.y));
		buffer.putInt(index + OFFSETOF_CLIP_POS_XY, Maths.asTwoInt16(clipPos.x, clipPos.y));
		buffer.putInt(index + OFFSETOF_CLIP_SIZE_XY, Maths.asTwoInt16(clipSize.x, clipSize.y));
		buffer.putInt(index + OFFSETOF_COLOR, backgroundColor.getRGB());
		buffer.putInt(index + OFFSETOF_ROUNDING, Maths.asTwoInt16(rounding, clipRounding));
	}
}
