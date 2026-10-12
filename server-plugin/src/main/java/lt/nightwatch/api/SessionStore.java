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
 * A player can be logged in on several computers (up to MAX_SESSIONS_PER_PLAYER).
 */
public class SessionStore {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int MAX_SESSIONS_PER_PLAYER = 10;

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
		if (data.isString(key + ".hash")) {
			// Old format (one session per player).
			data.set(key, null);
		}
		removeExpired(key);
		ConfigurationSection sessions = data.getConfigurationSection(key);
		if (sessions != null && sessions.getKeys(false).size() >= MAX_SESSIONS_PER_PLAYER) {
			// Drop the session that expires first (the oldest).
			sessions.getKeys(false).stream()
					.min((a, b) -> Long.compare(sessions.getLong(a), sessions.getLong(b)))
					.ifPresent(oldest -> sessions.set(oldest, null));
		}
		data.set(key + "." + hash(token), System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days));
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
		// Keys are SHA-256 hashes of the tokens, so the lookup does not reveal anything about valid tokens.
		String tokenHash = hash(token);
		if (!section.isLong(tokenHash) && !section.isInt(tokenHash)) {
			return false;
		}
		if (section.getLong(tokenHash) < System.currentTimeMillis()) {
			section.set(tokenHash, null);
			save();
			return false;
		}
		return true;
	}

	/** Ends one session (launcher logout). */
	public synchronized void remove(String playerName, String token) {
		data.set(key(playerName) + "." + hash(token), null);
		save();
	}

	private void removeExpired(String key) {
		ConfigurationSection section = data.getConfigurationSection(key);
		if (section == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (String tokenHash : section.getKeys(false)) {
			if (section.getLong(tokenHash) < now) {
				section.set(tokenHash, null);
			}
		}
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
