package xyz.sathro.factory.event.exceptions;

import java.lang.reflect.InvocationTargetException;

public class EventDispatchException extends RuntimeException {
	public EventDispatchException(final String message, final InvocationTargetException cause) {
		super(message, cause);
	}
}
