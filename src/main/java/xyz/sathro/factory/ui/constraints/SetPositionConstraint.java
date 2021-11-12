package xyz.sathro.factory.ui.constraints;

import org.joml.Vector2i;
import xyz.sathro.factory.ui.components.BasicUIComponent;
import xyz.sathro.factory.ui.vector.UIVector;
import xyz.sathro.factory.ui.vector.UIVectorCoordinate;
import xyz.sathro.factory.ui.vector.UIVectorType;

public class SetPositionConstraint implements PositionConstraint {
	private final UIVector<Vector2i> positionVector;

	public SetPositionConstraint(UIVector<Vector2i> vector) {
		this.positionVector = vector;
	}

	@Override
	public void apply(BasicUIComponent component) {
		if (positionVector.type == UIVectorType.RELATIVE) {
			if (component.getParent() != null) {
				final BasicUIComponent parent = component.getParent();
				if (positionVector.coordinate == UIVectorCoordinate.X || positionVector.coordinate == UIVectorCoordinate.BOTH) {
					component.position.x = parent.position.x + positionVector.vec.x;
				}
				if (positionVector.coordinate == UIVectorCoordinate.Y || positionVector.coordinate == UIVectorCoordinate.BOTH) {
					component.position.y = parent.position.y + positionVector.vec.y;
				}
			}
		} else if (positionVector.type == UIVectorType.ABSOLUTE) {
			if (positionVector.coordinate == UIVectorCoordinate.X || positionVector.coordinate == UIVectorCoordinate.BOTH) {
				component.position.x = positionVector.vec.x;
			}
			if (positionVector.coordinate == UIVectorCoordinate.Y || positionVector.coordinate == UIVectorCoordinate.BOTH) {
				component.position.y = positionVector.vec.y;
			}
		}
	}
}
