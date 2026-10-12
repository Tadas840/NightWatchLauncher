package lt.nightwatch.client.gui;

import lt.nightwatch.client.NightWatchConfig;
import lt.nightwatch.client.ServerEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Random;

/**
 * NightWatch main menu. Replaces the vanilla title screen.
 */
public class NightWatchTitleScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 24;
	private static final int BUTTON_GAP = 6;
	private static final int STAR_COUNT = 140;

	private static ConfigImage logo;
	private static ConfigImage background;
	private static boolean imagesLoaded;

	private final NightWatchConfig config = NightWatchConfig.get();
	private final float[] starX = new float[STAR_COUNT];
	private final float[] starY = new float[STAR_COUNT];
	private final float[] starPhase = new float[STAR_COUNT];
	private float time;

	public NightWatchTitleScreen() {
		super(Component.literal(NightWatchConfig.get().serverName));
		Random random = new Random(1337);
		for (int i = 0; i < STAR_COUNT; i++) {
			starX[i] = random.nextFloat();
			starY[i] = random.nextFloat() * 0.75f;
			starPhase[i] = random.nextFloat() * 6.28f;
		}
	}

	@Override
	protected void init() {
		if (!imagesLoaded) {
			imagesLoaded = true;
			logo = ConfigImage.load(config.logoImage, "logo");
			background = ConfigImage.load(config.backgroundImage, "background");
		}

		int accent = NightWatchConfig.color(config.accentColor, 0xFF1E88E5);
		int accentHover = NightWatchConfig.color(config.accentHoverColor, 0xFF42A5F5);
		int text = NightWatchConfig.color(config.textColor, 0xFFFFFFFF);

		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 + 4;

		addRenderableWidget(new NightWatchButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT + 8,
				Component.literal(config.playButton), this::connect, accent, accentHover, text, true));
		y += BUTTON_HEIGHT + 8 + BUTTON_GAP;

		if (config.showSingleplayer) {
			addRenderableWidget(new NightWatchButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
					Component.literal(config.singleplayerButton), () -> minecraft.setScreen(new SelectWorldScreen(this)),
					accent, accentHover, text, false));
			y += BUTTON_HEIGHT + BUTTON_GAP;
		}
		if (config.showMultiplayer) {
			addRenderableWidget(new NightWatchButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
					Component.literal(config.multiplayerButton), () -> minecraft.setScreen(new JoinMultiplayerScreen(this)),
					accent, accentHover, text, false));
			y += BUTTON_HEIGHT + BUTTON_GAP;
		}

		int half = (BUTTON_WIDTH - BUTTON_GAP) / 2;
		addRenderableWidget(new NightWatchButton(x, y, half, BUTTON_HEIGHT,
				Component.literal(config.optionsButton), () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)),
				accent, accentHover, text, false));
		addRenderableWidget(new NightWatchButton(x + half + BUTTON_GAP, y, half, BUTTON_HEIGHT,
				Component.literal(config.quitButton), minecraft::stop, accent, accentHover, text, false));

		// Optional link buttons in the bottom right corner.
		int linkX = width - 8;
		if (!config.discordUrl.isBlank()) {
			linkX -= 70;
			String url = config.discordUrl;
			addRenderableWidget(new NightWatchButton(linkX, height - 26, 70, 18,
					Component.literal(config.discordButton), () -> Util.getPlatform().openUri(url),
					accent, accentHover, text, false));
			linkX -= 6;
		}
		if (!config.websiteUrl.isBlank()) {
			linkX -= 70;
			String url = config.websiteUrl;
			addRenderableWidget(new NightWatchButton(linkX, height - 26, 70, 18,
					Component.literal(config.websiteButton), () -> Util.getPlatform().openUri(url),
					accent, accentHover, text, false));
		}
	}

	private void connect() {
		String address = config.serverAddress;
		ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address),
				ServerEntry.get(minecraft), false, null);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (background != null) {
			// Cover the screen while keeping the aspect ratio.
			float scale = Math.max((float) width / background.width(), (float) height / background.height());
			int w = Math.round(background.width() * scale);
			int h = Math.round(background.height() * scale);
			graphics.blit(RenderPipelines.GUI_TEXTURED, background.id(), (width - w) / 2, (height - h) / 2,
					0, 0, w, h, w, h);
			graphics.fill(0, 0, width, height, 0x66000000);
		} else {
			graphics.fillGradient(0, 0, width, height,
					NightWatchConfig.color(config.backgroundTopColor, 0xFF05070F),
					NightWatchConfig.color(config.backgroundBottomColor, 0xFF131A33));
		}

		if (config.stars) {
			time += partialTick * 0.05f;
			for (int i = 0; i < STAR_COUNT; i++) {
				float twinkle = 0.5f + 0.5f * (float) Math.sin(time + starPhase[i]);
				int alpha = 60 + (int) (twinkle * 195);
				int sx = (int) (starX[i] * width);
				int sy = (int) (starY[i] * height);
				int size = i % 9 == 0 ? 2 : 1;
				graphics.fill(sx, sy, sx + size, sy + size, (alpha << 24) | 0xFFFFFF);
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		int titleBottom = height / 2 - 14;
		if (logo != null) {
			int maxW = Math.min(width - 40, 360);
			int maxH = height / 2 - 40;
			float scale = Math.min((float) maxW / logo.width(), (float) maxH / logo.height());
			int w = Math.round(logo.width() * scale);
			int h = Math.round(logo.height() * scale);
			graphics.blit(RenderPipelines.GUI_TEXTURED, logo.id(), (width - w) / 2, titleBottom - h,
					0, 0, w, h, w, h);
		} else {
			float scale = 4.0f;
			int textWidth = font.width(config.serverName);
			while (scale > 1.0f && textWidth * scale > width - 40) {
				scale -= 0.5f;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0f, titleBottom - 8 * scale - 14);
			graphics.pose().scale(scale, scale);
			graphics.drawCenteredString(font, config.serverName, 0, 0, NightWatchConfig.color(config.textColor, 0xFFFFFFFF));
			graphics.pose().popMatrix();
		}

		if (!config.subtitle.isBlank()) {
			graphics.drawCenteredString(font, config.subtitle, width / 2, titleBottom - 6,
					NightWatchConfig.color(config.subtitleColor, 0xFF9FB3D9));
		}

		if (!config.footerText.isBlank()) {
			graphics.drawString(font, config.footerText, 8, height - 14, 0x99FFFFFF);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
