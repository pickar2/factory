package xyz.sathro.factory.event.events;

import java.lang.reflect.Type;

public abstract class GenericEvent extends Event {
	private final Type type;

	public GenericEvent(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
