package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.item.HandheldRadioItem;
import com.realradio.common.util.ChannelKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateHandheldPayload(
        boolean mainHand,
        float frequency,
        boolean isAM,
        float volume,
        boolean active,
        int channelKey
) implements CustomPacketPayload {
    public static final Type<UpdateHandheldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "update_handheld"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateHandheldPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, UpdateHandheldPayload::mainHand,
                    ByteBufCodecs.FLOAT, UpdateHandheldPayload::frequency,
                    ByteBufCodecs.BOOL, UpdateHandheldPayload::isAM,
                    ByteBufCodecs.FLOAT, UpdateHandheldPayload::volume,
                    ByteBufCodecs.BOOL, UpdateHandheldPayload::active,
                    ByteBufCodecs.VAR_INT, UpdateHandheldPayload::channelKey,
                    UpdateHandheldPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateHandheldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            InteractionHand hand = payload.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof HandheldRadioItem)) {
                return;
            }
            HandheldRadioItem.applySettings(stack, payload.frequency, payload.isAM, payload.volume,
                    payload.active, ChannelKeys.clamp(payload.channelKey));
        });
    }
}
