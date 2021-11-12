package xyz.sathro.factory.ui.components;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.Getter;
import lombok.Setter;
import xyz.sathro.factory.ui.constraints.PositionConstraint;
import xyz.sathro.factory.ui.constraints.SizeConstraint;
import xyz.sathro.factory.ui.constraints.UIConstraint;

public class BasicUIComponent extends UIComponent {
	protected final ObjectSet<UIConstraint> sizeConstraints = new ObjectOpenHashSet<>();
	protected final ObjectSet<UIConstraint> positionConstraints = new ObjectOpenHashSet<>();
	protected final ObjectSet<UIConstraint> otherConstraints = new ObjectOpenHashSet<>();

	public final ObjectList<BasicUIComponent> children = new ObjectArrayList<>();
	@Getter @Setter private BasicUIComponent parent;

	@Override
	public String toString() {
		return "[" +
		       "position=(" + position.x + ", " + position.y + ")" +
		       ", size=(" + size.x + ", " + size.y + ")" +
		       (children.isEmpty() ? "" : (", children=" + children)) +
		       "]";
	}

	public void addComponent(BasicUIComponent component) {
		component.setParent(this);
		children.add(component);
	}

	public void removeComponent(BasicUIComponent component) {
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
		children.forEach(BasicUIComponent::applyConstraints);
	}
}
