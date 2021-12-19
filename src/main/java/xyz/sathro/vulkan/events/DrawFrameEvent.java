package xyz.sathro.vulkan.events;

import lombok.AllArgsConstructor;
import xyz.sathro.factory.event.events.Event;

@AllArgsConstructor
public class DrawFrameEvent extends Event {
	public final double lag;
}
