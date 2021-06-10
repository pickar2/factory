package xyz.sathro.factory.ui.vector;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UIVector<Vector> {
	public UIVectorType type;
	public UIVectorCoordinate coordinate;

	public Vector vec;
}
