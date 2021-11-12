package xyz.sathro.factory.ui.font;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import xyz.sathro.vulkan.Vulkan;
import xyz.sathro.vulkan.models.IDisposable;
import xyz.sathro.vulkan.models.VulkanImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public final class UIFont implements IDisposable {
	public static final FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, false);
	public final String charset;
	public final Font font;
	public final FontMetrics fontMetrics;
	private final Object2ObjectOpenHashMap<Character, UICharacter> characterCache = new Object2ObjectOpenHashMap<>(512);

	public UIFont(String charset) {
		this.charset = charset;
		this.font = new Font(charset, Font.PLAIN, 16);
		this.fontMetrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(font);
		createCharactersCache();
	}

	private void createCharactersCache() {
		final CharsetEncoder characterEncoder = Charset.forName(charset).newEncoder();
		for (char character = 0; character < 256; character++) {
			if (!characterEncoder.canEncode(character)) { continue; }
			getCharacter(character);
		}
	}

	private UICharacter createCharacter(Font font, Character character) {
		final VulkanImage texture = createCharacterTexture(font, String.valueOf(character));

		return new UICharacter(character, fontMetrics.charWidth(character), fontMetrics.getHeight(), texture);
	}

	private VulkanImage createCharacterTexture(Font font, String strCharacter) {
		final Rectangle2D bounds = font.getStringBounds(strCharacter, UIFont.frc);
		final int width = (int) Math.ceil(bounds.getWidth());
		final int height = (int) Math.ceil(bounds.getHeight());
		if (width == 0 || height == 0) {
			return null;
		}
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();

		graphics.setColor(Color.WHITE);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(font);

		graphics.drawString(strCharacter, 0, graphics.getFontMetrics().getAscent());

		graphics.dispose();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			out.flush();
			final byte[] data = out.toByteArray();
			final ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
			buf.put(data, 0, data.length);
			buf.flip();

			return Vulkan.createTextureFromBytes(buf);
		} catch (IOException exception) {
			throw new RuntimeException("Bytebuffer write error: " + exception);
		}
	}

	public UICharacter getCharacter(Character character) {
		if (characterCache.containsKey(character)) {
			return characterCache.get(character);
		}

		final UICharacter uiCharacter = createCharacter(this.font, character);
		characterCache.put(character, uiCharacter);

		return uiCharacter;
	}

	public void dispose() {
		for (UICharacter uiCharacter : characterCache.values()) {
			if (uiCharacter.texture() != null) {
				uiCharacter.texture().registerToDisposal();
			}
		}
	}
}
