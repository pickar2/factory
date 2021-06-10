package xyz.sathro.factory.window.events;

import xyz.sathro.factory.event.events.Event;

public class KeyUpEvent extends Event {
	public int key;

	public KeyUpEvent(int key) {
		this.key = key;
	}
}
