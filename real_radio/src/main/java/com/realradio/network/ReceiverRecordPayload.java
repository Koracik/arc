package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.util.AirRecording;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: toggle REC on a receiver. */
public record ReceiverRecordPayload(BlockPos pos, boolean recording) implements CustomPacketPayload {
    public static final Type<ReceiverRecordPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "receiver_record"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReceiverRecordPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ReceiverRecordPayload::pos,
                    ByteBufCodecs.BOOL, ReceiverRecordPayload::recording,
                    ReceiverRecordPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReceiverRecordPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos) instanceof RadioReceiverBlockEntity be) {
                if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                if (payload.recording) {
                    if (be.isActive()) {
                        AirRecording.onRecordingStarted(be);
                        be.setRecording(true);
                    }
                } else {
                    AirRecording.finish(be);
                }
            }
        });
    }
}
