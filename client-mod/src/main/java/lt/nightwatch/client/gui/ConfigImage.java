package lt.nightwatch.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import lt.nightwatch.client.NightWatchClient;
import lt.nightwatch.client.NightWatchConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A PNG image loaded from config/nightwatch/ and registered as a texture.
 */
public record ConfigImage(Identifier id, int width, int height) {

	/**
	 * @return the loaded image, or null when the file name is empty or the file cannot be read.
	 */
	public static ConfigImage load(String fileName, String textureName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		Path file = NightWatchConfig.directory().resolve(fileName);
		if (!Files.exists(file)) {
			NightWatchClient.LOGGER.warn("Image {} not found", file);
			return null;
		}
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);
			Identifier id = Identifier.fromNamespaceAndPath(NightWatchClient.MOD_ID, "config/" + textureName);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "nightwatch " + textureName, image));
			return new ConfigImage(id, image.getWidth(), image.getHeight());
		} catch (Exception e) {
			NightWatchClient.LOGGER.error("Failed to load image {}", file, e);
			return null;
		}
	}
}
