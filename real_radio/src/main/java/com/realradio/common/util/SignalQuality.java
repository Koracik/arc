package com.realradio.common.util;

/**
 * Pure signal-quality math used by transmitter/receiver matching.
 */
public final class SignalQuality {
    private SignalQuality() {
    }

    /**
     * @param txFrequency transmitter frequency (band units)
     * @param rxFrequency receiver frequency (band units)
     * @param band        shared band (AM/FM must match)
     * @return tuning factor in [0, 1]
     */
    public static float tuningFactor(float txFrequency, float rxFrequency, RadioBand band) {
        float delta = Math.abs(txFrequency - rxFrequency);
        float tolerance = band.tuningTolerance();
        if (delta > tolerance) {
            return 0.0f;
        }
        return 1.0f - (delta / tolerance);
    }

    /**
     * @param distanceBlocks euclidean distance transmitter ↔ receiver
     * @param transmitterRange configured TX power (blocks, 50…500)
     * @param band             AM/FM (affects max range and falloff curve)
     * @return distance factor in [0, 1]
     */
    public static float distanceFactor(double distanceBlocks, int transmitterRange, RadioBand band) {
        double maxRange = transmitterRange * band.rangeMultiplier();
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
     * {@code staticVolume = (1 - finalQuality) * receiverVolume * 0.5}
     */
    public static float staticVolume(float finalQuality, float receiverVolume) {
        return (1.0f - finalQuality) * receiverVolume * 0.5f;
    }

    /** Plasmo Voice source gain for decoded speech. */
    public static float voiceVolume(float finalQuality, float receiverVolume) {
        return finalQuality * receiverVolume;
    }
}
