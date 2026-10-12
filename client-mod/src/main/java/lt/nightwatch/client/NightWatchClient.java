package lt.nightwatch.client;

import lt.nightwatch.client.auth.LauncherSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NightWatchClient implements ClientModInitializer {
	public static final String MOD_ID = "nightwatch";
	public static final Logger LOGGER = LoggerFactory.getLogger("NightWatch");

	/** True after the player was connected to a server and left it. */
	public static volatile boolean leftServer;

	@Override
	public void onInitializeClient() {
		NightWatchConfig config = NightWatchConfig.get();
		LauncherSession.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leftServer = true);
		// Before quick play connects: make sure the server entry accepts the server resource pack.
		ClientLifecycleEvents.CLIENT_STARTED.register(ServerEntry::get);
		LOGGER.info("NightWatch Client loaded, server {}", config.serverAddress);
	}
}
