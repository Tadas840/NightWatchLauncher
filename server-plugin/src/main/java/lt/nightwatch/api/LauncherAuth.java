package lt.nightwatch.api;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Launcher login / register / session validation against AuthMe.
 * Every method returns the HTTP status and JSON body to send.
 */
public class LauncherAuth {
	private static final Pattern NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
	private static final long FAIL_WINDOW = TimeUnit.MINUTES.toMillis(10);
	private static final long REGISTER_WINDOW = TimeUnit.HOURS.toMillis(1);

	public record Result(int status, Map<String, Object> body) {
	}

	private final NightWatchApiPlugin plugin;
	private final SessionStore sessions;
	private final Map<String, Deque<Long>> failures = new HashMap<>();
	private final Map<String, Deque<Long>> registrations = new HashMap<>();

	public LauncherAuth(NightWatchApiPlugin plugin, SessionStore sessions) {
		this.plugin = plugin;
		this.sessions = sessions;
	}

	public Result login(String ip, String username, String password) {
		Result check = precheck(ip, username, password);
		if (check != null) {
			return check;
		}
		AuthMeApi authMe = AuthMeApi.getInstance();
		if (!authMe.isRegistered(username)) {
			return error(404, "not_registered");
		}
		if (!authMe.checkPassword(username, password)) {
			recordFailure(ip);
			return error(401, "wrong_password");
		}
		return success(username);
	}

	public Result register(String ip, String username, String password) {
		if (!plugin.getConfig().getBoolean("auth.allow-register", true)) {
			return error(403, "register_disabled");
		}
		Result check = precheck(ip, username, password);
		if (check != null) {
			return check;
		}
		int min = authMeInt("settings.security.minPasswordLength", 5);
		int max = authMeInt("settings.security.passwordMaxLength", 30);
		if (password.length() < min || password.length() > max) {
			return error(400, "password_length", Map.of("min", min, "max", max));
		}
		if (password.equalsIgnoreCase(username)) {
			return error(400, "password_is_username");
		}
		AuthMeApi authMe = AuthMeApi.getInstance();
		if (authMe.isRegistered(username)) {
			return error(409, "already_registered");
		}
		// AuthMe's registerPlayer() does not check maxRegPerIp, so enforce it here.
		int maxPerIp = authMeInt("settings.restrictions.maxRegPerIp", 1);
		if (maxPerIp > 0 && authMe.getNamesByIp(ip).size() >= maxPerIp) {
			return error(403, "too_many_accounts");
		}
		if (countRecent(registrations, ip, REGISTER_WINDOW) >= plugin.getConfig().getInt("auth.max-registrations-per-hour", 3)) {
			return error(429, "too_many_attempts");
		}
		if (!authMe.registerPlayer(username, password)) {
			return error(500, "register_failed");
		}
		synchronized (this) {
			registrations.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
		}
		plugin.getLogger().info("Registered " + username + " from the launcher (" + ip + ")");
		return success(username);
	}

	public Result validate(String username, String token) {
		if (!plugin.getConfig().getBoolean("auth.enabled", true)) {
			return error(403, "auth_disabled");
		}
		if (username == null || !sessions.isValid(username, token)) {
			return error(401, "invalid_session");
		}
		return new Result(200, Map.of("valid", true));
	}

	public Result logout(String username, String token) {
		if (username != null && sessions.isValid(username, token)) {
			sessions.remove(username, token);
		}
		return new Result(200, Map.of("ok", true));
	}

	private Result precheck(String ip, String username, String password) {
		if (!plugin.getConfig().getBoolean("auth.enabled", true)) {
			return error(403, "auth_disabled");
		}
		if (Bukkit.getPluginManager().getPlugin("AuthMe") == null) {
			return error(503, "authme_missing");
		}
		if (tooManyFailures(ip)) {
			return error(429, "too_many_attempts");
		}
		if (username == null || !NAME.matcher(username).matches()) {
			return error(400, "invalid_username");
		}
		if (password == null || password.isEmpty()) {
			return error(400, "password_required");
		}
		return null;
	}

	private Result success(String username) {
		String token = sessions.create(username);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("username", username);
		body.put("token", token);
		return new Result(200, body);
	}

	private static Result error(int status, String code) {
		return error(status, code, Map.of());
	}

	private static Result error(int status, String code, Map<String, Object> extra) {
		Map<String, Object> body = new LinkedHashMap<>(extra);
		body.put("error", code);
		return new Result(status, body);
	}

	private boolean tooManyFailures(String ip) {
		return countRecent(failures, ip, FAIL_WINDOW) >= plugin.getConfig().getInt("auth.max-failed-attempts", 5);
	}

	private synchronized int countRecent(Map<String, Deque<Long>> map, String ip, long window) {
		Deque<Long> list = map.get(ip);
		if (list == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		while (!list.isEmpty() && list.peekFirst() < now - window) {
			list.pollFirst();
		}
		return list.size();
	}

	private synchronized void recordFailure(String ip) {
		failures.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
	}

	private static int authMeInt(String path, int fallback) {
		var authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
		if (authMe == null) {
			return fallback;
		}
		var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
				new java.io.File(authMe.getDataFolder(), "config.yml"));
		return config.getInt(path, fallback);
	}
}
