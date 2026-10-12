package lt.nightwatch.api;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Self-signed HTTPS certificate of the API. Created with keytool on first start:
 * keystore.p12 (private key, stays on the server) and nightwatch-api.pem (public certificate,
 * copy it to the launcher: nightwatch/nightwatch-api.pem).
 */
public final class HttpsKeystore {
	private HttpsKeystore() {
	}

	public static SSLContext load(NightWatchApiPlugin plugin) throws Exception {
		File folder = plugin.getDataFolder();
		File keystore = new File(folder, "keystore.p12");
		File passwordFile = new File(folder, "keystore-password.txt");
		File pem = new File(folder, "nightwatch-api.pem");

		if (!keystore.exists()) {
			generate(plugin, keystore, passwordFile);
		}
		char[] password = Files.readString(passwordFile.toPath(), StandardCharsets.UTF_8).trim().toCharArray();

		KeyStore store = KeyStore.getInstance("PKCS12");
		try (InputStream in = new FileInputStream(keystore)) {
			store.load(in, password);
		}

		Certificate certificate = store.getCertificate("nightwatch");
		if (!pem.exists()) {
			String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificate.getEncoded());
			Files.writeString(pem.toPath(), "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n", StandardCharsets.US_ASCII);
		}
		String fingerprint = HexFormat.ofDelimiter(":").withUpperCase()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
		plugin.getLogger().info("HTTPS certificate SHA-256: " + fingerprint);

		KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagers.init(store, password);
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keyManagers.getKeyManagers(), null, new SecureRandom());
		return context;
	}

	private static void generate(NightWatchApiPlugin plugin, File keystore, File passwordFile) throws Exception {
		folderExists(keystore.getParentFile());
		byte[] random = new byte[24];
		new SecureRandom().nextBytes(random);
		String password = HexFormat.of().formatHex(random);

		File keytool = new File(System.getProperty("java.home"), "bin/" + (isWindows() ? "keytool.exe" : "keytool"));
		Process process = new ProcessBuilder(
				keytool.getAbsolutePath(), "-genkeypair",
				"-alias", "nightwatch",
				"-keyalg", "EC", "-groupname", "secp256r1",
				"-validity", "3650",
				"-dname", "CN=NightWatch API",
				"-storetype", "PKCS12",
				"-keystore", keystore.getAbsolutePath(),
				"-storepass", password,
				"-keypass", password)
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0 || !keystore.exists()) {
			throw new IOException("keytool failed: " + output);
		}
		Files.writeString(passwordFile.toPath(), password, StandardCharsets.UTF_8);
		plugin.getLogger().warning("Created a new HTTPS certificate. Copy plugins/NightWatchAPI/nightwatch-api.pem "
				+ "to the launcher (nightwatch/nightwatch-api.pem) and rebuild it, otherwise the launcher cannot connect.");
	}

	private static void folderExists(File folder) throws IOException {
		if (!folder.exists() && !folder.mkdirs()) {
			throw new IOException("Could not create " + folder);
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}
