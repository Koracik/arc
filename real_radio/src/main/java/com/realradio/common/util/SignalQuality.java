package com.realradio.common.util;

/**
 * Pure signal-quality math used by transmitter/receiver matching.
 */
public final class SignalQuality {
    /**
     * Off-tune voice falloff exponent. Linear proximity is raised to this power so
     * near-miss frequencies are much quieter (like real radio), while exact match stays full.
     */
    private static final float TUNING_CURVE_EXPONENT = 2.5f;

    /** Static noise loudness scale — kept intentionally quiet so speech stays primary. */
    private static final float STATIC_VOLUME_SCALE = 0.15f;

    private SignalQuality() {
    }

    /**
     * @param txFrequency transmitter frequency (band units)
     * @param rxFrequency receiver frequency (band units)
     * @param band        shared band (AM/FM must match)
     * @return tuning factor in [0, 1] — exact match = 1, edge of tolerance ≈ 0
     */
    public static float tuningFactor(float txFrequency, float rxFrequency, RadioBand band) {
        float delta = Math.abs(txFrequency - rxFrequency);
        float tolerance = band.tuningTolerance();
        if (delta > tolerance) {
            return 0.0f;
        }
        float linear = 1.0f - (delta / tolerance);
        // Sharp curve: half-tolerance ≈ 0.18 volume instead of 0.5
        return (float) Math.pow(linear, TUNING_CURVE_EXPONENT);
    }

    /**
     * @param distanceBlocks   euclidean distance transmitter ↔ receiver
     * @param transmitterRange max TX range in blocks (from frequency / band)
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
            // Linear falloff — AM degrades gradually
            factor = 1.0 - ratio;
        } else {
            // Cubic falloff — FM stays clean then drops sharply
            factor = 1.0 - (ratio * ratio * ratio);
        }
        return (float) Math.max(0.0, Math.min(1.0, factor));
    }

    public static float finalQuality(float distanceFactor, float tuningFactor) {
        return Math.max(0.0f, Math.min(1.0f, distanceFactor * tuningFactor));
    }

    /**
     * White-noise loudness for the Minecraft looping static sound.
     * Quieter overall; still louder when signal quality is poor.
     */
    public static float staticVolume(float finalQuality, float receiverVolume) {
        return (1.0f - finalQuality) * receiverVolume * STATIC_VOLUME_SCALE;
    }

    /**
     * Plasmo Voice source gain for decoded speech.
     * Scales with both receiver volume knob and signal quality (distance × tuning).
     */
    public static float voiceVolume(float finalQuality, float receiverVolume) {
        return finalQuality * receiverVolume;
    }
}
