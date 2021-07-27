package xyz.sathro.factory.event.events;

import java.lang.reflect.Type;

public abstract class GenericEvent<E> extends Event {
	private final Type type;

	public GenericEvent(Class<? extends E> type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
