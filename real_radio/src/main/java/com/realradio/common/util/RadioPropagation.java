package com.realradio.common.util;

import com.realradio.config.RealRadioConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Environmental propagation: line-of-sight, antenna height, weather.
 * LOS results are cached briefly to avoid raycasting every tick at long range.
 */
public final class RadioPropagation {
    private static final Map<Long, CacheEntry> LOS_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 1000L;
    private static final int CACHE_MAX = 2048;

    private RadioPropagation() {
    }

    /**
     * Multiplier for max TX range from placement height (sea-level-ish).
     * Higher antenna → farther reach (both bands).
     */
    public static float antennaRangeMultiplier(int blockY) {
        float maxBonus = RealRadioConfig.antennaHeightBonus();
        if (maxBonus <= 1.0f) {
            return 1.0f;
        }
        // y=64 → ~1.0, y=160 → approaches maxBonus, y=0 → slight penalty floor 0.9
        float t = (blockY - 64.0f) / 96.0f;
        t = Math.max(-0.5f, Math.min(1.0f, t));
        if (t < 0.0f) {
            return 1.0f + t * 0.2f; // down to 0.9
        }
        return 1.0f + t * (maxBonus - 1.0f);
    }

    /**
     * Weather attenuation for the given band. Rain/thunder mainly hurt FM (VHF).
     */
    public static float weatherFactor(Level level, boolean isAM) {
        if (level == null) {
            return 1.0f;
        }
        boolean rain = level.isRaining();
        boolean thunder = level.isThundering();
        if (!rain && !thunder) {
            return 1.0f;
        }
        if (isAM) {
            // AM is more resilient; mild reduction only in storms
            return thunder ? 0.92f : 0.97f;
        }
        if (thunder) {
            return RealRadioConfig.fmThunderFactor();
        }
        return RealRadioConfig.fmRainFactor();
    }

    /**
     * Line-of-sight / obstruction factor in [min, 1].
     * AM is almost immune (ground wave); FM loses quality through solid blocks.
     */
    public static float lineOfSightFactor(Level level, BlockPos from, BlockPos to, boolean isAM) {
        if (level == null || !RealRadioConfig.enableLineOfSight()) {
            return 1.0f;
        }
        if (isAM) {
            // Slight attenuation only for extreme occlusion
            float raw = sampleObstruction(level, from, to, true);
            return 0.75f + 0.25f * raw;
        }
        return sampleObstruction(level, from, to, false);
    }

    private static float sampleObstruction(Level level, BlockPos from, BlockPos to, boolean soft) {
        long key = cacheKey(from, to, soft);
        long now = System.currentTimeMillis();
        CacheEntry cached = LOS_CACHE.get(key);
        if (cached != null && now - cached.timeMs < CACHE_TTL_MS) {
            return cached.factor;
        }

        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        double dist = a.distanceTo(b);
        if (dist < 2.0) {
            return cacheAndReturn(key, 1.0f, now);
        }

        int step = Math.max(2, RealRadioConfig.losSampleStep());
        int samples = Math.max(4, (int) (dist / step));
        samples = Math.min(samples, 128); // hard cap for very long links

        int solid = 0;
        int total = 0;
        for (int i = 1; i < samples; i++) {
            double t = i / (double) samples;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            BlockPos p = BlockPos.containing(x, y, z);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            total++;
            if (state.isSolidRender(level, p) || state.canOcclude()) {
                // Count full cubes / occluding blocks as obstruction
                if (!state.getCollisionShape(level, p).isEmpty()) {
                    solid++;
                }
            }
        }

        if (total == 0) {
            return cacheAndReturn(key, 1.0f, now);
        }

        float blocked = solid / (float) total;
        float maxPenalty = RealRadioConfig.losMaxPenalty();
        float factor;
        if (soft) {
            factor = 1.0f - blocked * maxPenalty * 0.35f;
        } else {
            // FM: sharp LOS — many blocks = deep fade
            factor = 1.0f - blocked * maxPenalty;
            // Terrain ridge bonus check: if path goes under surface a lot, extra fade
            factor *= ridgePenalty(level, a, b, samples);
        }
        factor = Math.max(1.0f - maxPenalty, Math.min(1.0f, factor));
        return cacheAndReturn(key, factor, now);
    }

    /** Extra penalty when the ray travels below local terrain height. */
    private static float ridgePenalty(Level level, Vec3 a, Vec3 b, int samples) {
        int under = 0;
        int checks = 0;
        int stride = Math.max(1, samples / 16);
        for (int i = 1; i < samples; i += stride) {
            double t = i / (double) samples;
            int x = (int) Math.floor(a.x + (b.x - a.x) * t);
            int z = (int) Math.floor(a.z + (b.z - a.z) * t);
            double y = a.y + (b.y - a.y) * t;
            BlockPos col = new BlockPos(x, (int) y, z);
            if (!level.isLoaded(col)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            checks++;
            if (y + 1.0 < surface) {
                under++;
            }
        }
        if (checks == 0) {
            return 1.0f;
        }
        float buried = under / (float) checks;
        return 1.0f - buried * 0.35f;
    }

    private static float cacheAndReturn(long key, float factor, long now) {
        if (LOS_CACHE.size() > CACHE_MAX) {
            LOS_CACHE.clear();
        }
        LOS_CACHE.put(key, new CacheEntry(factor, now));
        return factor;
    }

    private static long cacheKey(BlockPos a, BlockPos b, boolean soft) {
        // Order-independent key
        long x1 = a.asLong();
        long x2 = b.asLong();
        long lo = Math.min(x1, x2);
        long hi = Math.max(x1, x2);
        return lo * 31L + hi + (soft ? 1L : 0L);
    }

    private record CacheEntry(float factor, long timeMs) {
    }
}
