package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.window.MouseButton;

public class MouseDragEvent extends Event {
	public Vector2i location;
	public Vector2i oldLocation;
	public MouseButton mouseButton;

	public MouseDragEvent(Vector2i oldLocation, Vector2i location, MouseButton mouseButton) {
		this.oldLocation = oldLocation;
		this.location = location;
		this.mouseButton = mouseButton;
	}
}
