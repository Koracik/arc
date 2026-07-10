package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.util.HandheldRadioService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HandheldPttPayload(boolean down) implements CustomPacketPayload {
    public static final Type<HandheldPttPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "handheld_ptt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldPttPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HandheldPttPayload::down,
                    HandheldPttPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandheldPttPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HandheldRadioService.setPtt(player.getUUID(), payload.down);
            }
        });
    }
}
