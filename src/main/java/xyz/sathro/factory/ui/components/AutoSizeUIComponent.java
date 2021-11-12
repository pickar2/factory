package xyz.sathro.factory.ui.components;

public class AutoSizeUIComponent extends BasicUIComponent {
	@Override
	public void applyConstraints() {
		super.applyConstraints();

		int biggestIncreaseX = 0;
		int biggestIncreaseY = 0;

		for (BasicUIComponent child : children) {
			biggestIncreaseX = Math.max(biggestIncreaseX, child.position.x - this.position.x + child.size.x);
			biggestIncreaseY = Math.max(biggestIncreaseY, child.position.y - this.position.y + child.size.y);
		}

		this.size.x = biggestIncreaseX;
		this.size.y = biggestIncreaseY;
	}
}
