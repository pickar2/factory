package xyz.sathro.factory.window.events;

import lombok.AllArgsConstructor;
import org.joml.Vector2i;
import xyz.sathro.factory.event.events.Event;

@AllArgsConstructor
public class MouseMoveEvent extends Event {
	public final Vector2i pos;
	public final Vector2i delta;
}
