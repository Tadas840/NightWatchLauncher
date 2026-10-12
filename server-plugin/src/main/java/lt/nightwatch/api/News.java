package lt.nightwatch.api;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher news from plugins/NightWatchAPI/news.yml.
 */
public class News {
	private final NightWatchApiPlugin plugin;
	private volatile List<Map<String, Object>> articles = List.of();

	public News(NightWatchApiPlugin plugin) {
		this.plugin = plugin;
		reload();
	}

	public void reload() {
		File file = new File(plugin.getDataFolder(), "news.yml");
		if (!file.exists()) {
			plugin.saveResource("news.yml", false);
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		List<Map<String, Object>> loaded = new ArrayList<>();
		for (Map<?, ?> raw : yaml.getMapList("articles")) {
			Map<String, Object> article = new LinkedHashMap<>();
			article.put("title", text(raw.get("title")));
			article.put("date", text(raw.get("date")));
			article.put("author", text(raw.get("author")));
			article.put("link", text(raw.get("link")));
			article.put("content", text(raw.get("content")));
			loaded.add(article);
		}
		articles = List.copyOf(loaded);
	}

	public Map<String, Object> response() {
		return Map.of("articles", articles);
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}
}
