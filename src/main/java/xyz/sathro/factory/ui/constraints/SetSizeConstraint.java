package xyz.sathro.factory.ui.constraints;

import org.joml.Vector2d;
import xyz.sathro.factory.ui.components.UIComponent;
import xyz.sathro.factory.ui.vector.UIVector;
import xyz.sathro.factory.ui.vector.UIVectorCoordinate;
import xyz.sathro.factory.ui.vector.UIVectorType;
import xyz.sathro.factory.util.Maths;

public class SetSizeConstraint implements SizeConstraint {
	private final UIVector<Vector2d> sizeVector;

	public SetSizeConstraint(UIVector<Vector2d> sizeVector) {
		this.sizeVector = sizeVector;
	}

	@Override
	public void apply(UIComponent component) {
		if (sizeVector.type == UIVectorType.RELATIVE) {
			if (component.getParent() != null) {
				final UIComponent parent = component.getParent();
				if (sizeVector.coordinate == UIVectorCoordinate.X || sizeVector.coordinate == UIVectorCoordinate.BOTH) {
					component.size.x = Maths.floor(parent.size.x * sizeVector.vec.x);
				}
				if (sizeVector.coordinate == UIVectorCoordinate.Y || sizeVector.coordinate == UIVectorCoordinate.BOTH) {
					component.size.y = Maths.floor(parent.size.y * sizeVector.vec.y);
				}
			}
		} else if (sizeVector.type == UIVectorType.ABSOLUTE) {
			if (sizeVector.coordinate == UIVectorCoordinate.X || sizeVector.coordinate == UIVectorCoordinate.BOTH) {
				component.size.x = Maths.floor(sizeVector.vec.x);
			}
			if (sizeVector.coordinate == UIVectorCoordinate.Y || sizeVector.coordinate == UIVectorCoordinate.BOTH) {
				component.size.y = Maths.floor(sizeVector.vec.y);
			}
		}
	}
}
