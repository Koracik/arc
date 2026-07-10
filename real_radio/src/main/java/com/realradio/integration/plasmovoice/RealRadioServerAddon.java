package com.realradio.integration.plasmovoice;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioManager;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import com.realradio.common.util.RadioBand;
import com.realradio.common.util.SignalQuality;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.BaseServerSourceLine;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerStaticSource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.io.InputStream;

/**
 * Plasmo Voice server addon.
 * <p>
 * Intercepts player voice ({@link PlayerSpeakEvent}), finds active transmitters
 * within capture radius, and relays Opus frames to matching receivers'
 * {@link ServerStaticSource} instances with per-receiver quality.
 */
@Addon(
        id = "real_radio",
        name = "Real Radio",
        scope = AddonLoaderScope.SERVER,
        version = "1.0.0",
        authors = {"Real Radio"}
)
public final class RealRadioServerAddon implements AddonInitializer {
    /**
     * Valid 16×16 radio icon (base64 PNG) for the Plasmo Voice source-line UI.
     * A broken icon makes PV crash when opening settings (blit textureLocation = null).
     */
    private static final String RADIO_ICON_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAfUlEQVR4nGNkwAPubNH4D2Or+Nxg" +
            "xKaGiRjN2PgEDSAW4DQA3cm4vAAX1JDkwurEBetPMCQEWmCI33j+jRFugIYk138jZRGinAwD5+6+" +
            "Ybjx/BsjihduLDAiyRAGBrQw0Eg4xxAVBTGEEI3VAHLAqAFUSAcEUyIuAEuJFAMAvPosmE4YhDsA" +
            "AAAASUVORK5CYII=";

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private ServerSourceLine radioLine;

    private final DiscsRadioBridge discsBridge = new DiscsRadioBridge();

    @Override
    public void onAddonInitialize() {
        radioLine = createRadioLine();

        RadioVoiceService.bind(voiceServer, radioLine, this);
        voiceServer.getEventBus().register(this, this);
        // Soft-compat: relay pv-addon-discs jukebox/horn audio through radio TX
        voiceServer.getEventBus().register(this, discsBridge);
        RealRadio.LOGGER.info("Plasmo Voice radio_line registered (discs bridge enabled)");
    }

    private ServerSourceLine createRadioLine() {
        // Prefer loading a real PNG resource — InputStream overload of createBuilder
        try (InputStream iconStream = RealRadioServerAddon.class.getResourceAsStream(
                "/assets/real_radio/textures/gui/radio_icon.png")) {
            BaseServerSourceLine.Builder<ServerSourceLine> builder;
            if (iconStream != null) {
                builder = voiceServer.getSourceLineManager().createBuilder(
                        this,
                        "radio_line",
                        "real_radio.line.radio",
                        iconStream,
                        15
                );
            } else {
                RealRadio.LOGGER.warn("radio_icon.png missing from jar, falling back to embedded base64");
                builder = voiceServer.getSourceLineManager().createBuilder(
                        this,
                        "radio_line",
                        "real_radio.line.radio",
                        RADIO_ICON_BASE64,
                        15
                );
            }
            return builder.setDefaultVolume(1.0).build();
        } catch (Exception e) {
            RealRadio.LOGGER.error("Failed to create radio_line with resource icon, using base64 fallback", e);
            return voiceServer.getSourceLineManager()
                    .createBuilder(this, "radio_line", "real_radio.line.radio", RADIO_ICON_BASE64, 15)
                    .setDefaultVolume(1.0)
                    .build();
        }
    }

    @Override
    public void onAddonShutdown() {
        // Unregisters all listeners bound with owner = this (voice + discs bridge)
        voiceServer.getEventBus().unregister(this);
        RadioVoiceService.unbind();
        if (radioLine != null) {
            voiceServer.getSourceLineManager().unregister(radioLine.getName());
            radioLine = null;
        }
    }

    @EventSubscribe
    public void onPlayerSpeak(PlayerSpeakEvent event) {
        VoicePlayer voicePlayer = event.getPlayer();
        if (!(voicePlayer instanceof VoiceServerPlayer serverPlayer)) {
            return;
        }
        if (!voicePlayer.hasVoiceChat()) {
            return;
        }

        Object mcPlayer = serverPlayer.getInstance().getInstance();
        if (!(mcPlayer instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }

        PlayerAudioPacket packet = event.getPacket();
        byte[] data = packet.getData();
        long sequence = packet.getSequenceNumber();
        short distance = (short) 16; // hear radius around each receiver static source

        for (RadioTransmitterBlockEntity tx : RadioManager.transmitters()) {
            if (!tx.isActive() || tx.getLevel() != player.level()) {
                continue;
            }
            if (!tx.isPlayerInCaptureZone(player.position())) {
                continue;
            }

            tx.markSpeaking();
            relayFromTransmitter(tx, data, sequence, distance);
        }
    }

    @EventSubscribe
    public void onPlayerSpeakEnd(PlayerSpeakEndEvent event) {
        // Static sources auto-end when frames stop; nothing mandatory here.
    }

    private void relayFromTransmitter(RadioTransmitterBlockEntity tx, byte[] data, long sequence, short distance) {
        for (RadioReceiverBlockEntity rx : RadioManager.receivers()) {
            if (!rx.isActive() || rx.getLevel() != tx.getLevel() || rx.isAM() != tx.isAM()) {
                continue;
            }

            // Only the dominant station is relayed (FM capture / avoid AM garble).
            // Combined quality (interference) is applied client-side via voice gain.
            if (!rx.isDominantTransmitter(tx)) {
                continue;
            }
            float quality = rx.rawQualityFrom(tx);
            if (quality <= 0.0f || SignalQuality.isSquelched(rx.getSignalQuality())) {
                continue;
            }

            ServerStaticSource source = RadioVoiceService.getSource(rx);
            if (source == null) {
                source = RadioVoiceService.createSource(rx);
            }
            if (source == null) {
                continue;
            }

            // Player voice is mono Opus; restore mono when speaking so discs stereo
            // state does not stick after music stops.
            try {
                source.setStereo(false);
            } catch (Throwable ignored) {
            }

            // Voice loudness is applied on the client via quality sync + AlSource gain.
            source.sendAudioFrame(data, sequence, distance);
        }
    }

    public PlasmoVoiceServer getVoiceServer() {
        return voiceServer;
    }

    public ServerSourceLine getRadioLine() {
        return radioLine;
    }
}
