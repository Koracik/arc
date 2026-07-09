package com.realradio.integration.plasmovoice;

import com.realradio.RealRadio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * Loads Plasmo Voice server & client addons once Plasmo Voice is present.
 * Client addon class is only referenced on the physical client to avoid
 * loading client-only classes on a dedicated server.
 */
public final class PlasmoVoiceBootstrap {
    private static boolean loaded;

    private PlasmoVoiceBootstrap() {
    }

    public static void bootstrap() {
        if (loaded) {
            return;
        }

        loadServerAddon();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            loadClientAddon();
        }

        loaded = true;
    }

    private static void loadServerAddon() {
        try {
            Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
        } catch (ClassNotFoundException e) {
            RealRadio.LOGGER.warn("Plasmo Voice API not found — radio voice bridge disabled");
            return;
        }

        try {
            PlasmoVoiceServer.getAddonsLoader().load(new RealRadioServerAddon());
            RealRadio.LOGGER.info("Registered Plasmo Voice server addon: real_radio");
        } catch (Throwable t) {
            RealRadio.LOGGER.error("Failed to load Plasmo Voice server addon", t);
        }
    }

    private static void loadClientAddon() {
        try {
            Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");
            Class<?> addonClass = Class.forName("com.realradio.integration.plasmovoice.RealRadioClientAddon");
            Object addon = addonClass.getDeclaredConstructor().newInstance();
            Class<?> clientApi = Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");
            Object loader = clientApi.getMethod("getAddonsLoader").invoke(null);
            loader.getClass().getMethod("load", Object.class).invoke(loader, addon);
            RealRadio.LOGGER.info("Registered Plasmo Voice client addon: real_radio");
        } catch (ClassNotFoundException e) {
            RealRadio.LOGGER.debug("Plasmo Voice client API not present");
        } catch (Throwable t) {
            RealRadio.LOGGER.error("Failed to load Plasmo Voice client addon", t);
        }
    }
}
