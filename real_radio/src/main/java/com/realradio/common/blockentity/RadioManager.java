package com.realradio.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of active radio block entities, keyed by dimension + position.
 */
public final class RadioManager {
    private static final Map<DimPos, RadioTransmitterBlockEntity> TRANSMITTERS = new ConcurrentHashMap<>();
    private static final Map<DimPos, RadioReceiverBlockEntity> RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<DimPos, RadioRelayBlockEntity> RELAYS = new ConcurrentHashMap<>();

    private RadioManager() {
    }

    public static void registerTransmitter(RadioTransmitterBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        TRANSMITTERS.put(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static void unregisterTransmitter(RadioTransmitterBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        TRANSMITTERS.remove(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static void registerReceiver(RadioReceiverBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        RECEIVERS.put(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static void unregisterReceiver(RadioReceiverBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        RECEIVERS.remove(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static void registerRelay(RadioRelayBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        RELAYS.put(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static void unregisterRelay(RadioRelayBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        RELAYS.remove(DimPos.of(be.getLevel(), be.getBlockPos()), be);
    }

    public static Collection<RadioTransmitterBlockEntity> transmitters() {
        return Collections.unmodifiableCollection(TRANSMITTERS.values());
    }

    public static Collection<RadioReceiverBlockEntity> receivers() {
        return Collections.unmodifiableCollection(RECEIVERS.values());
    }

    public static Collection<RadioRelayBlockEntity> relays() {
        return Collections.unmodifiableCollection(RELAYS.values());
    }

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) {
        public static DimPos of(Level level, BlockPos pos) {
            return new DimPos(level.dimension(), pos.immutable());
        }
    }
}
