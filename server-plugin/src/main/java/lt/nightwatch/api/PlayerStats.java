package lt.nightwatch.api;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Resolves the configured fields (economy balances, statistics, rank) for a player.
 */
public class PlayerStats {
	private static final Pattern UUID_NO_DASHES = Pattern.compile("^[0-9a-fA-F]{32}$");
	private static final Pattern SAFE_COLUMN = Pattern.compile("^[a-zA-Z0-9_]+$");

	private final NightWatchApiPlugin plugin;

	public PlayerStats(NightWatchApiPlugin plugin) {
		this.plugin = plugin;
		reload();
	}

	public void reload() {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			plugin.getLogger().warning("SQLite driver not found, economy fields will be empty.");
		}
	}

	/**
	 * @param id player UUID (with or without dashes) or name.
	 * @return the response body, or null when the player is unknown.
	 */
	public Map<String, Object> lookup(String id) throws Exception {
		OfflinePlayer player = resolve(id);
		if (player == null) {
			return null;
		}
		UUID uuid = player.getUniqueId();

		List<Map<String, Object>> fields = new ArrayList<>();
		for (Map<?, ?> raw : plugin.getConfig().getMapList("fields")) {
			String source = String.valueOf(raw.get("source"));
			int decimals = raw.get("decimals") instanceof Number n ? n.intValue() : 0;

			Map<String, Object> field = new LinkedHashMap<>();
			field.put("id", String.valueOf(raw.get("id")));
			field.put("label", String.valueOf(raw.get("label")));
			field.put("icon", raw.get("icon") == null ? "" : String.valueOf(raw.get("icon")));
			field.put("color", raw.get("color") == null ? "" : String.valueOf(raw.get("color")));
			field.put("value", value(player, uuid, source, decimals));
			fields.add(field);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("uuid", uuid.toString());
		result.put("name", player.getName());
		result.put("online", player.isOnline());
		result.put("fields", fields);
		return result;
	}

	private OfflinePlayer resolve(String id) {
		String uuidText = id;
		if (UUID_NO_DASHES.matcher(id).matches()) {
			uuidText = id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
		}
		try {
			OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuidText));
			return player.hasPlayedBefore() || player.isOnline() ? player : null;
		} catch (IllegalArgumentException notUuid) {
			OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(id);
			if (cached != null) {
				return cached;
			}
			// Offline mode server: the UUID is derived from the name.
			UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + id).getBytes(StandardCharsets.UTF_8));
			OfflinePlayer player = Bukkit.getOfflinePlayer(offline);
			return player.hasPlayedBefore() ? player : null;
		}
	}

	private String value(OfflinePlayer player, UUID uuid, String source, int decimals) {
		try {
			if (source.startsWith("economy:")) {
				return format(economy(uuid, source.substring("economy:".length())), decimals);
			}
			if (source.startsWith("statistic:")) {
				Statistic statistic = Statistic.valueOf(source.substring("statistic:".length()).toUpperCase(Locale.ROOT));
				// Statistics of offline players are read from disk, do it on the main thread.
				int amount = Bukkit.getScheduler().callSyncMethod(plugin, () -> player.getStatistic(statistic))
						.get(5, TimeUnit.SECONDS);
				return format(amount, decimals);
			}
			if (source.equals("rank")) {
				return rank(uuid);
			}
			plugin.getLogger().warning("Unknown field source: " + source);
		} catch (Exception e) {
			plugin.getLogger().warning("Could not read " + source + " for " + uuid + ": " + e);
		}
		return "-";
	}

	private double economy(UUID uuid, String currency) throws Exception {
		if (!SAFE_COLUMN.matcher(currency).matches()) {
			throw new IllegalArgumentException("Invalid currency id " + currency);
		}
		File database = new File(plugin.getConfig().getString("economy-database", "plugins/ExcellentEconomy/data.db"));
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath() + "?open_mode=1");
			 PreparedStatement statement = connection.prepareStatement(
					 "SELECT " + currency + " FROM excellenteconomy_users WHERE uuid = ?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet rs = statement.executeQuery()) {
				return rs.next() ? rs.getDouble(1) : 0;
			}
		}
	}

	private String rank(UUID uuid) throws Exception {
		LuckPerms luckPerms = LuckPermsProvider.get();
		User user = luckPerms.getUserManager().loadUser(uuid).get(5, TimeUnit.SECONDS);
		String groupName = user.getPrimaryGroup();

		ConfigurationSection names = plugin.getConfig().getConfigurationSection("rank-names");
		if (names != null && names.isString(groupName)) {
			return names.getString(groupName);
		}
		Group group = luckPerms.getGroupManager().getGroup(groupName);
		if (group != null && group.getDisplayName() != null) {
			return group.getDisplayName();
		}
		return groupName.substring(0, 1).toUpperCase(Locale.ROOT) + groupName.substring(1);
	}

	private static String format(double value, int decimals) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
		symbols.setGroupingSeparator(' ');
		symbols.setDecimalSeparator(',');
		String pattern = decimals > 0 ? "#,##0." + "0".repeat(decimals) : "#,##0";
		DecimalFormat format = new DecimalFormat(pattern, symbols);
		return format.format(decimals > 0 ? value : Math.floor(value));
	}
}
