package com.realradio.common.util;

import com.realradio.common.item.HandheldRadioItem;
import com.realradio.config.RealRadioConfig;
import com.realradio.integration.plasmovoice.RadioVoiceService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import su.plo.voice.api.server.audio.source.ServerStaticSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual handheld radios: RX delivery to player-attached static sources,
 * TX via PTT state.
 */
public final class HandheldRadioService {
    private static final Map<UUID, Boolean> PTT = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerStaticSource> RX_SOURCES = new ConcurrentHashMap<>();
    private static final short HEAR_DISTANCE = 12;

    private HandheldRadioService() {
    }

    public static void setPtt(UUID playerId, boolean down) {
        if (down) {
            PTT.put(playerId, true);
        } else {
            PTT.remove(playerId);
        }
    }

    public static boolean isPtt(UUID playerId) {
        return Boolean.TRUE.equals(PTT.get(playerId));
    }

    public static void clearPlayer(UUID playerId) {
        PTT.remove(playerId);
        ServerStaticSource src = RX_SOURCES.remove(playerId);
        if (src != null) {
            try {
                src.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * If the player holds an active handheld and is PTT, treat voice as TX from player position.
     */
    public static boolean tryTransmitFromPlayer(ServerPlayer player, byte[] data, long sequence) {
        if (player == null || !isPtt(player.getUUID())) {
            return false;
        }
        ItemStack stack = findHeldRadio(player);
        if (stack.isEmpty() || !HandheldRadioItem.isActive(stack)) {
            return false;
        }
        float freq = HandheldRadioItem.getFrequency(stack);
        boolean am = HandheldRadioItem.isAM(stack);
        int key = HandheldRadioItem.getChannelKey(stack);
        RadioBand band = RadioBand.fromAm(am);
        int baseRange = band.rangeBlocks(freq);
        float mult = RadioPropagation.antennaRangeMultiplier((int) player.getY());
        if (am && player.level().isNight()) {
            mult *= RealRadioConfig.amNightMultiplier();
        }
        mult *= RealRadioConfig.handheldRangeFactor();
        int range = Math.max(1, Math.round(baseRange * mult));
        BlockPos pos = player.blockPosition();
        RadioBroadcast.broadcast(
                player.level(),
                pos,
                freq,
                am,
                key,
                range,
                data,
                sequence,
                HEAR_DISTANCE,
                false,
                0,
                new java.util.HashSet<>()
        );
        return true;
    }

    public static void deliverToHandhelds(
            Level level,
            BlockPos sourcePos,
            float frequency,
            boolean isAM,
            int channelKey,
            int rangeBlocks,
            byte[] data,
            long sequence,
            short hearDistance,
            boolean stereo
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        RadioBand band = RadioBand.fromAm(isAM);
        for (ServerPlayer player : serverLevel.players()) {
            ItemStack stack = findHeldRadio(player);
            if (stack.isEmpty() || !HandheldRadioItem.isActive(stack)) {
                continue;
            }
            if (HandheldRadioItem.isAM(stack) != isAM) {
                continue;
            }
            if (!ChannelKeys.matches(channelKey, HandheldRadioItem.getChannelKey(stack))) {
                continue;
            }
            float tuning = SignalQuality.tuningFactor(frequency, HandheldRadioItem.getFrequency(stack), band);
            if (tuning <= 0.0f) {
                continue;
            }
            double dist = Math.sqrt(sourcePos.distSqr(player.blockPosition()));
            float distance = SignalQuality.distanceFactor(dist, rangeBlocks, band);
            if (distance <= 0.0f) {
                continue;
            }
            float los = RadioPropagation.lineOfSightFactor(level, sourcePos, player.blockPosition(), isAM);
            float weather = RadioPropagation.weatherFactor(level, isAM);
            float quality = SignalQuality.finalQuality(distance * los * weather, tuning);
            if (quality <= 0.0f || SignalQuality.isSquelched(quality)) {
                continue;
            }
            ServerStaticSource source = ensureRxSource(player);
            if (source == null) {
                continue;
            }
            try {
                // Keep source near the player
                var mc = source.getPosition();
                // Position update may not be available on all PV versions — best effort
            } catch (Throwable ignored) {
            }
            try {
                source.setStereo(stereo);
            } catch (Throwable ignored) {
            }
            source.sendAudioFrame(data, sequence, hearDistance);
        }
    }

    private static ServerStaticSource ensureRxSource(ServerPlayer player) {
        ServerStaticSource existing = RX_SOURCES.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        if (!RadioVoiceService.isReady()) {
            return null;
        }
        try {
            BlockPos pos = player.blockPosition();
            // Reuse RadioVoiceService helper via temporary receiver-like create at player feet
            ServerStaticSource source = RadioVoiceService.createPlayerSource(player);
            if (source != null) {
                RX_SOURCES.put(player.getUUID(), source);
            }
            return source;
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack findHeldRadio(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof HandheldRadioItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof HandheldRadioItem) {
            return off;
        }
        // Also allow anywhere in inventory for listening convenience
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof HandheldRadioItem && HandheldRadioItem.isActive(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void tickSources(ServerLevel level) {
        // Remove sources for players who left or powered off
        for (var it = RX_SOURCES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || player.level() != level) {
                try {
                    e.getValue().remove();
                } catch (Throwable ignored) {
                }
                it.remove();
                continue;
            }
            ItemStack stack = findHeldRadio(player);
            if (stack.isEmpty() || !HandheldRadioItem.isActive(stack)) {
                try {
                    e.getValue().remove();
                } catch (Throwable ignored) {
                }
                it.remove();
            }
        }
    }
}
