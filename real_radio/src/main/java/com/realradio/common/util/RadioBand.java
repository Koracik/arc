package com.realradio.common.util;

/**
 * Radio modulation band with realistic frequency ranges and step sizes.
 * <ul>
 *   <li>FM — 87.5…108.0 MHz, step 0.1 MHz — short (line-of-sight) range</li>
 *   <li>AM — 530…1600 kHz, step 10 kHz — longer ground-wave range</li>
 * </ul>
 * Frequencies are stored in band units (MHz for FM, kHz for AM).
 * <p>
 * Transmission range is derived from frequency (lower → farther), not player-chosen power.
 */
public enum RadioBand {
    /**
     * FM VHF: ~100–180 blocks. Lower MHz propagates slightly farther.
     */
    FM(87.5f, 108.0f, 0.1f, 0.2f, 180, 100),
    /**
     * AM MF: ~280–520 blocks. Lower kHz propagates farther (classic AM behaviour).
     */
    AM(530.0f, 1600.0f, 10.0f, 20.0f, 520, 280);

    private final float minFrequency;
    private final float maxFrequency;
    private final float step;
    private final float tuningTolerance;
    private final int maxRangeBlocks;
    private final int minRangeBlocks;

    RadioBand(float minFrequency, float maxFrequency, float step, float tuningTolerance,
              int maxRangeBlocks, int minRangeBlocks) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.step = step;
        this.tuningTolerance = tuningTolerance;
        this.maxRangeBlocks = maxRangeBlocks;
        this.minRangeBlocks = minRangeBlocks;
    }

    public float minFrequency() {
        return minFrequency;
    }

    public float maxFrequency() {
        return maxFrequency;
    }

    public float step() {
        return step;
    }

    /** Allowed absolute frequency mismatch still producing a (noisy) signal. */
    public float tuningTolerance() {
        return tuningTolerance;
    }

    /**
     * Effective broadcast range in blocks for the given frequency.
     * Lower frequencies reach farther within the band (and AM &gt; FM overall).
     */
    public int rangeBlocks(float frequency) {
        float f = clamp(frequency);
        float span = maxFrequency - minFrequency;
        if (span <= 0.0f) {
            return maxRangeBlocks;
        }
        // t = 0 at lowest freq (max range), 1 at highest (min range)
        float t = (f - minFrequency) / span;
        t = Math.max(0.0f, Math.min(1.0f, t));
        return Math.round(maxRangeBlocks + (minRangeBlocks - maxRangeBlocks) * t);
    }

    /** @deprecated use {@link #rangeBlocks(float)}; kept for call-site migration */
    @Deprecated
    public float rangeMultiplier() {
        // Approximate mid-band AM/FM ratio for any leftover callers
        return isAM() ? 3.0f : 1.0f;
    }

    public boolean isAM() {
        return this == AM;
    }

    public static RadioBand fromAm(boolean isAM) {
        return isAM ? AM : FM;
    }

    public float defaultFrequency() {
        return this == FM ? 98.0f : 1000.0f;
    }

    public float clamp(float frequency) {
        return Math.max(minFrequency, Math.min(maxFrequency, frequency));
    }

    /** Snap to the band step after clamping. */
    public float snap(float frequency) {
        float clamped = clamp(frequency);
        float snapped = Math.round((clamped - minFrequency) / step) * step + minFrequency;
        return clamp(snapped);
    }

    public String unitLabel() {
        return this == FM ? "MHz" : "kHz";
    }

    public String format(float frequency) {
        if (this == FM) {
            return String.format("%.1f %s", frequency, unitLabel());
        }
        return String.format("%.0f %s", frequency, unitLabel());
    }
}
