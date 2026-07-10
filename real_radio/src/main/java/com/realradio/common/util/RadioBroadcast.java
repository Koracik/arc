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
 */
public final class RadioBroadcast {
    private RadioBroadcast() {
    }

    /**
     * Broadcast Opus frames from a physical transmitter block.
     */
    public static void fromTransmitter(RadioTransmitterBlockEntity tx, byte[] data, long sequence,
                                       short hearDistance, boolean stereo) {
        if (tx == null || !tx.isActive() || tx.getLevel() == null) {
            return;
        }
        Set<RadioManager.DimPos> visited = new HashSet<>();
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

    /**
     * Broadcast from a virtual source (handheld TX, tape playback, relay out).
     */
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
        if (level == null || data == null || data.length == 0 || rangeBlocks <= 0) {
            return;
        }
        RadioBand band = RadioBand.fromAm(isAM);

        // Deliver to block receivers
        for (RadioReceiverBlockEntity rx : RadioManager.receivers()) {
            if (!rx.isActive() || rx.getLevel() != level || rx.isAM() != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(channelKey, rx.getChannelKey())) {
                continue;
            }
            float quality = qualityToReceiver(level, sourcePos, frequency, isAM, channelKey, rangeBlocks, rx);
            if (quality <= 0.0f) {
                continue;
            }
            if (!isDominantForReceiver(level, sourcePos, frequency, isAM, channelKey, rangeBlocks, rx, quality)) {
                continue;
            }
            if (SignalQuality.isSquelched(rx.getSignalQuality())) {
                continue;
            }
            deliverToReceiver(rx, data, sequence, hearDistance, stereo);
            if (rx.isRecording()) {
                AirRecording.captureFrame(rx, data, sequence, stereo, frequency, isAM, channelKey);
            }
        }

        // Handheld virtual receivers
        HandheldRadioService.deliverToHandhelds(
                level, sourcePos, frequency, isAM, channelKey, rangeBlocks,
                data, sequence, hearDistance, stereo
        );

        int maxHops = RealRadioConfig.maxRelayHops();
        if (hop >= maxHops) {
            return;
        }

        // Feed relays (RX side) and retransmit on out channel
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
            float q = qualityToRelayIn(level, sourcePos, frequency, isAM, channelKey, rangeBlocks, relay);
            if (q <= 0.0f || SignalQuality.isSquelched(q)) {
                continue;
            }
            if (!isDominantForRelay(level, sourcePos, frequency, isAM, channelKey, rangeBlocks, relay, q)) {
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
        return SignalQuality.finalQuality(distance * los * weather, tuning);
    }

    private static boolean isDominantForReceiver(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            RadioReceiverBlockEntity rx,
            float candidate
    ) {
        float best = candidate;
        // Compare against other active transmitters
        for (RadioTransmitterBlockEntity other : RadioManager.transmitters()) {
            if (!other.isActive() || other.getLevel() != level) {
                continue;
            }
            float q = rx.rawQualityFrom(other);
            if (q > best) {
                return false;
            }
        }
        // Compare against other virtual sources is approximated by TX list + relays out
        for (RadioRelayBlockEntity other : RadioManager.relays()) {
            if (!other.isActive() || other.getLevel() != level) {
                continue;
            }
            if (other.getBlockPos().equals(sourcePos)) {
                continue;
            }
            float q = qualityToReceiver(level, other.getBlockPos(), other.getOutFrequency(),
                    other.isOutAM(), other.getOutChannelKey(), other.getOutRange(), rx);
            if (q > best + 0.001f) {
                return false;
            }
        }
        return true;
    }

    private static float qualityToRelayIn(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            RadioRelayBlockEntity relay
    ) {
        RadioBand band = RadioBand.fromAm(isAM);
        float tuning = SignalQuality.tuningFactor(frequency, relay.getInFrequency(), band);
        if (tuning <= 0.0f) {
            return 0.0f;
        }
        double dist = Math.sqrt(sourcePos.distSqr(relay.getBlockPos()));
        float distance = SignalQuality.distanceFactor(dist, rangeBlocks, band);
        if (distance <= 0.0f) {
            return 0.0f;
        }
        float los = RadioPropagation.lineOfSightFactor(level, sourcePos, relay.getBlockPos(), isAM);
        float weather = RadioPropagation.weatherFactor(level, isAM);
        return SignalQuality.finalQuality(distance * los * weather, tuning);
    }

    private static boolean isDominantForRelay(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            RadioRelayBlockEntity relay,
            float candidate
    ) {
        float best = candidate;
        for (RadioTransmitterBlockEntity other : RadioManager.transmitters()) {
            if (!other.isActive() || other.getLevel() != level) {
                continue;
            }
            if (!ChannelKeys.matches(other.getChannelKey(), relay.getInChannelKey())) {
                continue;
            }
            float q = qualityToRelayIn(level, other.getBlockPos(), other.getFrequency(),
                    other.isAM(), other.getChannelKey(), other.getRange(), relay);
            if (q > best + 0.001f) {
                return false;
            }
        }
        for (RadioRelayBlockEntity other : RadioManager.relays()) {
            if (!other.isActive() || other.getLevel() != level || other.getBlockPos().equals(sourcePos)) {
                continue;
            }
            if (!ChannelKeys.matches(other.getOutChannelKey(), relay.getInChannelKey())) {
                continue;
            }
            float q = qualityToRelayIn(level, other.getBlockPos(), other.getOutFrequency(),
                    other.isOutAM(), other.getOutChannelKey(), other.getOutRange(), relay);
            if (q > best + 0.001f) {
                return false;
            }
        }
        return true;
    }
}
