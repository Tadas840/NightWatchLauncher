package lt.nightwatch.api;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;


public class NightWatchApiPlugin extends JavaPlugin {
	private StatsHttpServer httpServer;
	private PlayerStats stats;
	private News news;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		stats = new PlayerStats(this);
		SessionStore sessions = new SessionStore(this);
		news = new News(this);
		httpServer = new StatsHttpServer(this, stats, new LauncherAuth(this, sessions), news);
		getServer().getMessenger().registerIncomingPluginChannel(this, AutoLoginListener.CHANNEL,
				new AutoLoginListener(this, sessions));
		try {
			httpServer.start(getConfig().getInt("port", 8765));
		} catch (Exception e) {
			getLogger().severe("Could not start the web server: " + e.getMessage());
		}
	}

	@Override
	public void onDisable() {
		if (httpServer != null) {
			httpServer.stop();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			reloadConfig();
			stats.reload();
			news.reload();
			sender.sendMessage("NightWatchAPI config and news reloaded.");
			return true;
		}
		return false;
	}
}
