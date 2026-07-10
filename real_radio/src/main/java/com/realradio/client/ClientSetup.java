package com.realradio.client;

import com.realradio.client.coverage.CoverageOverlay;
import com.realradio.client.gui.HandheldRadioScreen;
import com.realradio.client.gui.RadioReceiverScreen;
import com.realradio.client.gui.RadioRelayScreen;
import com.realradio.client.gui.RadioTransmitterScreen;
import com.realradio.client.sound.ReceiverStaticSoundHandler;
import com.realradio.common.registry.ModMenus;
import com.realradio.network.HandheldPttPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static KeyMapping KEY_COVERAGE;
    public static KeyMapping KEY_PTT;
    private static boolean pttWasDown;

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerScreens);
        modBus.addListener(ClientSetup::registerKeys);
        NeoForge.EVENT_BUS.addListener(ReceiverStaticSoundHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReceiverStaticSoundHandler::onLogout);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(CoverageOverlay::onRenderLevel);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RADIO_TRANSMITTER.get(), RadioTransmitterScreen::new);
        event.register(ModMenus.RADIO_RECEIVER.get(), RadioReceiverScreen::new);
        event.register(ModMenus.RADIO_RELAY.get(), RadioRelayScreen::new);
        event.register(ModMenus.HANDHELD_RADIO.get(), HandheldRadioScreen::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        KEY_COVERAGE = new KeyMapping(
                "key.real_radio.toggle_coverage",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.categories.real_radio"
        );
        KEY_PTT = new KeyMapping(
                "key.real_radio.ptt",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.categories.real_radio"
        );
        event.register(KEY_COVERAGE);
        event.register(KEY_PTT);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        CoverageOverlay.onClientTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (KEY_COVERAGE != null && KEY_COVERAGE.consumeClick()) {
            CoverageOverlay.toggle();
        }
        boolean pttDown = KEY_PTT != null && KEY_PTT.isDown();
        if (pttDown != pttWasDown) {
            pttWasDown = pttDown;
            PacketDistributor.sendToServer(new HandheldPttPayload(pttDown));
        }
    }
}
