package com.realradio.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code config/real_radio-common.toml}).
 */
public final class RealRadioConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BASE_RANGE_BLOCKS;
    public static final ModConfigSpec.DoubleValue AM_NIGHT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ANTENNA_HEIGHT_BONUS;

    public static final ModConfigSpec.DoubleValue SQUELCH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STATIC_VOLUME_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_AGC;
    public static final ModConfigSpec.DoubleValue AGC_EXPONENT;
    public static final ModConfigSpec.BooleanValue REALISM_MODE;

    public static final ModConfigSpec.BooleanValue ENABLE_LINE_OF_SIGHT;
    public static final ModConfigSpec.IntValue LOS_SAMPLE_STEP;
    public static final ModConfigSpec.DoubleValue LOS_MAX_PENALTY;

    public static final ModConfigSpec.DoubleValue FM_RAIN_FACTOR;
    public static final ModConfigSpec.DoubleValue FM_THUNDER_FACTOR;

    static {
        BUILDER.comment("Real Radio — transmission range").push("range");

        BASE_RANGE_BLOCKS = BUILDER
                .comment(
                        "Base transmission range in blocks (default 2500).",
                        "FM uses about 1.0×–1.5× of this value; AM about 2.0×–3.0×."
                )
                .defineInRange("baseRangeBlocks", 2500, 50, 100_000);

        AM_NIGHT_MULTIPLIER = BUILDER
                .comment("AM range multiplier at night. 1.0 = disabled.")
                .defineInRange("amNightMultiplier", 1.3, 1.0, 3.0);

        ANTENNA_HEIGHT_BONUS = BUILDER
                .comment(
                        "Max range multiplier for high antenna placement (Y≈160).",
                        "1.0 = no height bonus. Default 1.35."
                )
                .defineInRange("antennaHeightBonus", 1.35, 1.0, 2.5);

        BUILDER.pop();

        BUILDER.comment("Real Radio — audio / reception").push("audio");

        SQUELCH_THRESHOLD = BUILDER
                .comment(
                        "Mute VOICE only when quality below this (0–1).",
                        "Static hiss always plays while the receiver is powered (like a real radio)."
                )
                .defineInRange("squelchThreshold", 0.08, 0.0, 1.0);

        STATIC_VOLUME_SCALE = BUILDER
                .comment(
                        "White-noise loudness scale (keep low). Hiss is continuous while powered;",
                        "quieter when a station is locked. Default 0.05."
                )
                .defineInRange("staticVolumeScale", 0.05, 0.0, 1.0);

        ENABLE_AGC = BUILDER
                .comment("Soft AGC: slightly lifts weak but above-squelch voice.")
                .define("enableAgc", true);

        AGC_EXPONENT = BUILDER
                .comment("AGC curve exponent (<1 lifts weak signals). Default 0.85.")
                .defineInRange("agcExponent", 0.85, 0.5, 1.0);

        REALISM_MODE = BUILDER
                .comment(
                        "Realism mode: hide spectrum peaks, S-meter, signal %, station markers.",
                        "Player must find stations by ear only (recommended for immersion)."
                )
                .define("realismMode", true);

        BUILDER.pop();

        BUILDER.comment("Real Radio — propagation / environment").push("propagation");

        ENABLE_LINE_OF_SIGHT = BUILDER
                .comment("FM loses quality through solid blocks / terrain ridges.")
                .define("enableLineOfSight", true);

        LOS_SAMPLE_STEP = BUILDER
                .comment("Blocks between LOS samples (higher = cheaper, coarser).")
                .defineInRange("losSampleStep", 6, 2, 32);

        LOS_MAX_PENALTY = BUILDER
                .comment("Max quality fraction lost to full obstruction (0–1).")
                .defineInRange("losMaxPenalty", 0.85, 0.0, 1.0);

        FM_RAIN_FACTOR = BUILDER
                .comment("FM quality multiplier while raining.")
                .defineInRange("fmRainFactor", 0.85, 0.2, 1.0);

        FM_THUNDER_FACTOR = BUILDER
                .comment("FM quality multiplier during thunderstorm.")
                .defineInRange("fmThunderFactor", 0.70, 0.1, 1.0);

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

    public static float antennaHeightBonus() {
        try {
            return ANTENNA_HEIGHT_BONUS.get().floatValue();
        } catch (Exception e) {
            return 1.35f;
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
            return 0.05f;
        }
    }

    public static boolean enableAgc() {
        try {
            return ENABLE_AGC.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** Hide station assist UI (spectrum, S-meter quality) — find stations by ear. */
    public static boolean realismMode() {
        try {
            return REALISM_MODE.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static float agcExponent() {
        try {
            return AGC_EXPONENT.get().floatValue();
        } catch (Exception e) {
            return 0.85f;
        }
    }

    public static boolean enableLineOfSight() {
        try {
            return ENABLE_LINE_OF_SIGHT.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static int losSampleStep() {
        try {
            return LOS_SAMPLE_STEP.get();
        } catch (Exception e) {
            return 6;
        }
    }

    public static float losMaxPenalty() {
        try {
            return LOS_MAX_PENALTY.get().floatValue();
        } catch (Exception e) {
            return 0.85f;
        }
    }

    public static float fmRainFactor() {
        try {
            return FM_RAIN_FACTOR.get().floatValue();
        } catch (Exception e) {
            return 0.85f;
        }
    }

    public static float fmThunderFactor() {
        try {
            return FM_THUNDER_FACTOR.get().floatValue();
        } catch (Exception e) {
            return 0.70f;
        }
    }
}
