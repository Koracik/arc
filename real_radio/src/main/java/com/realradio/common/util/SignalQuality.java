package com.realradio.common.util;

import com.realradio.config.RealRadioConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure signal-quality math used by transmitter/receiver matching.
 */
public final class SignalQuality {
    /**
     * Off-tune voice falloff exponent. Linear proximity is raised to this power so
     * near-miss frequencies are much quieter (like real radio), while exact match stays full.
     */
    private static final float TUNING_CURVE_EXPONENT = 2.5f;

    /** Max quality reduction from adjacent-channel interference. */
    private static final float MAX_INTERFERENCE = 0.40f;

    /** FM capture ratio: if best ≥ this × second, second is fully suppressed. */
    private static final float FM_CAPTURE_RATIO = 1.5f;

    private SignalQuality() {
    }

    /**
     * @return tuning factor in [0, 1] — exact match = 1, edge of tolerance ≈ 0
     */
    public static float tuningFactor(float txFrequency, float rxFrequency, RadioBand band) {
        float delta = Math.abs(txFrequency - rxFrequency);
        float tolerance = band.tuningTolerance();
        if (delta > tolerance) {
            return 0.0f;
        }
        float linear = 1.0f - (delta / tolerance);
        return (float) Math.pow(linear, TUNING_CURVE_EXPONENT);
    }

    /**
     * @param distanceBlocks   euclidean distance transmitter ↔ receiver
     * @param transmitterRange max TX range in blocks (from frequency / band / night)
     * @param band             AM/FM (affects falloff curve)
     * @return distance factor in [0, 1]
     */
    public static float distanceFactor(double distanceBlocks, int transmitterRange, RadioBand band) {
        double maxRange = transmitterRange;
        if (maxRange <= 0.0 || distanceBlocks >= maxRange) {
            return 0.0f;
        }
        double ratio = distanceBlocks / maxRange;
        double factor;
        if (band.isAM()) {
            factor = 1.0 - ratio;
        } else {
            factor = 1.0 - (ratio * ratio * ratio);
        }
        return (float) Math.max(0.0, Math.min(1.0, factor));
    }

    public static float finalQuality(float distanceFactor, float tuningFactor) {
        return Math.max(0.0f, Math.min(1.0f, distanceFactor * tuningFactor));
    }

    /**
     * Combines multiple raw station qualities into one reception quality.
     * <ul>
     *   <li>FM capture: dominant station fully wins when clearly stronger</li>
     *   <li>Otherwise adjacent-channel interference reduces best quality</li>
     * </ul>
     */
    public static float combineStationQualities(List<Float> rawQualities, boolean isAM) {
        if (rawQualities == null || rawQualities.isEmpty()) {
            return 0.0f;
        }
        List<Float> sorted = new ArrayList<>(rawQualities.size());
        for (Float q : rawQualities) {
            if (q != null && q > 0.0f) {
                sorted.add(q);
            }
        }
        if (sorted.isEmpty()) {
            return 0.0f;
        }
        sorted.sort(Comparator.reverseOrder());
        float best = sorted.get(0);
        if (sorted.size() == 1) {
            return clamp01(best);
        }

        float second = sorted.get(1);
        // FM capture effect — strong station "locks" the receiver
        if (!isAM && second > 0.0f && best >= FM_CAPTURE_RATIO * second) {
            return clamp01(best);
        }

        float interferenceSum = 0.0f;
        for (int i = 1; i < sorted.size(); i++) {
            interferenceSum += sorted.get(i) / best;
        }
        float reduction = Math.min(MAX_INTERFERENCE, 0.25f * interferenceSum);
        return clamp01(best * (1.0f - reduction));
    }

    /**
     * Index of the strongest raw station, or -1 if empty.
     * Used so only the dominant TX is relayed (FM capture / clean AM).
     */
    public static int dominantStationIndex(List<Float> rawQualities) {
        if (rawQualities == null || rawQualities.isEmpty()) {
            return -1;
        }
        int bestIdx = -1;
        float best = 0.0f;
        for (int i = 0; i < rawQualities.size(); i++) {
            float q = rawQualities.get(i) != null ? rawQualities.get(i) : 0.0f;
            if (q > best) {
                best = q;
                bestIdx = i;
            }
        }
        return best > 0.0f ? bestIdx : -1;
    }

    /**
     * White-noise for a powered receiver. Always present like a real radio:
     * loudest on empty band, fades as signal locks. Not affected by voice squelch.
     */
    public static float staticVolume(float finalQuality, float receiverVolume) {
        float scale = RealRadioConfig.staticVolumeScale();
        float q = clamp01(finalQuality);
        // Empty band → full hiss; perfect lock → near silence (tiny residual floor)
        float residual = 0.04f;
        float hiss = residual + (1.0f - residual) * (1.0f - q);
        return hiss * receiverVolume * scale;
    }

    public static float voiceVolume(float finalQuality, float receiverVolume) {
        // Squelch only mutes speech, never the carrier hiss
        if (isSquelched(finalQuality)) {
            return 0.0f;
        }
        float q = finalQuality;
        if (RealRadioConfig.enableAgc() && q > 0.0f) {
            // Soft AGC: exponent < 1 lifts weak-but-open signals, full stays ~1
            q = (float) Math.pow(q, RealRadioConfig.agcExponent());
        }
        return clamp01(q) * receiverVolume;
    }

    public static boolean isSquelched(float finalQuality) {
        return finalQuality < RealRadioConfig.squelchThreshold();
    }

    public static Component qualityWord(float quality) {
        if (quality <= 0.001f) {
            return Component.translatable("gui.real_radio.signal_none");
        }
        if (quality < 0.20f) {
            return Component.translatable("gui.real_radio.signal_weak");
        }
        if (quality < 0.45f) {
            return Component.translatable("gui.real_radio.signal_fair");
        }
        if (quality < 0.75f) {
            return Component.translatable("gui.real_radio.signal_good");
        }
        return Component.translatable("gui.real_radio.signal_excellent");
    }

    /** Color ARGB for quality word / meter fill. */
    public static int qualityColor(float quality) {
        if (quality <= 0.001f) {
            return 0xFF665544;
        }
        if (quality < 0.20f) {
            return 0xFFCC4433;
        }
        if (quality < 0.45f) {
            return 0xFFCCAA33;
        }
        if (quality < 0.75f) {
            return 0xFF66BB44;
        }
        return 0xFF33CC66;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
