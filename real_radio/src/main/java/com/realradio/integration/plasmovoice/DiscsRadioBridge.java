package com.realradio.integration.plasmovoice;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioManager;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.common.util.RadioBroadcast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.audio.line.BaseServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerAudioSource;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.audio.source.ServerStaticSource;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;

/**
 * Soft compatibility with <b>pv-addon-discs</b> (NeoForge port).
 * <p>
 * Custom disc / goat-horn audio is played by Plasmo Voice on the {@code discs}
 * source line. When that audio originates inside an active transmitter's
 * capture radius, frames are relayed to matching radio receivers — same path
 * as player voice ({@link RealRadioServerAddon}).
 * <p>
 * No hard dependency: if the discs addon is absent, this simply never sees
 * a {@code discs} line source.
 */
public final class DiscsRadioBridge {
    /** Source-line name registered by pv-addon-discs PlasmoPlaybackService. */
    public static final String DISCS_LINE_NAME = "discs";
    /** Plasmo addon id of pv-addon-discs (Paper + NeoForge port). */
    public static final String DISCS_ADDON_ID = "pv-addon-discs";

    private static final short RECEIVER_HEAR_DISTANCE = 16;

    public DiscsRadioBridge() {
    }

    @EventSubscribe
    public void onSourceAudioPacket(ServerSourceAudioPacketEvent event) {
        if (!RadioVoiceService.isReady()) {
            return;
        }

        ServerAudioSource<?> source = event.getSource();
        if (!isDiscsSource(source)) {
            return;
        }

        SourceAudioPacket packet = event.getPacket();
        byte[] data = packet.getData();
        if (data == null || data.length == 0) {
            return;
        }

        CapturePoint capture = resolveCapturePoint(source);
        if (capture == null) {
            return;
        }

        long sequence = packet.getSequenceNumber();
        boolean stereo = isStereo(source);

        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            if (!tx.isActive() || tx.getLevel() != capture.level()) {
                continue;
            }
            if (!tx.isPlayerInCaptureZone(capture.pos())) {
                continue;
            }

            tx.markSpeaking();
            RadioBroadcast.fromTransmitter(tx, data, sequence, RECEIVER_HEAR_DISTANCE, stereo);
        }
    }

    private static boolean isDiscsSource(ServerAudioSource<?> source) {
        try {
            BaseServerSourceLine line = source.getLine();
            if (line != null && DISCS_LINE_NAME.equals(line.getName())) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (source.getAddon() != null && DISCS_ADDON_ID.equals(source.getAddon().getId())) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            SourceInfo info = source.getSourceInfo();
            if (info != null && DISCS_ADDON_ID.equals(info.getAddonId())) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static CapturePoint resolveCapturePoint(ServerAudioSource<?> source) {
        try {
            if (source instanceof ServerStaticSource staticSource) {
                ServerPos3d pos = staticSource.getPosition();
                if (pos == null || pos.getWorld() == null) {
                    return null;
                }
                McServerWorld world = pos.getWorld();
                Object instance = world.getInstance();
                if (!(instance instanceof ServerLevel level)) {
                    return null;
                }
                return new CapturePoint(level, new Vec3(pos.getX(), pos.getY(), pos.getZ()));
            }
        } catch (Throwable t) {
            RealRadio.LOGGER.debug("Failed to resolve discs static source position", t);
        }

        // Goat horn / other proximity sources attached to a player
        try {
            if (source instanceof ServerPlayerSource playerSource) {
                VoiceServerPlayer voicePlayer = playerSource.getPlayer();
                if (voicePlayer == null) {
                    return null;
                }
                Object mcPlayer = voicePlayer.getInstance().getInstance();
                if (!(mcPlayer instanceof net.minecraft.server.level.ServerPlayer player)) {
                    return null;
                }
                return new CapturePoint(player.serverLevel(), player.position());
            }
        } catch (Throwable t) {
            RealRadio.LOGGER.debug("Failed to resolve discs player source position", t);
        }

        return null;
    }

    private static boolean isStereo(ServerAudioSource<?> source) {
        try {
            SourceInfo info = source.getSourceInfo();
            if (info != null) {
                return info.isStereo();
            }
        } catch (Throwable ignored) {
        }
        // Discs defaults to stereo static sources unless mono_sources is enabled
        return true;
    }

    private record CapturePoint(ServerLevel level, Vec3 pos) {
    }
}
