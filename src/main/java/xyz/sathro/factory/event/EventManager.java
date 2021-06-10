package xyz.sathro.factory.event;

import it.unimi.dsi.fastutil.objects.*;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.event.events.GenericEvent;
import xyz.sathro.factory.event.exceptions.EventDispatchException;
import xyz.sathro.factory.event.listeners.ListenerPriority;
import xyz.sathro.factory.logger.Logger;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EventManager {
	private static final Object2ObjectMap<Type, ObjectList<Listener>> registeredListeners = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

	private static final Object2ObjectMap<Type, Object2ObjectMap<Type, ObjectList<Listener>>> registeredGenericListeners = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

	private EventManager() {
	}

	public static void registerListeners(final Object listenerClassInstance) {
		final Method[] methods;
		boolean isStatic = false;

		if (listenerClassInstance instanceof Class) {
			isStatic = true;
			methods = ((Class<?>) listenerClassInstance).getMethods();
		} else {
			methods = listenerClassInstance.getClass().getMethods();
		}
		for (Method method : methods) {
			if (Modifier.isStatic(method.getModifiers()) != isStatic) {
				continue;
			}

			if (!method.isAnnotationPresent(SubscribeEvent.class)) {
				continue;
			}

			if (method.getParameterCount() != 1) {
				Logger.instance.error("Ignoring illegal event handler: " + method.getName() + ": Wrong number of arguments (required: 1)");
				continue;
			}

			if (!Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
				Logger.instance.error("Ignoring illegal event handler: " + method.getName() + ": Argument must extend " + Event.class.getName());
				continue;
			}

			ListenerPriority priority = method.getAnnotation(SubscribeEvent.class).priority();
			Listener listener = new Listener(listenerClassInstance, method, priority);

			Type eventType = method.getParameterTypes()[0];
			if (!method.getGenericParameterTypes()[0].equals(eventType)) {
				addGenericListener(eventType, ((ParameterizedType) method.getGenericParameterTypes()[0]).getActualTypeArguments()[0], listener);
			} else {
				addListener(eventType, listener);
			}
		}
	}

	private static void addListener(final Type eventType, final Listener listener) {
		if (!registeredListeners.containsKey(eventType)) {
			registeredListeners.put(eventType, ObjectLists.synchronize(new ObjectArrayList<>()));
		}

		registeredListeners.get(eventType).add(listener);
	}

	private static void addGenericListener(final Type eventType, final Type genericType, final Listener listener) {
		if (!registeredGenericListeners.containsKey(eventType)) {
			registeredGenericListeners.put(eventType, Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>()));
		}
		Object2ObjectMap<Type, ObjectList<Listener>> map = registeredGenericListeners.get(eventType);
		if (!map.containsKey(genericType)) {
			map.put(genericType, ObjectLists.synchronize(new ObjectArrayList<>()));
		}
		map.get(genericType).add(listener);
	}

	public static void unregisterListeners(final Object listenerClassInstance) {
		for (ObjectList<Listener> listenerList : registeredListeners.values()) {
			for (int i = 0; i < listenerList.size(); i++) {
				if (listenerList.get(i).listenerClassInstance == listenerClassInstance) {
					listenerList.remove(i);
					i -= 1;
				}
			}
		}
	}

	public static void unregisterListenersOfEvent(final Class<? extends Event> eventClass) {
		registeredListeners.get(eventClass).clear();
	}

	public static void callEvent(final Event event) {
		for (ListenerPriority priority : ListenerPriority.values()) {
			if (event.isCancelled()) {
				break;
			}
			dispatchEvent(event, priority);
		}
	}

	private static void dispatchEvent(final Event event, final ListenerPriority priority) {
		ObjectList<Listener> listeners = null;
		if (event instanceof GenericEvent) {
			Object2ObjectMap<Type, ObjectList<Listener>> map = registeredGenericListeners.get(event.getClass());
			if (map != null) {
				listeners = map.get(((GenericEvent) event).getType());
			}
		} else {
			listeners = registeredListeners.get(event.getClass());
		}
		if (listeners != null) {
			for (Listener listener : listeners) {
				if (event.isCancelled()) {
					break;
				}
				if (listener.priority == priority) {
					try {
						listener.listenerMethod.setAccessible(true);
						listener.listenerMethod.invoke(listener.listenerClassInstance, event);
					} catch (IllegalAccessException e) {
						Logger.instance.error("Could not access event handler method:");
						Logger.instance.error(Arrays.toString(e.getStackTrace()));
					} catch (InvocationTargetException e) {
						throw new EventDispatchException("Could not dispatch event to handler " + listener.listenerMethod.getName(), e);
					}
				}
			}
		}
	}

	public static List<Listener> getRegisteredListeners() {
		List<Listener> ret = new ArrayList<>();
		registeredListeners.forEach((aClass, listeners) -> ret.addAll(listeners));
		return ret;
	}

	public static class Listener {
		private final Object listenerClassInstance;
		private final Method listenerMethod;
		private final ListenerPriority priority;

		private Listener(final Object listenerClassInstance, final Method listenerMethod, final ListenerPriority priority) {
			this.listenerClassInstance = listenerClassInstance;
			this.listenerMethod = listenerMethod;
			this.priority = priority;
		}

		@Override
		public String toString() {
			return "Listener{" +
			       "listenerClassInstance=" + listenerClassInstance +
			       ", listenerMethod=" + listenerMethod +
			       ", priority=" + priority +
			       '}';
		}
	}
}
