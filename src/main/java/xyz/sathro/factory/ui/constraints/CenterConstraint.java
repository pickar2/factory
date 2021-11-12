package xyz.sathro.factory.ui.constraints;

import xyz.sathro.factory.ui.components.BasicUIComponent;

public class CenterConstraint implements UIConstraint {
	private final Alignment alignment;

	public CenterConstraint(Alignment alignment) {
		this.alignment = alignment;
	}

	@Override
	public void apply(BasicUIComponent component) {
		if (component.getParent() != null) {
			final BasicUIComponent parent = component.getParent();
			if (alignment == Alignment.HORIZONTAL || alignment == Alignment.BOTH) {
				component.position.x = parent.position.x + (parent.size.x - component.size.x) / 2;
			}
			if (alignment == Alignment.VERTICAL || alignment == Alignment.BOTH) {
				component.position.y = parent.position.y + (parent.size.y - component.size.y) / 2;
			}
		}
	}

	public enum Alignment {
		HORIZONTAL, VERTICAL, BOTH
	}
}
