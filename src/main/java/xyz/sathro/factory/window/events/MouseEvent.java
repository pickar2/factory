package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;

public class MouseEvent extends Event {
	public Vector2i location;

	public MouseEvent(Vector2i location) {
		this.location = location;
	}
}
