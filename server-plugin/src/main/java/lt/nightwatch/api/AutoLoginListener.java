package lt.nightwatch.api;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Receives the launcher session token from the NightWatch client mod
 * (channel nightwatch:auth) and logs the player in to AuthMe.
 */
public class AutoLoginListener implements PluginMessageListener {
	public static final String CHANNEL = "nightwatch:auth";

	private final NightWatchApiPlugin plugin;
	private final SessionStore sessions;

	public AutoLoginListener(NightWatchApiPlugin plugin, SessionStore sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel) || !plugin.getConfig().getBoolean("auth.auto-login", true)) {
			return;
		}
		String token = readString(message);
		if (token == null || !sessions.isValid(player.getName(), token)) {
			plugin.getLogger().info(player.getName() + " sent an invalid launcher session, AuthMe login required.");
			return;
		}
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (player.isOnline() && !AuthMeApi.getInstance().isAuthenticated(player)) {
				AuthMeApi.getInstance().forceLogin(player);
				plugin.getLogger().info(player.getName() + " logged in automatically with the NightWatch launcher.");
			}
		});
	}

	/** Reads a Minecraft string: VarInt byte length followed by UTF-8 bytes. */
	private static String readString(byte[] data) {
		int length = 0;
		int shift = 0;
		int index = 0;
		while (true) {
			if (index >= data.length || shift > 28) {
				return null;
			}
			byte b = data[index++];
			length |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				break;
			}
			shift += 7;
		}
		if (length < 0 || index + length > data.length) {
			return null;
		}
		return new String(data, index, length, StandardCharsets.UTF_8);
	}
}
