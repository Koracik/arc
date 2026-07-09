package com.realradio.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code config/real_radio-common.toml}).
 * Shared by client and server so GUI range labels match signal math.
 */
public final class RealRadioConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BASE_RANGE_BLOCKS;
    public static final ModConfigSpec.DoubleValue AM_NIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SQUELCH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STATIC_VOLUME_SCALE;

    static {
        BUILDER.comment("Real Radio — transmission range").push("range");

        BASE_RANGE_BLOCKS = BUILDER
                .comment(
                        "Base transmission range in blocks (default 2500).",
                        "FM uses about 1.0×–1.5× of this value; AM about 2.0×–3.0×.",
                        "Lower frequency within a band always reaches farther."
                )
                .defineInRange("baseRangeBlocks", 2500, 50, 100_000);

        AM_NIGHT_MULTIPLIER = BUILDER
                .comment(
                        "AM range multiplier at night (Minecraft night). 1.0 = disabled.",
                        "Classic skywave-style boost for medium-wave AM."
                )
                .defineInRange("amNightMultiplier", 1.3, 1.0, 3.0);

        BUILDER.pop();

        BUILDER.comment("Real Radio — audio / reception").push("audio");

        SQUELCH_THRESHOLD = BUILDER
                .comment(
                        "Mute static and voice when signal quality is below this (0.0–1.0).",
                        "0.0 = always hear hiss when powered; default 0.08 opens only on real signal."
                )
                .defineInRange("squelchThreshold", 0.08, 0.0, 1.0);

        STATIC_VOLUME_SCALE = BUILDER
                .comment("Overall white-noise loudness scale when signal is poor.")
                .defineInRange("staticVolumeScale", 0.15, 0.0, 1.0);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private RealRadioConfig() {
    }

    public static int baseRangeBlocks() {
        try {
            return BASE_RANGE_BLOCKS.get();
        } catch (Exception e) {
            return 2500;
        }
    }

    public static float amNightMultiplier() {
        try {
            return AM_NIGHT_MULTIPLIER.get().floatValue();
        } catch (Exception e) {
            return 1.3f;
        }
    }

    public static float squelchThreshold() {
        try {
            return SQUELCH_THRESHOLD.get().floatValue();
        } catch (Exception e) {
            return 0.08f;
        }
    }

    public static float staticVolumeScale() {
        try {
            return STATIC_VOLUME_SCALE.get().floatValue();
        } catch (Exception e) {
            return 0.15f;
        }
    }
}
