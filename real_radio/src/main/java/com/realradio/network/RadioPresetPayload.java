package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: save or load a channel preset slot (0–2).
 * {@code save=true} stores current settings; {@code save=false} recalls the slot.
 */
public record RadioPresetPayload(
        BlockPos pos,
        boolean transmitter,
        int slot,
        boolean save
) implements CustomPacketPayload {
    public static final Type<RadioPresetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "radio_preset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioPresetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RadioPresetPayload::pos,
                    ByteBufCodecs.BOOL, RadioPresetPayload::transmitter,
                    ByteBufCodecs.VAR_INT, RadioPresetPayload::slot,
                    ByteBufCodecs.BOOL, RadioPresetPayload::save,
                    RadioPresetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RadioPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            if (payload.transmitter) {
                if (player.level().getBlockEntity(payload.pos) instanceof RadioTransmitterBlockEntity be) {
                    if (payload.save) {
                        be.savePreset(payload.slot);
                    } else {
                        be.loadPreset(payload.slot);
                    }
                }
            } else {
                if (player.level().getBlockEntity(payload.pos) instanceof RadioReceiverBlockEntity be) {
                    if (payload.save) {
                        be.savePreset(payload.slot);
                    } else {
                        be.loadPreset(payload.slot);
                    }
                }
            }
        });
    }
}
