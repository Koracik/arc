package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioRelayBlockEntity;
import com.realradio.common.util.ChannelKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateRelayPayload(
        BlockPos pos,
        float inFrequency,
        boolean inAM,
        float outFrequency,
        boolean outAM,
        boolean active,
        int inChannelKey,
        int outChannelKey
) implements CustomPacketPayload {
    public static final Type<UpdateRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "update_relay"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateRelayPayload> STREAM_CODEC =
            StreamCodec.of(UpdateRelayPayload::encode, UpdateRelayPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, UpdateRelayPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeFloat(p.inFrequency);
        buf.writeBoolean(p.inAM);
        buf.writeFloat(p.outFrequency);
        buf.writeBoolean(p.outAM);
        buf.writeBoolean(p.active);
        buf.writeVarInt(p.inChannelKey);
        buf.writeVarInt(p.outChannelKey);
    }

    private static UpdateRelayPayload decode(RegistryFriendlyByteBuf buf) {
        return new UpdateRelayPayload(
                buf.readBlockPos(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateRelayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos) instanceof RadioRelayBlockEntity be) {
                if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                be.applySettings(
                        payload.inFrequency, payload.inAM,
                        payload.outFrequency, payload.outAM,
                        payload.active,
                        ChannelKeys.clamp(payload.inChannelKey),
                        ChannelKeys.clamp(payload.outChannelKey)
                );
            }
        });
    }
}
