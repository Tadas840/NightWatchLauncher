package lt.nightwatch.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Launcher sessions. Only a SHA-256 hash of each token is stored (sessions.yml).
 */
public class SessionStore {
	private static final SecureRandom RANDOM = new SecureRandom();

	private final NightWatchApiPlugin plugin;
	private final File file;
	private final YamlConfiguration data;

	public SessionStore(NightWatchApiPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "sessions.yml");
		this.data = YamlConfiguration.loadConfiguration(file);
	}

	/** Creates a new session for the player and returns its token. */
	public synchronized String create(String playerName) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = HexFormat.of().formatHex(bytes);
		long days = plugin.getConfig().getLong("auth.session-days", 30);
		String key = key(playerName);
		data.set(key + ".hash", hash(token));
		data.set(key + ".expires", System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days));
		save();
		return token;
	}

	public synchronized boolean isValid(String playerName, String token) {
		if (playerName == null || token == null || token.isBlank()) {
			return false;
		}
		ConfigurationSection section = data.getConfigurationSection(key(playerName));
		if (section == null) {
			return false;
		}
		if (section.getLong("expires") < System.currentTimeMillis()) {
			data.set(key(playerName), null);
			save();
			return false;
		}
		return MessageDigest.isEqual(
				hash(token).getBytes(StandardCharsets.UTF_8),
				section.getString("hash", "").getBytes(StandardCharsets.UTF_8));
	}

	public synchronized void remove(String playerName) {
		data.set(key(playerName), null);
		save();
	}

	private static String key(String playerName) {
		return "sessions." + playerName.toLowerCase(Locale.ROOT);
	}

	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private void save() {
		try {
			data.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Could not save sessions.yml: " + e.getMessage());
		}
	}
}
