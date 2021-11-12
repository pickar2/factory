package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.window.MouseButton;

public class MouseDragEvent extends Event {
	public Vector2i pos;
	public Vector2i oldPos;
	public MouseButton mouseButton;

	public MouseDragEvent(Vector2i oldPos, Vector2i pos, MouseButton mouseButton) {
		this.oldPos = oldPos;
		this.pos = pos;
		this.mouseButton = mouseButton;
	}
}
