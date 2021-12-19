package xyz.sathro.factory.window.events;

import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;

public class MouseClickEvent extends Event {
	public Vector2i pos;
	public int mouseButton;
	public Stage stage;

	public MouseClickEvent(Vector2i pos, int mouseButton, Stage stage) {
		this.pos = pos;
		this.mouseButton = mouseButton;
		this.stage = stage;
	}

	public enum Stage {
		PRESS, RELEASE
	}
}
