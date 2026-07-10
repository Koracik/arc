package com.realradio.network;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioManager;
import com.realradio.common.blockentity.RadioRelayBlockEntity;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.config.RealRadioConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Client → server: request nearby TX list for coverage overlay. */
public record RequestCoveragePayload() implements CustomPacketPayload {
    public static final Type<RequestCoveragePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "request_coverage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCoveragePayload> STREAM_CODEC =
            StreamCodec.unit(new RequestCoveragePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestCoveragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!RealRadioConfig.enableCoverageOverlay()) {
                PacketDistributor.sendToPlayer(player, new CoverageStationsPayload(List.of()));
                return;
            }
            // Creative / op only for full data when realism is on
            if (RealRadioConfig.realismMode() && !player.isCreative() && !player.hasPermissions(2)) {
                PacketDistributor.sendToPlayer(player, new CoverageStationsPayload(List.of()));
                return;
            }
            List<CoverageStationsPayload.Station> stations = new ArrayList<>();
            for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
                if (!tx.isActive() || tx.getLevel() != player.level()) {
                    continue;
                }
                stations.add(new CoverageStationsPayload.Station(
                        tx.getBlockPos(),
                        tx.getFrequency(),
                        tx.isAM(),
                        tx.getRange(),
                        Integer.hashCode(tx.getChannelKey())
                ));
                if (stations.size() >= CoverageStationsPayload.MAX) {
                    break;
                }
            }
            if (stations.size() < CoverageStationsPayload.MAX) {
                for (RadioRelayBlockEntity relay : RadioManager.relays()) {
                    if (!relay.isActive() || relay.getLevel() != player.level()) {
                        continue;
                    }
                    stations.add(new CoverageStationsPayload.Station(
                            relay.getBlockPos(),
                            relay.getOutFrequency(),
                            relay.isOutAM(),
                            relay.getOutRange(),
                            Integer.hashCode(relay.getOutChannelKey())
                    ));
                    if (stations.size() >= CoverageStationsPayload.MAX) {
                        break;
                    }
                }
            }
            PacketDistributor.sendToPlayer(player, new CoverageStationsPayload(stations));
        });
    }
}
