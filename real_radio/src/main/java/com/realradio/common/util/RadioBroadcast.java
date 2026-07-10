package com.realradio.common.util;

import com.realradio.common.blockentity.RadioManager;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.blockentity.RadioRelayBlockEntity;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.config.RealRadioConfig;
import com.realradio.integration.plasmovoice.RadioVoiceService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import su.plo.voice.api.server.audio.source.ServerStaticSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared TX → RX / relay broadcast path with hop limiting and channel keys.
 * <p>
 * Hot path (every Opus frame): cheap distance/key checks + cached dominant source
 * on receivers. Full LOS math runs on the RX quality tick only.
 */
public final class RadioBroadcast {
    private RadioBroadcast() {
    }

    public static void fromTransmitter(RadioTransmitterBlockEntity tx, byte[] data, long sequence,
                                       short hearDistance, boolean stereo) {
        if (tx == null || !tx.isActive() || tx.getLevel() == null) {
            return;
        }
        Set<RadioManager.DimPos> visited = new HashSet<>(4);
        visited.add(RadioManager.DimPos.of(tx.getLevel(), tx.getBlockPos()));
        broadcast(
                tx.getLevel(),
                tx.getBlockPos(),
                tx.getFrequency(),
                tx.isAM(),
                tx.getChannelKey(),
                tx.getRange(),
                data,
                sequence,
                hearDistance,
                stereo,
                0,
                visited
        );
    }

    public static void broadcast(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            byte[] data,
            long sequence,
            short hearDistance,
            boolean stereo,
            int hop,
            Set<RadioManager.DimPos> visited
    ) {
        if (level == null || data == null || data.length == 0 || rangeBlocks <= 0 || sourcePos == null) {
            return;
        }

        double rangeSq = (double) rangeBlocks * (double) rangeBlocks;
        RadioBand band = RadioBand.fromAm(isAM);

        for (RadioReceiverBlockEntity rx : RadioManager.receivers()) {
            if (!rx.isActive() || rx.getLevel() != level || rx.isAM() != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(channelKey, rx.getChannelKey())) {
                continue;
            }
            // Cheap cull before any quality math
            if (sourcePos.distSqr(rx.getBlockPos()) >= rangeSq) {
                continue;
            }
            // Dominant was decided on the quality tick — no LOS re-scan per frame
            if (!rx.acceptsFrom(sourcePos)) {
                continue;
            }
            deliverToReceiver(rx, data, sequence, hearDistance, stereo);
            if (rx.isRecording()) {
                AirRecording.captureFrame(rx, data, sequence, stereo, frequency, isAM, channelKey);
            }
        }

        HandheldRadioService.deliverToHandhelds(
                level, sourcePos, frequency, isAM, channelKey, rangeBlocks,
                data, sequence, hearDistance, stereo
        );

        int maxHops = RealRadioConfig.maxRelayHops();
        if (hop >= maxHops) {
            return;
        }

        for (RadioRelayBlockEntity relay : RadioManager.relays()) {
            if (!relay.isActive() || relay.getLevel() != level) {
                continue;
            }
            RadioManager.DimPos relayPos = RadioManager.DimPos.of(level, relay.getBlockPos());
            if (visited.contains(relayPos)) {
                continue;
            }
            if (relay.isInAM() != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(channelKey, relay.getInChannelKey())) {
                continue;
            }
            if (sourcePos.distSqr(relay.getBlockPos()) >= rangeSq) {
                continue;
            }
            // Cheap tuning + distance only on hot path; full LOS only if link looks viable
            if (!relayCanHear(level, sourcePos, frequency, isAM, rangeBlocks, band, relay)) {
                continue;
            }

            Set<RadioManager.DimPos> nextVisited = new HashSet<>(visited);
            nextVisited.add(relayPos);
            relay.markSpeaking();
            broadcast(
                    level,
                    relay.getBlockPos(),
                    relay.getOutFrequency(),
                    relay.isOutAM(),
                    relay.getOutChannelKey(),
                    relay.getOutRange(),
                    data,
                    sequence,
                    hearDistance,
                    stereo,
                    hop + 1,
                    nextVisited
            );
        }
    }

    /**
     * Relay ingress check: tuning + distance first; LOS only if within range and tuned.
     * Skips the O(all TX) dominant scan that used to run every audio frame.
     */
    private static boolean relayCanHear(Level level, BlockPos sourcePos, float frequency, boolean isAM,
                                        int rangeBlocks, RadioBand band, RadioRelayBlockEntity relay) {
        float tuning = SignalQuality.tuningFactor(frequency, relay.getInFrequency(), band);
        if (tuning <= 0.0f) {
            return false;
        }
        double dist = Math.sqrt(sourcePos.distSqr(relay.getBlockPos()));
        float distance = SignalQuality.distanceFactor(dist, rangeBlocks, band);
        if (distance <= 0.0f) {
            return false;
        }
        float los = RadioPropagation.lineOfSightFactor(level, sourcePos, relay.getBlockPos(), isAM);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        float q = SignalQuality.finalQuality(distance * los * weather, tuning);
        return q > 0.0f && !SignalQuality.isSquelched(q);
    }

    private static void deliverToReceiver(RadioReceiverBlockEntity rx, byte[] data, long sequence,
                                          short hearDistance, boolean stereo) {
        ServerStaticSource source = RadioVoiceService.getSource(rx);
        if (source == null) {
            source = RadioVoiceService.createSource(rx);
        }
        if (source == null) {
            return;
        }
        try {
            source.setStereo(stereo);
        } catch (Throwable ignored) {
        }
        source.sendAudioFrame(data, sequence, hearDistance);
    }

    /**
     * Full quality sample (used by non-hot paths / tools). Prefer {@link RadioReceiverBlockEntity#acceptsFrom}
     * on the voice path.
     */
    public static float qualityToReceiver(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            RadioReceiverBlockEntity rx
    ) {
        if (!ChannelKeys.matches(channelKey, rx.getChannelKey())) {
            return 0.0f;
        }
        if (rx.isAM() != isAM) {
            return 0.0f;
        }
        if (sourcePos.distSqr(rx.getBlockPos()) >= (double) rangeBlocks * (double) rangeBlocks) {
            return 0.0f;
        }
        RadioBand band = RadioBand.fromAm(isAM);
        float tuning = SignalQuality.tuningFactor(frequency, rx.getFrequency(), band);
        if (tuning <= 0.0f) {
            return 0.0f;
        }
        double dist = Math.sqrt(sourcePos.distSqr(rx.getBlockPos()));
        float distance = SignalQuality.distanceFactor(dist, rangeBlocks, band);
        if (distance <= 0.0f) {
            return 0.0f;
        }
        float los = RadioPropagation.lineOfSightFactor(level, sourcePos, rx.getBlockPos(), isAM);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        float rxAntenna = RadioPropagation.antennaReceiveMultiplier(level, rx.getBlockPos());
        return SignalQuality.finalQuality(distance * los * weather * rxAntenna, tuning);
    }
}
