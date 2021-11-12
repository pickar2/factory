package xyz.sathro.factory.ui.components;

import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector2i;
import xyz.sathro.factory.ui.constraints.SetPositionConstraint;
import xyz.sathro.factory.ui.constraints.SetSizeConstraint;
import xyz.sathro.factory.ui.font.UICharacter;
import xyz.sathro.factory.ui.font.UIFont;
import xyz.sathro.factory.ui.vector.UIVector;

import java.awt.*;
import java.util.Random;

public class Label extends BasicUIComponent {
	//	private final List<BasicUIComponent> words = new ObjectArrayList<>();
	@Getter private String text;
	@Getter private UIFont font;
	@Getter private int characterPadding = 1;

	public Label(UIFont font, String text) {
		this.font = font;
		this.text = text;
		updateText();

		color = Color.BLACK;
	}

	public Label(UIFont font) {
		this.font = font;
		this.text = "";
		updateText();

		color = Color.BLACK;
	}

	public void setText(String text) {
		this.text = text;
		updateText();
	}

	public void setFont(UIFont font) {
		this.font = font;
		updateText();
	}

	public void setCharacterPadding(int characterPadding) {
		this.characterPadding = characterPadding;
		updateText();
	}

	public void updateText() {
		children.clear();
//		words.clear();

		int padding;
		int wordPadding = 0;
		UICharacter uiCharacter;
		BasicUIComponent component;
		BasicUIComponent wordComponent;
		final int spaceWidth = font.fontMetrics.charWidth(' ');
		for (String word : text.split(" ")) {
			padding = 0;
			wordComponent = new AutoSizeUIComponent();
			wordComponent.color = new Color(0, true);
			wordComponent.addConstraint(new SetPositionConstraint(UIVector.builder(new Vector2i(wordPadding, new Random().nextInt(50))).relative().both()));

			for (String s : word.split("")) {
				uiCharacter = font.getCharacter(s.charAt(0));
				component = new BasicUIComponent();

				component.addConstraint(new SetPositionConstraint(UIVector.builder(new Vector2i(padding, 0)).relative().both()));
				component.addConstraint(new SetSizeConstraint(UIVector.builder(new Vector2d(uiCharacter.width(), uiCharacter.height())).absolute().both()));
				component.texture = uiCharacter.texture();
				component.hasTexture = true;

				wordComponent.addComponent(component);

				padding += uiCharacter.width() + characterPadding;
			}

			addComponent(wordComponent);
			wordPadding += spaceWidth + padding;
		}
	}

	@Override
	public void applyConstraints() {

		super.applyConstraints();
	}
}
