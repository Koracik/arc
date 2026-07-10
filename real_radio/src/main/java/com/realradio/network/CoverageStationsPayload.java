package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.client.coverage.CoverageOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server → client: active TX / relay-out stations for coverage overlay. */
public record CoverageStationsPayload(List<Station> stations) implements CustomPacketPayload {
    public static final int MAX = 64;

    public static final Type<CoverageStationsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "coverage_stations"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CoverageStationsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Station.STREAM_CODEC),
                    CoverageStationsPayload::stations,
                    CoverageStationsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoverageStationsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CoverageOverlay.updateStations(payload.stations));
    }

    public record Station(BlockPos pos, float frequency, boolean isAM, int range, int keyHash) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Station> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Station::pos,
                        ByteBufCodecs.FLOAT, Station::frequency,
                        ByteBufCodecs.BOOL, Station::isAM,
                        ByteBufCodecs.VAR_INT, Station::range,
                        ByteBufCodecs.VAR_INT, Station::keyHash,
                        Station::new
                );
    }
}
