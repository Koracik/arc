package com.realradio.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code config/real_radio-common.toml}).
 * Shared by client and server so GUI range labels match signal math.
 */
public final class RealRadioConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    /**
     * Base transmission range in blocks. Actual range scales with band and frequency:
     * <ul>
     *   <li>FM: {@code base} … {@code base × 1.5} (lower MHz → farther)</li>
     *   <li>AM: {@code base × 2} … {@code base × 3} (lower kHz → farther)</li>
     * </ul>
     */
    public static final ModConfigSpec.IntValue BASE_RANGE_BLOCKS;

    static {
        BUILDER.comment("Real Radio — transmission range").push("range");

        BASE_RANGE_BLOCKS = BUILDER
                .comment(
                        "Base transmission range in blocks (default 2500).",
                        "FM uses about 1.0×–1.5× of this value; AM about 2.0×–3.0×.",
                        "Lower frequency within a band always reaches farther."
                )
                .defineInRange("baseRangeBlocks", 2500, 50, 100_000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private RealRadioConfig() {
    }

    /** Safe accessor — falls back to 2500 if config is not loaded yet. */
    public static int baseRangeBlocks() {
        try {
            return BASE_RANGE_BLOCKS.get();
        } catch (Exception e) {
            return 2500;
        }
    }
}
