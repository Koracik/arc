package com.realradio.common.util;

import com.realradio.config.RealRadioConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Environmental propagation: line-of-sight, antenna height, weather.
 * LOS results are grid-quantized and TTL-cached for multi-chunk distances.
 */
public final class RadioPropagation {
    private static final Map<Long, CacheEntry> LOS_CACHE = new ConcurrentHashMap<>();

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
        long ttl = RealRadioConfig.losCacheTtlMs();
        CacheEntry cached = LOS_CACHE.get(key);
        if (cached != null && now - cached.timeMs < ttl) {
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
        int max = RealRadioConfig.losCacheMax();
        if (LOS_CACHE.size() >= max) {
            evictExpiredOrHalf(now);
        }
        LOS_CACHE.put(key, new CacheEntry(factor, now));
        return factor;
    }

    /** Drop expired entries; if still over capacity, drop oldest half. */
    private static void evictExpiredOrHalf(long now) {
        long ttl = RealRadioConfig.losCacheTtlMs();
        Iterator<Map.Entry<Long, CacheEntry>> it = LOS_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CacheEntry> e = it.next();
            if (now - e.getValue().timeMs >= ttl) {
                it.remove();
            }
        }
        int max = RealRadioConfig.losCacheMax();
        if (LOS_CACHE.size() < max) {
            return;
        }
        // Half-evict oldest
        long[] times = new long[LOS_CACHE.size()];
        int i = 0;
        for (CacheEntry e : LOS_CACHE.values()) {
            times[i++] = e.timeMs;
        }
        java.util.Arrays.sort(times);
        long cutoff = times[times.length / 2];
        LOS_CACHE.entrySet().removeIf(e -> e.getValue().timeMs <= cutoff);
    }

    /**
     * Order-independent key with optional grid quantization so multi-chunk
     * links share cache entries across tiny endpoint jitter.
     */
    private static long cacheKey(BlockPos a, BlockPos b, boolean soft) {
        int grid = Math.max(1, RealRadioConfig.losCacheGrid());
        long x1 = quantize(a, grid);
        long x2 = quantize(b, grid);
        long lo = Math.min(x1, x2);
        long hi = Math.max(x1, x2);
        // Mix soft bit without colliding common pos packs
        return lo * 31L + hi * 17L + (soft ? 1L : 0L) + grid * 3L;
    }

    private static long quantize(BlockPos p, int grid) {
        int x = Math.floorDiv(p.getX(), grid) * grid;
        int y = Math.floorDiv(p.getY(), grid) * grid;
        int z = Math.floorDiv(p.getZ(), grid) * grid;
        return BlockPos.asLong(x, y, z);
    }

    private record CacheEntry(float factor, long timeMs) {
    }
}
