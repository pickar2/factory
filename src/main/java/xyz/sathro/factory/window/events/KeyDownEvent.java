package xyz.sathro.factory.window.events;

import xyz.sathro.factory.event.events.Event;

public class KeyDownEvent extends Event {
	public int key;
	public int action;

	public KeyDownEvent(int key, int action) {
		this.key = key;
		this.action = action;
	}
}
