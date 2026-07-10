package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.common.util.ChannelKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: transmitter settings.
 * Range is not included — it is derived from frequency on the server.
 */
public record UpdateTransmitterPayload(
        BlockPos pos,
        float frequency,
        boolean isAM,
        boolean active,
        int channelKey
) implements CustomPacketPayload {
    public static final Type<UpdateTransmitterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "update_transmitter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTransmitterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UpdateTransmitterPayload::pos,
                    ByteBufCodecs.FLOAT, UpdateTransmitterPayload::frequency,
                    ByteBufCodecs.BOOL, UpdateTransmitterPayload::isAM,
                    ByteBufCodecs.BOOL, UpdateTransmitterPayload::active,
                    ByteBufCodecs.VAR_INT, UpdateTransmitterPayload::channelKey,
                    UpdateTransmitterPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateTransmitterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos) instanceof RadioTransmitterBlockEntity be) {
                if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                be.applySettings(payload.frequency, payload.isAM, payload.active, ChannelKeys.clamp(payload.channelKey));
            }
        });
    }
}
