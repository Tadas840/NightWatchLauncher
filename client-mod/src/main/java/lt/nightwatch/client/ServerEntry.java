package lt.nightwatch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/**
 * The NightWatch server entry in servers.dat. Quick play (launcher auto connect) and the
 * NightWatch menu both use it, so the server resource pack is accepted without asking.
 */
public final class ServerEntry {
	private ServerEntry() {
	}

	public static ServerData get(Minecraft minecraft) {
		NightWatchConfig config = NightWatchConfig.get();
		ServerList list = new ServerList(minecraft);
		list.load();
		ServerData server = list.get(config.serverAddress);
		boolean changed = false;
		if (server == null) {
			server = new ServerData(config.serverName, config.serverAddress, ServerData.Type.OTHER);
			list.add(server, false);
			changed = true;
		}
		if (config.acceptServerResourcePack && server.getResourcePackStatus() != ServerData.ServerPackStatus.ENABLED) {
			server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
			changed = true;
		}
		if (changed) {
			list.save();
		}
		return server;
	}
}
