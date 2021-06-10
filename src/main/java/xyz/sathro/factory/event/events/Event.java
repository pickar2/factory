package xyz.sathro.factory.event.events;

public abstract class Event {
	private boolean cancelled = false;

	public boolean isCancelled() {
		return this.cancelled;
	}

	public void setCancelled(final boolean cancelled) {
		this.cancelled = cancelled;
	}
}
