package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.window.MouseButton;

public class MouseClickEvent extends Event {
	public Vector2i location;
	public MouseButton mouseButton;

	public MouseClickEvent(Vector2i location, MouseButton mouseButton) {
		this.location = location;
		this.mouseButton = mouseButton;
	}
}
