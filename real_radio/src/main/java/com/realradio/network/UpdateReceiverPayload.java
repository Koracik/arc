package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateReceiverPayload(
        BlockPos pos,
        float frequency,
        boolean isAM,
        float volume,
        boolean active
) implements CustomPacketPayload {
    public static final Type<UpdateReceiverPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "update_receiver"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateReceiverPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UpdateReceiverPayload::pos,
                    ByteBufCodecs.FLOAT, UpdateReceiverPayload::frequency,
                    ByteBufCodecs.BOOL, UpdateReceiverPayload::isAM,
                    ByteBufCodecs.FLOAT, UpdateReceiverPayload::volume,
                    ByteBufCodecs.BOOL, UpdateReceiverPayload::active,
                    UpdateReceiverPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateReceiverPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos) instanceof RadioReceiverBlockEntity be) {
                if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                be.applySettings(payload.frequency, payload.isAM, payload.volume, payload.active);
            }
        });
    }
}
