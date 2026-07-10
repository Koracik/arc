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
    public static final ModConfigSpec.DoubleValue ANTENNA_ROD_BONUS_PER_ROD;
    public static final ModConfigSpec.DoubleValue ANTENNA_RX_BONUS_PER_ROD;
    public static final ModConfigSpec.IntValue ANTENNA_MAX_RODS;
    public static final ModConfigSpec.IntValue ANTENNA_SCAN_HEIGHT;
    public static final ModConfigSpec.IntValue ANTENNA_SCAN_RADIUS;

    public static final ModConfigSpec.DoubleValue SQUELCH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STATIC_VOLUME_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_AGC;
    public static final ModConfigSpec.DoubleValue AGC_EXPONENT;
    public static final ModConfigSpec.BooleanValue REALISM_MODE;

    public static final ModConfigSpec.BooleanValue ENABLE_LINE_OF_SIGHT;
    public static final ModConfigSpec.IntValue LOS_SAMPLE_STEP;
    public static final ModConfigSpec.DoubleValue LOS_MAX_PENALTY;
    public static final ModConfigSpec.IntValue LOS_CACHE_GRID;
    public static final ModConfigSpec.IntValue LOS_CACHE_TTL_MS;
    public static final ModConfigSpec.IntValue LOS_CACHE_MAX;

    public static final ModConfigSpec.DoubleValue FM_RAIN_FACTOR;
    public static final ModConfigSpec.DoubleValue FM_THUNDER_FACTOR;

    public static final ModConfigSpec.BooleanValue REQUIRE_MATCHING_KEY;
    public static final ModConfigSpec.IntValue MAX_RELAY_HOPS;
    public static final ModConfigSpec.BooleanValue ENABLE_COVERAGE_OVERLAY;
    public static final ModConfigSpec.IntValue MAX_RECORDING_SECONDS;
    public static final ModConfigSpec.DoubleValue HANDHELD_RANGE_FACTOR;

    static {
        // --- gameplay (toggle modes here) ---
        BUILDER.comment(
                "Real Radio — gameplay modes",
                "File: config/real_radio-common.toml (client AND server)"
        ).push("gameplay");

        REALISM_MODE = BUILDER
                .comment(
                        "=== REALISM MODE (enable/disable here) ===",
                        "true  = hide spectrum peaks, S-meter, signal % — find stations by ear only",
                        "false = show station assist (spectrum + quality meter)",
                        "Set this on both client and dedicated server for multiplayer."
                )
                .define("realismMode", true);

        BUILDER.pop();

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
                        "Soft max range multiplier for high radio placement (Y≈160).",
                        "1.0 = no height bonus. Stacks with copper lightning-rod mast.",
                        "Default 1.35."
                )
                .defineInRange("antennaHeightBonus", 1.35, 1.0, 2.5);

        ANTENNA_ROD_BONUS_PER_ROD = BUILDER
                .comment(
                        "TX/relay range bonus per copper lightning rod in the mast above the radio.",
                        "Example: 0.08 × 8 rods = +64% range. Default 0.08."
                )
                .defineInRange("antennaRodBonusPerRod", 0.08, 0.0, 0.5);

        ANTENNA_RX_BONUS_PER_ROD = BUILDER
                .comment(
                        "Receiver quality multiplier bonus per lightning rod on the RX mast.",
                        "Example: 0.04 × 5 rods ≈ +20% quality. Default 0.04."
                )
                .defineInRange("antennaRxBonusPerRod", 0.04, 0.0, 0.5);

        ANTENNA_MAX_RODS = BUILDER
                .comment("Max lightning rods counted for antenna bonus.")
                .defineInRange("antennaMaxRods", 16, 0, 64);

        ANTENNA_SCAN_HEIGHT = BUILDER
                .comment("How many blocks above the radio to scan for lightning rods.")
                .defineInRange("antennaScanHeight", 24, 1, 64);

        ANTENNA_SCAN_RADIUS = BUILDER
                .comment("Horizontal radius (blocks) around the radio for the mast (0 = only column above).")
                .defineInRange("antennaScanRadius", 1, 0, 3);

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

        LOS_CACHE_GRID = BUILDER
                .comment(
                        "Quantize LOS endpoints to this grid (blocks) for multi-chunk cache hits.",
                        "Higher = cheaper, coarser. 1 = exact BlockPos keys."
                )
                .defineInRange("losCacheGrid", 8, 1, 32);

        LOS_CACHE_TTL_MS = BUILDER
                .comment("How long a LOS cache entry stays valid (ms).")
                .defineInRange("losCacheTtlMs", 1500, 200, 30_000);

        LOS_CACHE_MAX = BUILDER
                .comment("Max LOS cache entries before expired/half eviction.")
                .defineInRange("losCacheMax", 4096, 256, 65_536);

        FM_RAIN_FACTOR = BUILDER
                .comment("FM quality multiplier while raining.")
                .defineInRange("fmRainFactor", 0.85, 0.2, 1.0);

        FM_THUNDER_FACTOR = BUILDER
                .comment("FM quality multiplier during thunderstorm.")
                .defineInRange("fmThunderFactor", 0.70, 0.1, 1.0);

        BUILDER.pop();

        BUILDER.comment("Real Radio — channel key / relay / extras (v1.2)").push("features");

        REQUIRE_MATCHING_KEY = BUILDER
                .comment(
                        "When true, TX and RX channelKey must match (0 = open channel).",
                        "Mismatched keys produce no voice path."
                )
                .define("requireMatchingKey", true);

        MAX_RELAY_HOPS = BUILDER
                .comment("Max relay retransmissions per audio frame (anti-loop).")
                .defineInRange("maxRelayHops", 3, 0, 16);

        ENABLE_COVERAGE_OVERLAY = BUILDER
                .comment(
                        "Allow creative/debug coverage map overlay (client).",
                        "Still disabled while realismMode is true unless player is creative."
                )
                .define("enableCoverageOverlay", true);

        MAX_RECORDING_SECONDS = BUILDER
                .comment("Max seconds of air to buffer into a radio tape.")
                .defineInRange("maxRecordingSeconds", 45, 5, 180);

        HANDHELD_RANGE_FACTOR = BUILDER
                .comment("Handheld radio TX range as fraction of block TX range.")
                .defineInRange("handheldRangeFactor", 0.35, 0.05, 1.0);

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

    public static float antennaRodBonusPerRod() {
        try {
            return ANTENNA_ROD_BONUS_PER_ROD.get().floatValue();
        } catch (Exception e) {
            return 0.08f;
        }
    }

    public static float antennaRxBonusPerRod() {
        try {
            return ANTENNA_RX_BONUS_PER_ROD.get().floatValue();
        } catch (Exception e) {
            return 0.04f;
        }
    }

    public static int antennaMaxRods() {
        try {
            return ANTENNA_MAX_RODS.get();
        } catch (Exception e) {
            return 16;
        }
    }

    public static int antennaScanHeight() {
        try {
            return ANTENNA_SCAN_HEIGHT.get();
        } catch (Exception e) {
            return 24;
        }
    }

    public static int antennaScanRadius() {
        try {
            return ANTENNA_SCAN_RADIUS.get();
        } catch (Exception e) {
            return 1;
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

    public static int losCacheGrid() {
        try {
            return LOS_CACHE_GRID.get();
        } catch (Exception e) {
            return 8;
        }
    }

    public static long losCacheTtlMs() {
        try {
            return LOS_CACHE_TTL_MS.get().longValue();
        } catch (Exception e) {
            return 1500L;
        }
    }

    public static int losCacheMax() {
        try {
            return LOS_CACHE_MAX.get();
        } catch (Exception e) {
            return 4096;
        }
    }

    public static boolean requireMatchingKey() {
        try {
            return REQUIRE_MATCHING_KEY.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static int maxRelayHops() {
        try {
            return MAX_RELAY_HOPS.get();
        } catch (Exception e) {
            return 3;
        }
    }

    public static boolean enableCoverageOverlay() {
        try {
            return ENABLE_COVERAGE_OVERLAY.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static int maxRecordingSeconds() {
        try {
            return MAX_RECORDING_SECONDS.get();
        } catch (Exception e) {
            return 45;
        }
    }

    public static float handheldRangeFactor() {
        try {
            return HANDHELD_RANGE_FACTOR.get().floatValue();
        } catch (Exception e) {
            return 0.35f;
        }
    }
}
