package xyz.sathro.factory.ui.components;

import lombok.Getter;
import lombok.Setter;
import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.factory.window.events.MouseMoveEvent;
import xyz.sathro.factory.window.events.MouseScrollEvent;

import java.util.function.BiConsumer;

public class ActiveUIComponent extends BasicUIComponent implements IActiveUIComponent<ActiveUIComponent> {
	@Getter @Setter private BiConsumer<ActiveUIComponent, MouseMoveEvent> onHoverInCallback;
	@Getter @Setter private BiConsumer<ActiveUIComponent, MouseMoveEvent> onHoverOutCallback;
	@Getter @Setter private BiConsumer<ActiveUIComponent, MouseClickEvent> onClickCallback;
	@Getter @Setter private BiConsumer<ActiveUIComponent, MouseScrollEvent> onScrollCallback;

	@Override
	public ActiveUIComponent getComponent() {
		return this;
	}
}
