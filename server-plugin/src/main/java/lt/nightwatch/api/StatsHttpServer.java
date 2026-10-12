package lt.nightwatch.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Small web server for the NightWatch launcher:
 * GET /player/{uuid or name} returns the player's stats, GET /news the launcher news,
 * POST /auth/login, /auth/register, /auth/validate, /auth/logout handle the launcher login (JSON body).
 */
public class StatsHttpServer {
	private static final Gson GSON = new Gson();

	private final NightWatchApiPlugin plugin;
	private final PlayerStats stats;
	private final LauncherAuth auth;
	private final News news;
	private HttpServer server;

	public StatsHttpServer(NightWatchApiPlugin plugin, PlayerStats stats, LauncherAuth auth, News news) {
		this.plugin = plugin;
		this.stats = stats;
		this.auth = auth;
		this.news = news;
	}

	public void start(int port) throws Exception {
		if (plugin.getConfig().getBoolean("https", true)) {
			HttpsServer https = HttpsServer.create(new InetSocketAddress(port), 0);
			https.setHttpsConfigurator(new HttpsConfigurator(HttpsKeystore.load(plugin)));
			server = https;
		} else {
			plugin.getLogger().warning("HTTPS is disabled: launcher passwords are sent unencrypted!");
			server = HttpServer.create(new InetSocketAddress(port), 0);
		}
		server.setExecutor(Executors.newFixedThreadPool(4));
		server.createContext("/player/", this::handlePlayer);
		server.createContext("/auth/", this::handleAuth);
		server.createContext("/news", exchange -> send(exchange, 200, news.response()));
		server.start();
		plugin.getLogger().info("Web server listening on port " + port + (server instanceof HttpsServer ? " (HTTPS)" : " (HTTP)"));
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private void handlePlayer(HttpExchange exchange) throws IOException {
		try {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				send(exchange, 405, Map.of("error", "Method not allowed"));
				return;
			}
			String id = URLDecoder.decode(exchange.getRequestURI().getPath().substring("/player/".length()), StandardCharsets.UTF_8);
			Map<String, Object> result = stats.lookup(id);
			if (result == null) {
				send(exchange, 404, Map.of("error", "Player not found"));
			} else {
				send(exchange, 200, result);
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Stats request failed: " + e);
			send(exchange, 500, Map.of("error", "Internal error"));
		}
	}

	private void handleAuth(HttpExchange exchange) throws IOException {
		try {
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
				exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
				exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST");
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				send(exchange, 405, Map.of("error", "method_not_allowed"));
				return;
			}
			JsonObject body;
			try (InputStream in = exchange.getRequestBody()) {
				byte[] raw = in.readNBytes(4096);
				body = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
			}
			if (body == null) {
				send(exchange, 400, Map.of("error", "bad_request"));
				return;
			}
			String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
			String username = string(body, "username");
			String password = string(body, "password");
			String token = string(body, "token");

			LauncherAuth.Result result = switch (exchange.getRequestURI().getPath()) {
				case "/auth/login" -> auth.login(ip, username, password);
				case "/auth/register" -> auth.register(ip, username, password);
				case "/auth/validate" -> auth.validate(username, token);
				case "/auth/logout" -> auth.logout(username, token);
				default -> new LauncherAuth.Result(404, Map.of("error", "not_found"));
			};
			send(exchange, result.status(), result.body());
		} catch (Exception e) {
			plugin.getLogger().warning("Auth request failed: " + e);
			send(exchange, 500, Map.of("error", "internal_error"));
		}
	}

	private static String string(JsonObject body, String key) {
		return body.has(key) && body.get(key).isJsonPrimitive() ? body.get(key).getAsString() : null;
	}

	private void send(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}
}
