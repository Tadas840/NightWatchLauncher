package lt.nightwatch.client.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lt.nightwatch.client.NightWatchClient;
import lt.nightwatch.client.NightWatchConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sends the launcher session (written by the launcher to config/nightwatch/session.json)
 * to the server when joining, so the player does not have to type /login.
 */
public final class LauncherSession {
	private LauncherSession() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(AuthPayload.TYPE, AuthPayload.CODEC);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			JsonObject session = read();
			if (session == null || client.getUser() == null) {
				return;
			}
			String username = session.has("username") ? session.get("username").getAsString() : "";
			String token = session.has("token") ? session.get("token").getAsString() : "";
			if (!token.isEmpty() && username.equalsIgnoreCase(client.getUser().getName())) {
				sender.sendPacket(new AuthPayload(token));
			}
		});
	}

	private static JsonObject read() {
		Path file = NightWatchConfig.directory().resolve("session.json");
		if (!Files.exists(file)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return new Gson().fromJson(reader, JsonObject.class);
		} catch (Exception e) {
			NightWatchClient.LOGGER.warn("Could not read launcher session", e);
			return null;
		}
	}
}
