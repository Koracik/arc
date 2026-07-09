package com.realradio.client.sound;

import com.realradio.network.NearbyStationsPayload;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client cache of spectrum markers keyed by receiver block position.
 */
public final class StationSpectrumCache {
    private static final Map<BlockPos, List<NearbyStationsPayload.StationMarker>> MAP = new ConcurrentHashMap<>();

    private StationSpectrumCache() {
    }

    public static void update(BlockPos pos, List<NearbyStationsPayload.StationMarker> stations) {
        if (stations == null || stations.isEmpty()) {
            MAP.remove(pos.immutable());
            return;
        }
        MAP.put(pos.immutable(), List.copyOf(stations));
    }

    public static List<NearbyStationsPayload.StationMarker> get(BlockPos pos) {
        List<NearbyStationsPayload.StationMarker> list = MAP.get(pos);
        return list != null ? list : Collections.emptyList();
    }

    public static void clear() {
        MAP.clear();
    }
}
