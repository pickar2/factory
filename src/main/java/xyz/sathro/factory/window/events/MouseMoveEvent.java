package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;

public class MouseMoveEvent extends Event {
	public Vector2i pos;

	public MouseMoveEvent(Vector2i pos) {
		this.pos = pos;
	}
}
