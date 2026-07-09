package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.client.sound.ReceiverStaticSoundHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: current signal quality at a receiver for static sound + voice gain.
 */
public record ReceiverQualityPayload(
        BlockPos pos,
        float quality,
        float voiceVolume,
        float staticVolume,
        boolean active
) implements CustomPacketPayload {
    public static final Type<ReceiverQualityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "receiver_quality"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReceiverQualityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ReceiverQualityPayload::pos,
                    ByteBufCodecs.FLOAT, ReceiverQualityPayload::quality,
                    ByteBufCodecs.FLOAT, ReceiverQualityPayload::voiceVolume,
                    ByteBufCodecs.FLOAT, ReceiverQualityPayload::staticVolume,
                    ByteBufCodecs.BOOL, ReceiverQualityPayload::active,
                    ReceiverQualityPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReceiverQualityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ReceiverStaticSoundHandler.update(payload));
    }
}
