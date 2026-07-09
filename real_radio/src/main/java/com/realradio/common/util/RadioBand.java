package com.realradio.common.util;

/**
 * Radio modulation band with realistic frequency ranges and step sizes.
 * <ul>
 *   <li>FM — 87.5…108.0 MHz, step 0.1 MHz</li>
 *   <li>AM — 530…1600 kHz, step 10 kHz</li>
 * </ul>
 * Frequencies are stored in band units (MHz for FM, kHz for AM).
 */
public enum RadioBand {
    FM(87.5f, 108.0f, 0.1f, 0.2f, 1.0f),
    AM(530.0f, 1600.0f, 10.0f, 20.0f, 3.0f);

    private final float minFrequency;
    private final float maxFrequency;
    private final float step;
    private final float tuningTolerance;
    private final float rangeMultiplier;

    RadioBand(float minFrequency, float maxFrequency, float step, float tuningTolerance, float rangeMultiplier) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.step = step;
        this.tuningTolerance = tuningTolerance;
        this.rangeMultiplier = rangeMultiplier;
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

    /** Multiplier applied to transmitter power for max range. AM reaches farther. */
    public float rangeMultiplier() {
        return rangeMultiplier;
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
