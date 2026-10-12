package lt.nightwatch.client.mixin;

import lt.nightwatch.client.NightWatchClient;
import lt.nightwatch.client.NightWatchConfig;
import lt.nightwatch.client.gui.NightWatchTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

	/** Use the NightWatch window title. */
	@Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
	private void nightwatch$createTitle(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(NightWatchConfig.get().windowTitle);
	}

	/**
	 * Replace the vanilla main menu with the NightWatch one.
	 * After leaving the server, or cancelling the connection, the game closes instead of returning to the menu.
	 */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen nightwatch$replaceTitleScreen(Screen screen) {
		Minecraft minecraft = (Minecraft) (Object) this;
		boolean menu = screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen
				|| screen instanceof NightWatchTitleScreen;
		if (menu && NightWatchConfig.get().closeOnDisconnect) {
			if (NightWatchClient.leftServer) {
				NightWatchClient.LOGGER.info("Left the server, closing the game.");
				minecraft.stop();
			} else if (minecraft.screen instanceof ConnectScreen) {
				NightWatchClient.LOGGER.info("Connection cancelled, closing the game.");
				minecraft.stop();
			}
		}
		if (screen instanceof TitleScreen) {
			return new NightWatchTitleScreen();
		}
		return screen;
	}
}
