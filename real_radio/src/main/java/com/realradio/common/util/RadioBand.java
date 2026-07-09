package com.realradio.common.util;

import com.realradio.config.RealRadioConfig;

/**
 * Radio modulation band with realistic frequency ranges and step sizes.
 * <ul>
 *   <li>FM — 87.5…108.0 MHz, step 0.1 MHz — shorter (line-of-sight) range</li>
 *   <li>AM — 530…1600 kHz, step 10 kHz — longer ground-wave range</li>
 * </ul>
 * Frequencies are stored in band units (MHz for FM, kHz for AM).
 * <p>
 * Transmission range is derived from {@link RealRadioConfig#baseRangeBlocks()} and frequency
 * (lower → farther; AM farther than FM), not player-chosen power.
 */
public enum RadioBand {
    /**
     * FM: {@code base × 1.0} … {@code base × 1.5} (low MHz = farther).
     */
    FM(87.5f, 108.0f, 0.1f, 0.2f, 1.5f, 1.0f),
    /**
     * AM: {@code base × 2.0} … {@code base × 3.0} (low kHz = farther).
     */
    AM(530.0f, 1600.0f, 10.0f, 20.0f, 3.0f, 2.0f);

    private final float minFrequency;
    private final float maxFrequency;
    private final float step;
    private final float tuningTolerance;
    /** Multiplier of base range at the lowest frequency in this band. */
    private final float maxRangeMultiplier;
    /** Multiplier of base range at the highest frequency in this band. */
    private final float minRangeMultiplier;

    RadioBand(float minFrequency, float maxFrequency, float step, float tuningTolerance,
              float maxRangeMultiplier, float minRangeMultiplier) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.step = step;
        this.tuningTolerance = tuningTolerance;
        this.maxRangeMultiplier = maxRangeMultiplier;
        this.minRangeMultiplier = minRangeMultiplier;
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
     * Scaled from {@link RealRadioConfig#baseRangeBlocks()} (default 2500).
     * Lower frequencies reach farther within the band (and AM &gt; FM overall).
     */
    public int rangeBlocks(float frequency) {
        int base = RealRadioConfig.baseRangeBlocks();
        float f = clamp(frequency);
        float span = maxFrequency - minFrequency;
        float mult;
        if (span <= 0.0f) {
            mult = maxRangeMultiplier;
        } else {
            // t = 0 at lowest freq (max range), 1 at highest (min range)
            float t = (f - minFrequency) / span;
            t = Math.max(0.0f, Math.min(1.0f, t));
            mult = maxRangeMultiplier + (minRangeMultiplier - maxRangeMultiplier) * t;
        }
        return Math.max(1, Math.round(base * mult));
    }

    /** @deprecated use {@link #rangeBlocks(float)}; kept for call-site migration */
    @Deprecated
    public float rangeMultiplier() {
        return isAM() ? 2.5f : 1.25f;
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
