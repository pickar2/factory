package xyz.sathro.factory.window.events;

import lombok.AllArgsConstructor;
import xyz.sathro.factory.event.events.Event;

@AllArgsConstructor
public class GameUpdateEvent extends Event {
	public final double lag;
}
