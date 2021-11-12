package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.window.MouseButton;

public class MouseClickEvent extends Event {
	public Vector2i pos;
	public MouseButton mouseButton;
	public Stage stage;

	public MouseClickEvent(Vector2i pos, MouseButton mouseButton, Stage stage) {
		this.pos = pos;
		this.mouseButton = mouseButton;
		this.stage = stage;
	}

	public enum Stage {
		PRESS, RELEASE
	}
}
