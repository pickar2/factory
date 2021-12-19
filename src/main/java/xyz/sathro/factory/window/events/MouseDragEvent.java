package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;

public class MouseDragEvent extends Event {
	public Vector2i pos;
	public Vector2i oldPos;
	public int mouseButton;

	public MouseDragEvent(Vector2i oldPos, Vector2i pos, int mouseButton) {
		this.oldPos = oldPos;
		this.pos = pos;
		this.mouseButton = mouseButton;
	}
}
