package xyz.sathro.factory.ui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector2i;
import xyz.sathro.factory.event.EventManager;
import xyz.sathro.factory.event.annotations.SubscribeEvent;
import xyz.sathro.factory.ui.components.ActiveUIComponent;
import xyz.sathro.factory.ui.components.BasicUIComponent;
import xyz.sathro.factory.ui.components.Label;
import xyz.sathro.factory.ui.constraints.CenterConstraint;
import xyz.sathro.factory.ui.constraints.SetPositionConstraint;
import xyz.sathro.factory.ui.constraints.SetSizeConstraint;
import xyz.sathro.factory.ui.font.UIFont;
import xyz.sathro.factory.ui.vector.UIVector;
import xyz.sathro.factory.vulkan.renderers.UIRenderer;
import xyz.sathro.factory.window.MouseButton;
import xyz.sathro.factory.window.events.MouseClickEvent;
import xyz.sathro.factory.window.events.MouseMoveEvent;
import xyz.sathro.factory.window.events.MouseScrollEvent;

import java.awt.*;
import java.util.List;
import java.util.Random;

import static xyz.sathro.factory.ui.vector.UIVectorCoordinate.BOTH;
import static xyz.sathro.factory.ui.vector.UIVectorCoordinate.X;
import static xyz.sathro.factory.ui.vector.UIVectorType.ABSOLUTE;
import static xyz.sathro.factory.ui.vector.UIVectorType.RELATIVE;

public class Interface {
	public static final Interface INSTANCE = new Interface();
	public List<BasicUIComponent> componentList = new ObjectArrayList<>();
	public BasicUIComponent root;
	@Getter private BasicUIComponent currentComponent;

	private Interface() {
		EventManager.registerListeners(this);
	}

	private static Color getRandomColor() {
		final Random random = new Random();
		return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 255);
	}

	private static boolean isInsideComponent(Vector2i pos, BasicUIComponent component) {
		return pos.x >= component.position.x && pos.x < component.position.x + component.size.x &&
		       pos.y >= component.position.y && pos.y < component.position.y + component.size.y;
	}

	@SubscribeEvent
	public void onMouseMove(MouseMoveEvent event) {
		final BasicUIComponent oldComponent = currentComponent;
		if (currentComponent != null) {
			currentComponent = findHoveredComponent(event.pos, currentComponent);
		} else {
			currentComponent = findHoveredComponent(event.pos, root);
		}

		if (currentComponent != oldComponent) {
			if (currentComponent instanceof ActiveUIComponent component) {
				component.onHoverIn(event);
				UIRenderer.INSTANCE.updateDataBuffers();
			}
			if (oldComponent instanceof ActiveUIComponent component) {
				component.onHoverOut(event);
				UIRenderer.INSTANCE.updateDataBuffers();
			}
		}
	}

	@SubscribeEvent
	public void onMouseClick(MouseClickEvent event) {
		if (event.stage != MouseClickEvent.Stage.RELEASE) { return; }
		if (currentComponent instanceof ActiveUIComponent component) {
			component.onClick(event);
			UIRenderer.INSTANCE.updateDataBuffers();
		}
	}

	@SubscribeEvent
	public void onMouseScroll(MouseScrollEvent event) {
		if (currentComponent instanceof ActiveUIComponent component) {
			component.onScroll(event);
			UIRenderer.INSTANCE.updateDataBuffers();
		}
	}

	private BasicUIComponent findHoveredComponent(Vector2i pos, BasicUIComponent startComponent) {
		while (startComponent != null && !isInsideComponent(pos, startComponent)) {
			startComponent = startComponent.getParent();
		}

		loop:
		while (startComponent != null && !startComponent.children.isEmpty()) {
			for (BasicUIComponent child : startComponent.children) {
				if (child.color.getAlpha() != 0 && isInsideComponent(pos, child)) {
					startComponent = child;
					continue loop;
				}
			}
			break;
		}

		return startComponent;
	}

	public void init() {
		root = new BasicUIComponent();
//		initTestUIText();
		initTestUIGrid();
		componentList = addNodeToList(root, new ObjectArrayList<>());
		updateUIRenderer(true);
	}

	public List<BasicUIComponent> addNodeToList(BasicUIComponent component, List<BasicUIComponent> list) {
		list.add(component);
		if (!component.children.isEmpty()) {
			for (BasicUIComponent child : component.children) {
				addNodeToList(child, list);
			}
		}

		return list;
	}

	private void initTestUIText() {
		root.addConstraint(new SetPositionConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2i(50))));
		root.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(250))));
		root.color = new Color(0, 0, 0, 127);

		final UIFont font = new UIFont("UTF-8");

		final Label label = new Label(font, "Hello world!");

		label.addConstraint(new SetPositionConstraint(UIVector.builder(new Vector2i(5)).relative().both()));

		root.addComponent(label);

		root.applyConstraints();

		font.registerToLateDisposal();
	}

	private void initTestUIGrid() {
		final int count = 10;
		final int size = 50;
		final int spacing = 20;
		final int borderSize = 3;

		root.addConstraint(new SetPositionConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2i(spacing / 2))));
		root.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d((size + spacing) * count))));
		root.color = new Color(0, 0, 0, 127);

		for (int i = 0; i < count; i++) {
			for (int j = 0; j < count; j++) {
				final int posX = (size + spacing) * i + spacing / 2;
				final int posY = (size + spacing) * j + spacing / 2;
				final Color color = getRandomColor();

				final ActiveUIComponent box = new ActiveUIComponent();
				box.color = color;
				box.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(size))));
				box.addConstraint(new SetPositionConstraint(new UIVector<>(RELATIVE, BOTH, new Vector2i(posX, posY))));

				final ActiveUIComponent boxBorder = new ActiveUIComponent();
				boxBorder.color = new Color(0, true);
				boxBorder.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(size))));
				boxBorder.addConstraint(new SetPositionConstraint(new UIVector<>(RELATIVE, BOTH, new Vector2i(posX, posY))));
				boxBorder.clipPos.set(borderSize);
				boxBorder.clipSize.set(size - borderSize * 2);
				boxBorder.clipRounding = boxBorder.clipSize.x;
				boxBorder.rounding = boxBorder.clipSize.x;

				box.setOnHoverInCallback((component, event) -> {
					component.rounding = size;
					component.textureID = 1;
					component.color = Color.WHITE;
					boxBorder.color = color;

					component.zIndex = 200;
				});

				box.setOnHoverOutCallback((component, event) -> {
					component.rounding = 0;
					component.textureID = 0;
					component.color = color;
					boxBorder.color = new Color(0, true);

					component.zIndex = 0;
				});

				box.setOnClickCallback((component, event) -> {
					if (event.mouseButton != MouseButton.LEFT) { return; }
					if (boxBorder.color.equals(color)) {
						boxBorder.color = getRandomColor();
					} else {
						boxBorder.color = color;
					}
				});

				box.setOnScrollCallback((component, event) -> {
					final int offset = event.yOffset * 4;

					component.size.x += offset * 2;
					component.size.y += offset * 2;

					component.position.x -= offset;
					component.position.y -= offset;
				});

				root.addComponent(box);
				root.addComponent(boxBorder);
			}
		}

		root.applyConstraints();
	}

	private void initTestUIRoundingAndClipping() {
		root = new BasicUIComponent();
		root.color = new Color(0, 0, 255, 127);
		root.addConstraint(new SetPositionConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2i(50, 50))));
		root.addConstraint(new SetSizeConstraint(new UIVector<>(ABSOLUTE, BOTH, new Vector2d(300, 300))));

		final BasicUIComponent box = new BasicUIComponent();
		box.color = new Color(0, 255, 0, 127);
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
