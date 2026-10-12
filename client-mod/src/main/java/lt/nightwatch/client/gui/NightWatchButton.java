package lt.nightwatch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Flat button in NightWatch colors.
 */
public class NightWatchButton extends AbstractButton {
	private final Runnable action;
	private final int color;
	private final int hoverColor;
	private final int textColor;
	private final boolean filled;

	public NightWatchButton(int x, int y, int width, int height, Component message, Runnable action,
							int color, int hoverColor, int textColor, boolean filled) {
		super(x, y, width, height, message);
		this.action = action;
		this.color = color;
		this.hoverColor = hoverColor;
		this.textColor = textColor;
		this.filled = filled;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int right = x + getWidth();
		int bottom = y + getHeight();
		boolean hovered = isHoveredOrFocused();
		int current = hovered ? hoverColor : color;

		if (filled) {
			graphics.fill(x, y, right, bottom, current);
		} else {
			graphics.fill(x, y, right, bottom, hovered ? 0x55000000 | (current & 0x00FFFFFF) : 0x66000000);
			graphics.fill(x, y, right, y + 1, current);
			graphics.fill(x, bottom - 1, right, bottom, current);
			graphics.fill(x, y, x + 1, bottom, current);
			graphics.fill(right - 1, y, right, bottom, current);
		}

		var font = Minecraft.getInstance().font;
		graphics.drawCenteredString(font, getMessage(), x + getWidth() / 2, y + (getHeight() - 8) / 2, textColor);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
