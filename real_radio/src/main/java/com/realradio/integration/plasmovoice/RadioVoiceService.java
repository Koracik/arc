package com.realradio.integration.plasmovoice;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioManager;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import su.plo.slib.api.server.McServerLib;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerStaticSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns {@link ServerStaticSource} instances for active radio receivers.
 * Keys include dimension so same coords in different worlds do not collide.
 */
public final class RadioVoiceService {
    private static final Map<RadioManager.DimPos, ServerStaticSource> SOURCES = new ConcurrentHashMap<>();

    private static PlasmoVoiceServer voiceServer;
    private static ServerSourceLine radioLine;
    private static Object addonRef;

    private RadioVoiceService() {
    }

    public static void bind(PlasmoVoiceServer server, ServerSourceLine line, Object addon) {
        voiceServer = server;
        radioLine = line;
        addonRef = addon;
    }

    public static void unbind() {
        for (ServerStaticSource source : SOURCES.values()) {
            try {
                source.remove();
            } catch (Throwable ignored) {
            }
        }
        SOURCES.clear();
        voiceServer = null;
        radioLine = null;
        addonRef = null;
    }

    public static boolean isReady() {
        return voiceServer != null && radioLine != null;
    }

    public static ServerStaticSource getSource(RadioReceiverBlockEntity receiver) {
        if (receiver.getLevel() == null) {
            return null;
        }
        return SOURCES.get(RadioManager.DimPos.of(receiver.getLevel(), receiver.getBlockPos()));
    }

    public static ServerStaticSource createSource(RadioReceiverBlockEntity receiver) {
        if (!isReady()) {
            return null;
        }
        Level level = receiver.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        RadioManager.DimPos key = RadioManager.DimPos.of(level, receiver.getBlockPos());
        ServerStaticSource existing = SOURCES.get(key);
        if (existing != null) {
            return existing;
        }

        BlockPos pos = receiver.getBlockPos();
        try {
            McServerLib mc = voiceServer.getMinecraftServer();
            McServerWorld world = mc.getWorld(serverLevel);
            ServerPos3d sourcePos = new ServerPos3d(
                    world,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5
            );

            ServerStaticSource source = radioLine.createStaticSource(sourcePos, false);
            source.setIconVisible(false);
            source.setName("Radio");
            SOURCES.put(key, source);
            return source;
        } catch (Throwable t) {
            RealRadio.LOGGER.error("Failed to create Plasmo static source at {}", pos, t);
            return null;
        }
    }

    public static void removeSource(RadioReceiverBlockEntity receiver) {
        if (receiver.getLevel() == null) {
            return;
        }
        ServerStaticSource source = SOURCES.remove(RadioManager.DimPos.of(receiver.getLevel(), receiver.getBlockPos()));
        if (source != null) {
            try {
                source.remove();
            } catch (Throwable t) {
                RealRadio.LOGGER.warn("Failed to remove Plasmo static source", t);
            }
        }
    }

    /**
     * Static source near a player for handheld RX.
     */
    public static ServerStaticSource createPlayerSource(ServerPlayer player) {
        if (!isReady() || player == null) {
            return null;
        }
        ServerLevel serverLevel = player.serverLevel();
        try {
            McServerLib mc = voiceServer.getMinecraftServer();
            McServerWorld world = mc.getWorld(serverLevel);
            ServerPos3d sourcePos = new ServerPos3d(
                    world,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ()
            );
            ServerStaticSource source = radioLine.createStaticSource(sourcePos, false);
            source.setIconVisible(false);
            source.setName("Handheld");
            return source;
        } catch (Throwable t) {
            RealRadio.LOGGER.error("Failed to create handheld Plasmo source for {}", player.getGameProfile().getName(), t);
            return null;
        }
    }
}
