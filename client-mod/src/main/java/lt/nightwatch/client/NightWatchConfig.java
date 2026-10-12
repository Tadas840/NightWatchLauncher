package lt.nightwatch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Editable client settings, stored in .minecraft/config/nightwatch/client.json.
 * The launcher ships this file, so edit nightwatch/client/client.json in the launcher repository.
 */
public class NightWatchConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Window title of the game. */
	public String windowTitle = "NightWatch";
	/** Big title shown on the main menu (used when no logo image is set). */
	public String serverName = "NightWatch";
	/** Line shown under the title. */
	public String subtitle = "Survival serveris";
	/** Server the play button connects to. */
	public String serverAddress = "26.139.249.47:25565";

	public String playButton = "ŽAISTI";
	public String optionsButton = "NUSTATYMAI";
	public String quitButton = "IŠEITI";
	public boolean showSingleplayer = false;
	public String singleplayerButton = "VIENAS ŽAIDĖJAS";
	public boolean showMultiplayer = false;
	public String multiplayerButton = "SERVERIAI";

	/** Optional links shown as small buttons at the bottom. Leave empty to hide. */
	public String websiteUrl = "";
	public String websiteButton = "SVETAINĖ";
	public String discordUrl = "";
	public String discordButton = "DISCORD";

	/** Text in the bottom left corner. */
	public String footerText = "NightWatch © 2026";

	/** Colors: #RRGGBB or #AARRGGBB. */
	public String accentColor = "#1E88E5";
	public String accentHoverColor = "#42A5F5";
	public String backgroundTopColor = "#05070F";
	public String backgroundBottomColor = "#131A33";
	public String textColor = "#FFFFFF";
	public String subtitleColor = "#9FB3D9";

	/**
	 * Close the whole game after leaving the server (Disconnect button or kick) or cancelling
	 * the connection, instead of going back to the menu.
	 */
	public boolean closeOnDisconnect = true;

	/** Accept the server resource pack automatically (no Yes/No prompt when joining). */
	public boolean acceptServerResourcePack = true;

	/** Animated stars on the background. */
	public boolean stars = true;

	/** Image files in config/nightwatch/. Leave empty to use the default look. */
	public String logoImage = "";
	public String backgroundImage = "";

	private static NightWatchConfig instance;

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("nightwatch");
	}

	public static NightWatchConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static NightWatchConfig load() {
		Path file = directory().resolve("client.json");
		NightWatchConfig config = new NightWatchConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				NightWatchConfig loaded = GSON.fromJson(reader, NightWatchConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (Exception e) {
				NightWatchClient.LOGGER.error("Failed to read {}, using defaults", file, e);
				return config;
			}
		}
		// Write back so new options appear in the file.
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			NightWatchClient.LOGGER.error("Failed to write {}", file, e);
		}
		return config;
	}

	/** Parses #RRGGBB or #AARRGGBB into an ARGB int. */
	public static int color(String value, int fallback) {
		try {
			String hex = value.trim().replace("#", "");
			long parsed = Long.parseLong(hex, 16);
			return hex.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
		} catch (Exception e) {
			return fallback;
		}
	}
}
