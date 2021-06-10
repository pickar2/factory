package xyz.sathro.factory.ui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector2i;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.ui.components.UIComponent;
import xyz.sathro.factory.ui.constraints.CenterConstraint;
import xyz.sathro.factory.ui.constraints.SetPositionConstraint;
import xyz.sathro.factory.ui.constraints.SetSizeConstraint;
import xyz.sathro.factory.ui.vector.UIVector;
import xyz.sathro.factory.ui.vector.UIVectorCoordinate;
import xyz.sathro.factory.ui.vector.UIVectorType;
import xyz.sathro.factory.vulkan.renderers.UIRenderer;
import xyz.sathro.factory.window.events.MouseMoveEvent;

import java.awt.*;
import java.util.List;
import java.util.Random;

import static xyz.sathro.factory.ui.vector.UIVectorCoordinate.BOTH;
import static xyz.sathro.factory.ui.vector.UIVectorCoordinate.X;
import static xyz.sathro.factory.ui.vector.UIVectorType.ABSOLUTE;
import static xyz.sathro.factory.ui.vector.UIVectorType.RELATIVE;

public class Interface {
	public static final Interface INSTANCE = new Interface();
	public List<UIComponent> componentList = new ObjectArrayList<>();
	public UIComponent root;
	@Getter private UIComponent currentComponent;

	private Interface() {
		EventManager.registerListeners(this);
	}

	private static Color getRandomColor() {
		final Random random = new Random();
		return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 255);
	}

	private static boolean isInsideComponent(Vector2i pos, UIComponent component) {
		return pos.x >= component.position.x && pos.x < component.position.x + component.size.x &&
		       pos.y >= component.position.y && pos.y < component.position.y + component.size.y;
	}

	@SubscribeEvent
	public void onMouseMove(MouseMoveEvent event) {
		UIComponent oldComponent = currentComponent;
		if (currentComponent != null) {
			currentComponent = findHoveredComponent(event.pos, currentComponent);
		} else {
			currentComponent = findHoveredComponent(event.pos, root);
		}

		if (currentComponent != null) {
			currentComponent.onHoverIn();
			UIRenderer.INSTANCE.updateDataBuffers();
		}
		if (currentComponent != oldComponent && oldComponent != null) {
			oldComponent.onHoverOut();
			UIRenderer.INSTANCE.updateDataBuffers();
		}
	}

	private UIComponent findHoveredComponent(Vector2i pos, UIComponent startComponent) {
		while (startComponent != null && !isInsideComponent(pos, startComponent)) {
			startComponent = startComponent.getParent();
		}

		loop:
		while (startComponent != null && !startComponent.children.isEmpty()) {
			for (UIComponent child : startComponent.children) {
				if (child.getBackgroundColor().getAlpha() != 0 && isInsideComponent(pos, child)) {
					startComponent = child;
					continue loop;
				}
			}
			break;
		}

		return startComponent;
	}

	public void init() {
		initTestUIGrid();
		componentList = addNodeToList(root, new ObjectArrayList<>());
		updateUIRenderer(true);
	}

	public List<UIComponent> addNodeToList(UIComponent component, List<UIComponent> list) {
		list.add(component);
		if (!component.children.isEmpty()) {
			for (UIComponent child : component.children) {
				addNodeToList(child, list);
			}
		}

		return list;
	}

	private void initTestUIGrid() {
		root = new UIComponent();
		final int count = 10;
		final int size = 50;
		final int spacing = 20;
		final int borderSize = 3;

		root.addConstraint(new SetPositionConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2i(spacing / 2))));
		root.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d((size + spacing) * count))));
		root.setBackgroundColor(new Color(0, 0, 0, 127));

		for (int i = 0; i < count; i++) {
			for (int j = 0; j < count; j++) {
				final Color color = getRandomColor();
				final UIComponent box = new UIComponent();
				box.setBackgroundColor(color);
				box.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(size))));
				box.addConstraint(new SetPositionConstraint(new UIVector<>(RELATIVE, BOTH, new Vector2i((size + spacing) * i + spacing / 2, (size + spacing) * j + spacing / 2))));

				final UIComponent boxBorder = new UIComponent();
				boxBorder.setBackgroundColor(new Color(0, true));
				boxBorder.addConstraint(new SetSizeConstraint(new UIVector<>(UIVectorType.ABSOLUTE, UIVectorCoordinate.BOTH, new Vector2d(size))));
				boxBorder.addConstraint(new SetPositionConstraint(new UIVector<>(UIVectorType.RELATIVE, UIVectorCoordinate.BOTH, new Vector2i((size + spacing) * i + spacing / 2, (size + spacing) * j + spacing / 2))));
				boxBorder.clipPos.set(borderSize);
				boxBorder.clipSize.set(size-borderSize*2);
				boxBorder.clipRounding = boxBorder.clipSize.x;
				boxBorder.rounding = boxBorder.clipSize.x;

				box.setOnHoverCallback(component -> {
					component.rounding = size;
					component.textureID = 0;
					component.setBackgroundColor(Color.WHITE);
					boxBorder.setBackgroundColor(color);
				}, component -> {
					component.rounding = 0;
					component.textureID = -1;
					component.setBackgroundColor(color);
					boxBorder.setBackgroundColor(new Color(0, true));
				});

				root.addComponent(box);
				root.addComponent(boxBorder);
			}
		}

		root.applyConstraints();
	}

	private void initTestUIRoundingAndClipping() {
		root = new UIComponent();
		root.setBackgroundColor(new Color(0, 0, 255, 127));
		root.addConstraint(new SetPositionConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2i(50, 50))));
		root.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(300, 300))));

		final UIComponent box = new UIComponent();
		box.setBackgroundColor(new Color(0, 255, 0, 127));
		box.addConstraint(new SetSizeConstraint(new UIVector<>(RELATIVE, BOTH, new Vector2d(0.5, 0.2))));
		box.addConstraint(new CenterConstraint(CenterConstraint.Alignment.VERTICAL));
		box.addConstraint(new SetPositionConstraint(new UIVector<>(RELATIVE, X, new Vector2i(200, 0))));

		root.addComponent(box);

		root.applyConstraints();

		root.rounding = 300;

		box.clipPos.set(box.position).add(20, 20);
		box.clipSize.set(30);
		box.clipRounding = 10;
	}

	public void updateUIRenderer(boolean texturesChanged) {
		UIRenderer.INSTANCE.rebuildUI(texturesChanged, componentList);
	}
}
