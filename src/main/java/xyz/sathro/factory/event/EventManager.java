package xyz.sathro.factory.event;

import it.unimi.dsi.fastutil.objects.*;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.event.events.GenericEvent;
import xyz.sathro.factory.event.listeners.ListenerPriority;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.function.Consumer;

@SuppressWarnings({ "unchecked", "rawtypes" })
@Log4j2
public final class EventManager {
	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private static final MethodType EVENT_CONSUMER_TYPE = MethodType.methodType(void.class, Event.class);
	private static final MethodType STATIC_INVOKE_TYPE = MethodType.methodType(EventConsumer.class);
	private static final String METHOD_NAME = "accept";

	private static final Object2ObjectMap<Class<Event>, ObjectList<Listener<Event>>> REGISTERED_LISTENERS = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
	private static final Object2ObjectMap<Class<Event>, Object2ObjectMap<Type, ObjectList<Listener<Event>>>> REGISTERED_GENERIC_LISTENERS = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());

	private EventManager() { }

	// helps to insert methods in list based on their priority
	private static <E extends Comparable<E>> void binaryInsert(final ObjectList<E> list, final E element) {
		int position = Collections.binarySearch(list, element);
		if (position < 0) {
			position = -(position + 1);
		}
		list.add(position, element);
	}

	private static EventConsumer getConsumer(final Method method, final Object caller) {
		try {
			final MethodHandle mh = LOOKUP.unreflect(method);
			final MethodType mhType = MethodType.methodType(void.class, mh.type().parameterType(1));

			return (EventConsumer) LambdaMetafactory.metafactory(LOOKUP, METHOD_NAME, STATIC_INVOKE_TYPE.appendParameterTypes(caller.getClass()), EVENT_CONSUMER_TYPE, mh, mhType).getTarget().invoke(caller);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private static EventConsumer getStaticConsumer(final Method method) {
		try {
			final MethodHandle mh = LOOKUP.unreflect(method);
			final MethodType mhType = MethodType.methodType(void.class, mh.type().parameterType(0));

			return (EventConsumer) LambdaMetafactory.metafactory(LOOKUP, METHOD_NAME, STATIC_INVOKE_TYPE, EVENT_CONSUMER_TYPE, mh, mhType).getTarget().invokeExact();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public static void registerListeners(final Object caller) {
		final Method[] methods = caller.getClass().getMethods();
		for (Method method : methods) {
			if (Modifier.isStatic(method.getModifiers())) { continue; }
			if (!method.isAnnotationPresent(SubscribeEvent.class)) { continue; }
			if (method.getParameterCount() != 1) { continue; }
			if (!Event.class.isAssignableFrom(method.getParameterTypes()[0])) { continue; }

			registerMethod(method, Listener.create(caller, method, method.getAnnotation(SubscribeEvent.class).priority()));
		}
	}

	public static void registerListeners(final Class<?> caller) {
		final Method[] methods = caller.getMethods();
		for (Method method : methods) {
			if (!Modifier.isStatic(method.getModifiers())) { continue; }
			if (!method.isAnnotationPresent(SubscribeEvent.class)) { continue; }
			if (method.getParameterCount() != 1) { continue; }
			if (!Event.class.isAssignableFrom(method.getParameterTypes()[0])) { continue; }

			registerMethod(method, Listener.createStatic(caller, method, method.getAnnotation(SubscribeEvent.class).priority()));
		}
	}

	private static void registerMethod(final Method method, final Listener<Event> listener) {
		final Class<Event> eventType = (Class<Event>) method.getParameterTypes()[0];
		final Type parameterType = method.getGenericParameterTypes()[0];
		if (!parameterType.equals(eventType)) {
			addGenericListener(eventType, ((ParameterizedType) parameterType).getActualTypeArguments()[0], listener);
		} else {
			addListener(eventType, listener);
		}
	}

	public static <E extends Event> void registerMethodDirect(final Class<E> eventClass, final EventConsumer<E> consumer, final ListenerPriority priority) {
		addListener((Class<Event>) eventClass, new Listener<>(EventManager.class, consumer, priority));
	}

	public static <E extends Event> void registerGenericMethodDirect(final Class<E> eventClass, final Class<?> genericType, final EventConsumer<E> consumer, final ListenerPriority priority) {
		addGenericListener((Class<Event>) eventClass, genericType, new Listener<>(EventManager.class, consumer, priority));
	}

	private static void addListener(final Class<Event> eventType, final Listener listener) {
		final ObjectList<Listener<Event>> list = REGISTERED_LISTENERS.computeIfAbsent(eventType, k -> ObjectLists.synchronize(new ObjectArrayList<>()));
		binaryInsert(list, listener);
	}

	private static void addGenericListener(final Class<Event> eventType, final Type genericType, final Listener listener) {
		final Object2ObjectMap<Type, ObjectList<Listener<Event>>> map = REGISTERED_GENERIC_LISTENERS.computeIfAbsent(eventType, k -> Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>()));
		final ObjectList<Listener<Event>> list = map.computeIfAbsent(genericType, k -> ObjectLists.synchronize(new ObjectArrayList<>()));

		binaryInsert(list, listener);
	}

	public static void unregisterListeners(final Object caller) {
		for (ObjectList<Listener<Event>> list : REGISTERED_LISTENERS.values()) {
			list.removeIf(listener -> listener.caller == caller);
		}

		for (Object2ObjectMap<Type, ObjectList<Listener<Event>>> map : REGISTERED_GENERIC_LISTENERS.values()) {
			for (ObjectList<Listener<Event>> list : map.values()) {
				list.removeIf(listener -> listener.caller == caller);
			}
		}
	}

	public static void unregisterListenersOfEvent(final Class<? extends Event> eventClass) {
		final ObjectList<Listener<Event>> listeners = REGISTERED_LISTENERS.get(eventClass);
		if (listeners != null) {
			listeners.clear();
		}
	}

	public static void unregisterListenersOfGenericEvent(final Class<? extends GenericEvent> eventClass, final Type genericType) {
		final Object2ObjectMap<Type, ObjectList<Listener<Event>>> map = REGISTERED_GENERIC_LISTENERS.get(eventClass);
		if (map != null) {
			final ObjectList<Listener<Event>> listeners = map.get(genericType);
			if (listeners != null) {
				listeners.clear();
			}
		}
	}

	public static void unregisterAllListenersOfGenericEvent(final Class<? extends GenericEvent> eventClass) {
		final Object2ObjectMap<Type, ObjectList<Listener<Event>>> map = REGISTERED_GENERIC_LISTENERS.get(eventClass);
		if (map != null) {
			map.values().forEach(ObjectList::clear);
		}
	}

	public static void callEvent(final Event event) {
//		log.info("Calling {} from {}", event.getClass().getSimpleName(), Thread.currentThread());
		if (event.isCancelled()) { return; }

		final ObjectList<Listener<Event>> listeners = REGISTERED_LISTENERS.get(event.getClass());
		if (listeners != null) {
			for (Listener<Event> listener : listeners) {
				if (event.isCancelled()) { return; }

				listener.consumer.accept(event);
			}
		}
	}

	public static void callEvent(final GenericEvent<?> event) {
//		log.info("Calling {}<{}> from {}", event.getClass().getSimpleName(), event.getType().getTypeName(), Thread.currentThread());
		if (event.isCancelled()) { return; }

		final Object2ObjectMap<Type, ObjectList<Listener<Event>>> map = REGISTERED_GENERIC_LISTENERS.get(event.getClass());
		if (map != null) {
			final ObjectList<Listener<Event>> listeners = map.get(event.getType());

			if (listeners != null) {
				for (Listener<Event> listener : listeners) {
					if (event.isCancelled()) { return; }

					listener.consumer.accept(event);
				}
			}
		}
	}

	public static ObjectList<Listener<Event>> getAllRegisteredListeners() {
		final ObjectList<Listener<Event>> ret = new ObjectArrayList<>();

		REGISTERED_LISTENERS.values().forEach(ret::addAll);
		REGISTERED_GENERIC_LISTENERS.values().forEach(map -> map.values().forEach(ret::addAll));

		return ret;
	}

	@ToString
	@AllArgsConstructor
	public static class Listener<E extends Event> implements Comparable<Listener<E>> {
		public final Object caller;
		public final EventConsumer<E> consumer;
		public final ListenerPriority priority;

		public static <E extends Event> Listener<E> create(final Object caller, final Method method, final ListenerPriority priority) {
			return new Listener<>(caller, getConsumer(method, caller), priority);
		}

		public static <E extends Event> Listener<E> createStatic(final Object caller, final Method method, final ListenerPriority priority) {
			return new Listener<>(caller, getStaticConsumer(method), priority);
		}

		@Override
		public int compareTo(@NotNull final EventManager.Listener o) {
			return priority.compareTo(o.priority);
		}
	}

	@FunctionalInterface
	public interface EventConsumer<E extends Event> extends Consumer<E> {
		void accept(final E event);
	}
}
