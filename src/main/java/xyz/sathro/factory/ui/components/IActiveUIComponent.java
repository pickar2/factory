package xyz.sathro.factory.ui.components;

import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.factory.window.events.MouseMoveEvent;
import xyz.sathro.factory.window.events.MouseScrollEvent;

import java.util.function.BiConsumer;

public interface IActiveUIComponent<Component> {
	Component getComponent();

	BiConsumer<Component, MouseMoveEvent> getOnHoverInCallback();

	void setOnHoverInCallback(BiConsumer<Component, MouseMoveEvent> callback);

	BiConsumer<Component, MouseMoveEvent> getOnHoverOutCallback();

	void setOnHoverOutCallback(BiConsumer<Component, MouseMoveEvent> callback);

	BiConsumer<Component, MouseClickEvent> getOnClickCallback();

	void setOnClickCallback(BiConsumer<Component, MouseClickEvent> callback);

	BiConsumer<Component, MouseScrollEvent> getOnScrollCallback();

	void setOnScrollCallback(BiConsumer<Component, MouseScrollEvent> callback);

	default void onHoverIn(MouseMoveEvent event) {
		if (getOnHoverInCallback() != null) {
			getOnHoverInCallback().accept(getComponent(), event);
		}
	}

	default void onHoverOut(MouseMoveEvent event) {
		if (getOnHoverOutCallback() != null) {
			getOnHoverOutCallback().accept(getComponent(), event);
		}
	}

	default void onClick(MouseClickEvent event) {
		if (getOnClickCallback() != null) {
			getOnClickCallback().accept(getComponent(), event);
		}
	}

	default void onScroll(MouseScrollEvent event) {
		if (getOnScrollCallback() != null) {
			getOnScrollCallback().accept(getComponent(), event);
		}
	}
}
