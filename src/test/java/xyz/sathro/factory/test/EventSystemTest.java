package xyz.sathro.factory.test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.event.events.Event;
import xyz.sathro.factory.event.events.GenericEvent;
import xyz.sathro.factory.event.listeners.ListenerPriority;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventSystemTest {
	private static final EventHandlers handlers = new EventHandlers();

	@BeforeEach
	public void before() {
		EventManager.registerListeners(EventHandlers.class);
		EventManager.registerListeners(handlers);
	}

	@AfterEach
	public void after() {
		EventManager.unregisterListeners(EventHandlers.class);
		EventManager.unregisterListeners(handlers);
	}

	@Test
	public void testBasicEvents() {
		final TestEvent testEvent = new TestEvent(0);

		EventManager.callEvent(testEvent);

		assertEquals(IntList.of(0, 5, 3, 1), testEvent.dataStorage);
		assertEquals(11, EventManager.getAllRegisteredListeners().stream().mapToInt(l -> l.priority.ordinal()).reduce(Integer::sum).orElse(-1));
	}

	@Test
	public void testGenericEvents() {
		final GenericTestEvent<Object> testEventObject = new GenericTestEvent<>(Object.class, 2);
		final GenericTestEvent<Event> testEventEvent = new GenericTestEvent<>(Event.class, 7);

		EventManager.callEvent(testEventObject);
		EventManager.callEvent(testEventEvent);

		assertEquals(IntList.of(2, 6), testEventObject.dataStorage);
		assertEquals(IntList.of(7, 0), testEventEvent.dataStorage);
		assertEquals(11, EventManager.getAllRegisteredListeners().stream().mapToInt(l -> l.priority.ordinal()).reduce(Integer::sum).orElse(-1));
	}

	@Test
	public void testDirectEvent() {
		final TestEvent2 testEvent = new TestEvent2(42);

		EventManager.registerMethodDirect(TestEvent2.class, (e) -> e.addData(12), ListenerPriority.LOWEST);
		EventManager.registerMethodDirect(TestEvent2.class, (e) -> e.addData(52), ListenerPriority.HIGHEST);

		EventManager.callEvent(testEvent);

		assertEquals(IntList.of(42, 52, 12), testEvent.dataStorage);
		assertEquals(15, EventManager.getAllRegisteredListeners().stream().mapToInt(l -> l.priority.ordinal()).reduce(Integer::sum).orElse(-1));

		EventManager.unregisterListenersOfEvent(TestEvent2.class);
	}

	@Test
	public void testUnregisteredEvents() {
		EventManager.unregisterListeners(EventHandlers.class);
		EventManager.unregisterListeners(handlers);

		final TestEvent testEvent = new TestEvent(0);
		EventManager.callEvent(testEvent);

		assertEquals(IntList.of(0), testEvent.dataStorage);
	}

	@Test
	public void testUnregisterNonExisting() {
		EventManager.unregisterListeners(null);
		EventManager.unregisterListeners(new Object());
		EventManager.unregisterListenersOfEvent(null);
		EventManager.unregisterListenersOfEvent(TestEvent.class);
		EventManager.unregisterAllListenersOfGenericEvent(GenericTestEvent.class);
		EventManager.unregisterListenersOfGenericEvent(GenericTestEvent.class, null);
		EventManager.unregisterListenersOfGenericEvent(GenericTestEvent.class, Object.class);
		EventManager.unregisterListenersOfGenericEvent(null, null);
	}

	public static class TestEvent extends Event {
		private final IntList dataStorage = new IntArrayList();

		public TestEvent(int data) {
			addData(data);
		}

		public void addData(int data) {
			dataStorage.add(data);
		}
	}

	public static class TestEvent2 extends Event {
		private final IntList dataStorage = new IntArrayList();

		public TestEvent2(int data) {
			addData(data);
		}

		public void addData(int data) {
			dataStorage.add(data);
		}
	}

	public static class GenericTestEvent<E> extends GenericEvent<E> {
		private final IntList dataStorage = new IntArrayList();

		public GenericTestEvent(Class<? extends E> type, int data) {
			super(type);
			addData(data);
		}

		public void addData(int data) {
			dataStorage.add(data);
		}
	}

	public static class EventHandlers {
		@SubscribeEvent
		public static void onBasicEventStaticSetThree(TestEvent event) {
			event.addData(3);
		}

		@SubscribeEvent(priority = ListenerPriority.LOWEST)
		public void onBasicEventSetSevenCancelled(TestEvent event) {
			event.addData(7);
		}

		@SubscribeEvent(priority = ListenerPriority.LOW)
		public void onBasicEventSetOne(TestEvent event) {
			event.addData(1);
			event.setCancelled(true);
		}

		@SubscribeEvent(priority = ListenerPriority.HIGHEST)
		public void onBasicEventSetFive(TestEvent event) {
			event.addData(5);
		}

		@SubscribeEvent
		public void onObjectGenericEventSetSix(GenericTestEvent<Object> event) {
			event.addData(6);
		}

		@SubscribeEvent(priority = ListenerPriority.HIGHEST)
		public void onEventGenericEventSetZero(GenericTestEvent<Event> event) {
			event.addData(0);
		}
	}
}
