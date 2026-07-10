package com.realradio.network;

import com.realradio.RealRadio;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(RealRadio.MOD_ID).versioned("2");

        registrar.playToServer(
                UpdateTransmitterPayload.TYPE,
                UpdateTransmitterPayload.STREAM_CODEC,
                UpdateTransmitterPayload::handle
        );

        registrar.playToServer(
                UpdateReceiverPayload.TYPE,
                UpdateReceiverPayload.STREAM_CODEC,
                UpdateReceiverPayload::handle
        );

        registrar.playToServer(
                UpdateRelayPayload.TYPE,
                UpdateRelayPayload.STREAM_CODEC,
                UpdateRelayPayload::handle
        );

        registrar.playToServer(
                UpdateHandheldPayload.TYPE,
                UpdateHandheldPayload.STREAM_CODEC,
                UpdateHandheldPayload::handle
        );

        registrar.playToServer(
                HandheldPttPayload.TYPE,
                HandheldPttPayload.STREAM_CODEC,
                HandheldPttPayload::handle
        );

        registrar.playToServer(
                ReceiverRecordPayload.TYPE,
                ReceiverRecordPayload.STREAM_CODEC,
                ReceiverRecordPayload::handle
        );

        registrar.playToServer(
                RequestCoveragePayload.TYPE,
                RequestCoveragePayload.STREAM_CODEC,
                RequestCoveragePayload::handle
        );

        registrar.playToClient(
                ReceiverQualityPayload.TYPE,
                ReceiverQualityPayload.STREAM_CODEC,
                ReceiverQualityPayload::handle
        );

        registrar.playToClient(
                NearbyStationsPayload.TYPE,
                NearbyStationsPayload.STREAM_CODEC,
                NearbyStationsPayload::handle
        );

        registrar.playToClient(
                CoverageStationsPayload.TYPE,
                CoverageStationsPayload.STREAM_CODEC,
                CoverageStationsPayload::handle
        );

        registrar.playToServer(
                RadioPresetPayload.TYPE,
                RadioPresetPayload.STREAM_CODEC,
                RadioPresetPayload::handle
        );
    }
}
