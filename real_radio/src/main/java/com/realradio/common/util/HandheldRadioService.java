package com.realradio.common.util;

import com.realradio.common.item.HandheldRadioItem;
import com.realradio.config.RealRadioConfig;
import com.realradio.integration.plasmovoice.RadioVoiceService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import su.plo.voice.api.server.audio.source.ServerStaticSource;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Virtual handheld radios: RX delivery to player-attached static sources, TX via PTT.
 */
public final class HandheldRadioService {
    private static final Map<UUID, Boolean> PTT = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerStaticSource> RX_SOURCES = new ConcurrentHashMap<>();
    private static final Map<UUID, InventoryCache> INV_CACHE = new ConcurrentHashMap<>();
    private static final short HEAR_DISTANCE = 12;
    private static final long INV_CACHE_TTL_MS = 250L;

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
        INV_CACHE.remove(playerId);
        ServerStaticSource src = RX_SOURCES.remove(playerId);
        if (src != null) {
            try {
                src.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean tryTransmitFromPlayer(ServerPlayer player, byte[] data, long sequence) {
        if (player == null || !isPtt(player.getUUID())) {
            return false;
        }
        // PTT only from hands — never scan full inventory on the voice hot path
        ItemStack stack = findHandRadio(player);
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
                new HashSet<>(2)
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
        if (serverLevel.players().isEmpty()) {
            return;
        }
        RadioBand band = RadioBand.fromAm(isAM);
        double rangeSq = (double) rangeBlocks * (double) rangeBlocks;
        for (ServerPlayer player : serverLevel.players()) {
            if (sourcePos.distSqr(player.blockPosition()) >= rangeSq) {
                continue;
            }
            ItemStack stack = findActiveRadioCached(player);
            if (stack.isEmpty()) {
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
            // Skip LOS for handheld RX on hot path — distance + weather only (cheaper, still usable)
            float weather = RadioPropagation.weatherFactor(level, isAM);
            float quality = SignalQuality.finalQuality(distance * weather, tuning);
            if (quality <= 0.0f || SignalQuality.isSquelched(quality)) {
                continue;
            }
            ServerStaticSource source = ensureRxSource(player);
            if (source == null) {
                continue;
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
        ServerStaticSource source = RadioVoiceService.createPlayerSource(player);
        if (source != null) {
            RX_SOURCES.put(player.getUUID(), source);
        }
        return source;
    }

    private static ItemStack findHandRadio(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof HandheldRadioItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof HandheldRadioItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Active handheld for listening: hands first, then inventory (cached ~250ms).
     */
    private static ItemStack findActiveRadioCached(ServerPlayer player) {
        ItemStack hand = findHandRadio(player);
        if (!hand.isEmpty() && HandheldRadioItem.isActive(hand)) {
            return hand;
        }
        long now = System.currentTimeMillis();
        InventoryCache cache = INV_CACHE.get(player.getUUID());
        if (cache != null && now - cache.timeMs < INV_CACHE_TTL_MS) {
            return cache.stack;
        }
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof HandheldRadioItem && HandheldRadioItem.isActive(s)) {
                found = s;
                break;
            }
        }
        INV_CACHE.put(player.getUUID(), new InventoryCache(found, now));
        return found;
    }

    public static ItemStack findHeldRadio(ServerPlayer player) {
        ItemStack hand = findHandRadio(player);
        if (!hand.isEmpty()) {
            return hand;
        }
        return findActiveRadioCached(player);
    }

    public static void tickSources(ServerLevel level) {
        for (var it = RX_SOURCES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || player.level() != level) {
                try {
                    e.getValue().remove();
                } catch (Throwable ignored) {
                }
                it.remove();
                INV_CACHE.remove(e.getKey());
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

    private record InventoryCache(ItemStack stack, long timeMs) {
    }
}
