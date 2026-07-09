package com.realradio.client.sound;

import com.realradio.common.registry.ModSounds;
import com.realradio.network.ReceiverQualityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays looping white-noise around active receivers.
 * Uses {@link AbstractTickableSoundInstance} so volume updates apply every tick
 * (SimpleSoundInstance only samples volume when started — which broke the volume slider).
 */
public final class ReceiverStaticSoundHandler {
    private static final Map<BlockPos, ReceiverState> STATES = new ConcurrentHashMap<>();
    private static final double MAX_HEAR_DISTANCE = 32.0;

    private ReceiverStaticSoundHandler() {
    }

    public static void update(ReceiverQualityPayload payload) {
        if (!payload.active()) {
            stop(payload.pos());
            STATES.remove(payload.pos());
            return;
        }
        ReceiverState state = STATES.computeIfAbsent(payload.pos().immutable(), ReceiverState::new);
        state.quality = payload.quality();
        state.voiceVolume = payload.voiceVolume();
        state.staticVolume = payload.staticVolume();
        state.active = true;
        state.lastUpdateMs = System.currentTimeMillis();
    }

    public static float getVoiceVolume(BlockPos pos) {
        ReceiverState state = STATES.get(pos);
        if (state == null || !state.active) {
            // Try block center snap for static source positions
            for (Map.Entry<BlockPos, ReceiverState> e : STATES.entrySet()) {
                if (e.getKey().distManhattan(pos) <= 1 && e.getValue().active) {
                    return e.getValue().voiceVolume;
                }
            }
            return 0.0f;
        }
        return state.voiceVolume;
    }

    /**
     * Fallback gain used when we cannot map an AlSource back to a block pos:
     * loudest currently tracked radio near the local player.
     */
    public static float getNearestVoiceVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1.0f;
        }
        Vec3 playerPos = mc.player.position();
        float best = 0.0f;
        for (Map.Entry<BlockPos, ReceiverState> e : STATES.entrySet()) {
            if (!e.getValue().active) {
                continue;
            }
            double dist = Math.sqrt(e.getKey().distToCenterSqr(playerPos.x, playerPos.y, playerPos.z));
            if (dist <= MAX_HEAR_DISTANCE) {
                best = Math.max(best, e.getValue().voiceVolume);
            }
        }
        return best;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearAll();
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, ReceiverState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ReceiverState> entry = it.next();
            ReceiverState state = entry.getValue();
            BlockPos pos = entry.getKey();

            // Drop stale entries (receiver unloaded / out of range of sync)
            if (now - state.lastUpdateMs > 2000L) {
                stop(pos);
                it.remove();
                continue;
            }

            double distSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > MAX_HEAR_DISTANCE * MAX_HEAR_DISTANCE || state.staticVolume <= 0.001f) {
                if (state.sound != null) {
                    mc.getSoundManager().stop(state.sound);
                    state.sound = null;
                }
                continue;
            }

            float volume = state.staticVolume;
            if (state.sound == null || !mc.getSoundManager().isActive(state.sound)) {
                state.sound = new LoopingStaticSound(pos, volume);
                mc.getSoundManager().play(state.sound);
            } else if (state.sound instanceof LoopingStaticSound looping) {
                // Tickable sound re-reads volume each engine tick → live volume slider
                looping.setDynamicVolume(volume);
            }
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    private static void stop(BlockPos pos) {
        ReceiverState state = STATES.get(pos);
        if (state != null && state.sound != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(state.sound);
            }
            state.sound = null;
        }
    }

    private static void clearAll() {
        for (BlockPos pos : STATES.keySet()) {
            stop(pos);
        }
        STATES.clear();
        StationSpectrumCache.clear();
    }

    private static final class ReceiverState {
        final BlockPos pos;
        float quality;
        float voiceVolume;
        float staticVolume;
        boolean active;
        long lastUpdateMs;
        SoundInstance sound;

        ReceiverState(BlockPos pos) {
            this.pos = pos;
            this.lastUpdateMs = System.currentTimeMillis();
        }
    }

    /**
     * Positioned looping static noise that updates volume every sound-engine tick.
     * Critical for the receiver volume slider — non-tickable instances ignore mid-play changes.
     */
    private static final class LoopingStaticSound extends AbstractTickableSoundInstance {
        LoopingStaticSound(BlockPos pos, float volume) {
            super(ModSounds.RADIO_STATIC.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = Math.max(0.0f, volume);
            this.pitch = 1.0f;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.attenuation = Attenuation.LINEAR;
            this.relative = false;
        }

        void setDynamicVolume(float volume) {
            this.volume = Math.max(0.0f, volume);
        }

        @Override
        public void tick() {
            if (this.volume <= 0.001f) {
                this.stop();
            }
        }
    }
}
