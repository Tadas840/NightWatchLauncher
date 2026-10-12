package lt.nightwatch.client.auth;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Launcher session token sent to the NightWatchAPI server plugin, which logs the player in to AuthMe.
 */
public record AuthPayload(String token) implements CustomPacketPayload {
	public static final Type<AuthPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("nightwatch", "auth"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AuthPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AuthPayload::token, AuthPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
