package xyz.sathro.factory.window.events;

import xyz.sathro.factory.event.events.Event;

public class MouseScrollEvent extends Event {
	final int xOffset;
	final int yOffset;

	public MouseScrollEvent(int xOffset, int yOffset) {
		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}
}
