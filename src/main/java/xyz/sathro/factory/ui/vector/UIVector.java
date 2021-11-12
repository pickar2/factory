package xyz.sathro.factory.ui.vector;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UIVector<Vector> {
	public UIVectorType type;
	public UIVectorCoordinate coordinate;

	public Vector vec;

	public UIVector(Vector vector) {
		this.vec = vector;
	}

	public static <V> Builder<V> builder(V vector) {
		return new Builder<>(vector);
	}

	public static class Builder<Vector> {
		UIVectorType type;
		UIVectorCoordinate coordinate;
		Vector vec;

		private Builder(Vector vector) {
			this.vec = vector;
		}

		public BuilderCoordinate absolute() {
			type = UIVectorType.ABSOLUTE;
			return Builder.this.new BuilderCoordinate();
		}

		public BuilderCoordinate relative() {
			type = UIVectorType.RELATIVE;
			return Builder.this.new BuilderCoordinate();
		}

		public class BuilderCoordinate {
			public UIVector<Vector> x() {
				coordinate = UIVectorCoordinate.X;
				return new UIVector<>(type, coordinate, vec);
			}

			public UIVector<Vector> y() {
				coordinate = UIVectorCoordinate.Y;
				return new UIVector<>(type, coordinate, vec);
			}

			public UIVector<Vector> both() {
				coordinate = UIVectorCoordinate.BOTH;
				return new UIVector<>(type, coordinate, vec);
			}
		}
	}
}
