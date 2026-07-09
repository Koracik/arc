package com.realradio.integration.plasmovoice;

import com.realradio.RealRadio;
import com.realradio.client.sound.ReceiverStaticSoundHandler;
import net.minecraft.core.BlockPos;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.source.AlSource;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.api.client.event.audio.device.source.AlSourceWriteEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceInitializedEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.data.audio.source.StaticSourceInfo;

/**
 * Client Plasmo Voice addon.
 * <p>
 * Applies per-receiver voice gain ({@code finalQuality * receiverVolume}) by
 * scaling OpenAL source volume for audio belonging to the {@code radio_line}.
 * <p>
 * Server API has no {@code setVolume} on {@code ServerStaticSource}; this is the
 * supported way to implement quality-based loudness without decoding Opus on the server.
 */
@Addon(
        id = "real_radio",
        name = "Real Radio",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"Real Radio"}
)
public final class RealRadioClientAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    @Override
    public void onAddonInitialize() {
        voiceClient.getEventBus().register(this, this);
        RealRadio.LOGGER.info("Plasmo Voice client radio addon initialized");
    }

    @Override
    public void onAddonShutdown() {
        voiceClient.getEventBus().unregister(this);
    }

    @EventSubscribe
    public void onSourceInitialized(AudioSourceInitializedEvent event) {
        applyGain(event.getSource());
    }

    @EventSubscribe
    public void onAlSourceWrite(AlSourceWriteEvent event) {
        // Re-apply gain each buffer write so quality / volume-slider updates are heard live
        AlSource al = event.getSource();
        float gain = currentGainForAlSource(al);
        // Always apply (including 0) so the volume slider never "sticks" until zero
        if (gain >= 0.0f) {
            try {
                al.setVolume(Math.max(0.0f, Math.min(1.0f, gain)));
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyGain(ClientAudioSource<?> source) {
        float gain = resolveGain(source);
        if (gain < 0.0f) {
            return;
        }
        try {
            AlSource al = source.getSource();
            if (al != null) {
                al.setVolume(gain);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * @return gain in [0, 1], or -1 if this source is not a radio static source
     */
    private float resolveGain(ClientAudioSource<?> source) {
        SourceInfo info = source.getSourceInfo();
        if (!(info instanceof StaticSourceInfo staticInfo)) {
            return -1.0f;
        }
        // Only touch our radio line if the line name is available
        try {
            if (source.getSourceLine() != null
                    && !"radio_line".equals(source.getSourceLine().getName())) {
                return -1.0f;
            }
        } catch (Throwable ignored) {
        }

        double x = staticInfo.getPosition().getX();
        double y = staticInfo.getPosition().getY();
        double z = staticInfo.getPosition().getZ();
        BlockPos pos = BlockPos.containing(x, y, z);
        return ReceiverStaticSoundHandler.getVoiceVolume(pos);
    }

    private float currentGainForAlSource(AlSource al) {
        // AlSource has no reverse map to ClientAudioSource; use nearest active receiver gain.
        // Quality packets already update a map keyed by block pos; pick strongest nearby.
        return ReceiverStaticSoundHandler.getNearestVoiceVolume();
    }
}
