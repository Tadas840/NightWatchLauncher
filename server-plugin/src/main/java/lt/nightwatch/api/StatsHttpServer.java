package lt.nightwatch.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small web server for the NightWatch launcher:
 * GET /player/{uuid or name} returns the player's stats, GET /news the launcher news,
 * POST /auth/login, /auth/register, /auth/validate, /auth/logout handle the launcher login (JSON body).
 *
 * Uses a plain (SSL)ServerSocket instead of com.sun.net.httpserver: its HTTPS server does a reverse
 * DNS lookup of every client, which takes ~5 seconds for Radmin VPN / unresolvable addresses.
 * One request per connection (Connection: close).
 */
public class StatsHttpServer {
	private static final Gson GSON = new Gson();
	private static final int MAX_HEADER_BYTES = 8192;
	private static final int MAX_BODY_BYTES = 4096;
	private static final int SOCKET_TIMEOUT_MS = 10000;

	private record Request(String method, String path, Map<String, String> headers, byte[] body, String ip) {
	}

	private record Response(int status, Object body) {
	}

	private final NightWatchApiPlugin plugin;
	private final PlayerStats stats;
	private final LauncherAuth auth;
	private final News news;
	private ServerSocket serverSocket;
	private ExecutorService workers;
	private Thread acceptThread;

	public StatsHttpServer(NightWatchApiPlugin plugin, PlayerStats stats, LauncherAuth auth, News news) {
		this.plugin = plugin;
		this.stats = stats;
		this.auth = auth;
		this.news = news;
	}

	public void start(int port) throws Exception {
		boolean https = plugin.getConfig().getBoolean("https", true);
		if (https) {
			SSLServerSocket socket = (SSLServerSocket) HttpsKeystore.load(plugin).getServerSocketFactory().createServerSocket();
			socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
			serverSocket = socket;
		} else {
			plugin.getLogger().warning("HTTPS is disabled: launcher passwords are sent unencrypted!");
			serverSocket = ServerSocketFactory.getDefault().createServerSocket();
		}
		serverSocket.setReuseAddress(true);
		serverSocket.bind(new InetSocketAddress(port), 50);

		workers = Executors.newFixedThreadPool(8);
		acceptThread = new Thread(this::acceptLoop, "NightWatchAPI-accept");
		acceptThread.setDaemon(true);
		acceptThread.start();
		plugin.getLogger().info("Web server listening on port " + port + (https ? " (HTTPS)" : " (HTTP)"));
	}

	public void stop() {
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		} catch (IOException ignored) {
			// Closing anyway.
		}
		if (workers != null) {
			workers.shutdownNow();
		}
	}

	private void acceptLoop() {
		while (!serverSocket.isClosed()) {
			try {
				Socket socket = serverSocket.accept();
				workers.execute(() -> handleConnection(socket));
			} catch (SocketException closed) {
				return;
			} catch (IOException e) {
				plugin.getLogger().warning("Accept failed: " + e.getMessage());
			}
		}
	}

	private void handleConnection(Socket socket) {
		try (socket) {
			socket.setSoTimeout(SOCKET_TIMEOUT_MS);
			if (socket instanceof SSLSocket ssl) {
				ssl.startHandshake();
			}
			String ip = socket.getInetAddress().getHostAddress();
			Request request = read(socket.getInputStream(), ip);
			Response response = request == null ? new Response(400, Map.of("error", "bad_request")) : route(request);
			write(socket.getOutputStream(), response);
		} catch (IOException ignored) {
			// Client disconnected, timed out or failed the TLS handshake.
		} catch (Exception e) {
			plugin.getLogger().warning("Request failed: " + e);
		}
	}

	private Response route(Request request) {
		try {
			String path = request.path();
			if ("OPTIONS".equals(request.method())) {
				return new Response(204, null);
			}
			if (path.startsWith("/player/")) {
				if (!"GET".equals(request.method())) {
					return new Response(405, Map.of("error", "method_not_allowed"));
				}
				String id = URLDecoder.decode(path.substring("/player/".length()), StandardCharsets.UTF_8);
				Map<String, Object> result = stats.lookup(id);
				return result == null ? new Response(404, Map.of("error", "Player not found")) : new Response(200, result);
			}
			if (path.equals("/news")) {
				return new Response(200, news.response());
			}
			if (path.startsWith("/auth/")) {
				if (!"POST".equals(request.method())) {
					return new Response(405, Map.of("error", "method_not_allowed"));
				}
				JsonObject body = GSON.fromJson(new String(request.body(), StandardCharsets.UTF_8), JsonObject.class);
				if (body == null) {
					return new Response(400, Map.of("error", "bad_request"));
				}
				String username = string(body, "username");
				String password = string(body, "password");
				String token = string(body, "token");
				LauncherAuth.Result result = switch (path) {
					case "/auth/login" -> auth.login(request.ip(), username, password);
					case "/auth/register" -> auth.register(request.ip(), username, password);
					case "/auth/validate" -> auth.validate(username, token);
					case "/auth/logout" -> auth.logout(username, token);
					default -> new LauncherAuth.Result(404, Map.of("error", "not_found"));
				};
				return new Response(result.status(), result.body());
			}
			return new Response(404, Map.of("error", "not_found"));
		} catch (Exception e) {
			plugin.getLogger().warning("Request " + request.path() + " failed: " + e);
			return new Response(500, Map.of("error", "internal_error"));
		}
	}

	private static String string(JsonObject body, String key) {
		return body.has(key) && body.get(key).isJsonPrimitive() ? body.get(key).getAsString() : null;
	}

	/** Reads one HTTP/1.1 request. Returns null if it is malformed or too large. */
	private static Request read(InputStream in, String ip) throws IOException {
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		int matched = 0;
		while (matched < 4) {
			int b = in.read();
			if (b < 0 || head.size() >= MAX_HEADER_BYTES) {
				return null;
			}
			head.write(b);
			matched = (b == (matched % 2 == 0 ? '\r' : '\n')) ? matched + 1 : (b == '\r' ? 1 : 0);
		}
		String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
		String[] requestLine = lines[0].split(" ");
		if (requestLine.length < 2) {
			return null;
		}
		Map<String, String> headers = new HashMap<>();
		for (int i = 1; i < lines.length; i++) {
			int colon = lines[i].indexOf(':');
			if (colon > 0) {
				headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
			}
		}
		int length = 0;
		if (headers.containsKey("content-length")) {
			try {
				length = Integer.parseInt(headers.get("content-length"));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		if (length < 0 || length > MAX_BODY_BYTES) {
			return null;
		}
		byte[] body = in.readNBytes(length);
		if (body.length != length) {
			return null;
		}
		String path = requestLine[1];
		int query = path.indexOf('?');
		if (query >= 0) {
			path = path.substring(0, query);
		}
		return new Request(requestLine[0].toUpperCase(Locale.ROOT), path, headers, body, ip);
	}

	private static void write(OutputStream out, Response response) throws IOException {
		byte[] body = response.body() == null ? new byte[0] : GSON.toJson(response.body()).getBytes(StandardCharsets.UTF_8);
		String head = "HTTP/1.1 " + response.status() + " " + reason(response.status()) + "\r\n"
				+ "Content-Type: application/json; charset=utf-8\r\n"
				+ "Content-Length: " + body.length + "\r\n"
				+ "Access-Control-Allow-Origin: *\r\n"
				+ "Access-Control-Allow-Headers: Content-Type\r\n"
				+ "Access-Control-Allow-Methods: GET, POST\r\n"
				+ "Connection: close\r\n\r\n";
		out.write(head.getBytes(StandardCharsets.ISO_8859_1));
		out.write(body);
		out.flush();
	}

	private static String reason(int status) {
		return switch (status) {
			case 200 -> "OK";
			case 204 -> "No Content";
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 405 -> "Method Not Allowed";
			case 409 -> "Conflict";
			case 429 -> "Too Many Requests";
			case 503 -> "Service Unavailable";
			default -> "Error";
		};
	}
}
