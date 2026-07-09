package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.client.sound.StationSpectrumCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: spectrum markers for the receiver dial (same band, audible stations).
 */
public record NearbyStationsPayload(
        BlockPos pos,
        List<StationMarker> stations
) implements CustomPacketPayload {
    public static final int MAX_STATIONS = 12;

    public static final Type<NearbyStationsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "nearby_stations"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NearbyStationsPayload> STREAM_CODEC =
            StreamCodec.of(NearbyStationsPayload::encode, NearbyStationsPayload::decode);

    public record StationMarker(float frequency, float strength) {
    }

    private static void encode(RegistryFriendlyByteBuf buf, NearbyStationsPayload payload) {
        buf.writeBlockPos(payload.pos);
        int n = Math.min(MAX_STATIONS, payload.stations.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            StationMarker m = payload.stations.get(i);
            buf.writeFloat(m.frequency());
            buf.writeFloat(m.strength());
        }
    }

    private static NearbyStationsPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(MAX_STATIONS, buf.readVarInt());
        List<StationMarker> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new StationMarker(buf.readFloat(), buf.readFloat()));
        }
        return new NearbyStationsPayload(pos, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NearbyStationsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StationSpectrumCache.update(payload.pos(), payload.stations()));
    }
}
