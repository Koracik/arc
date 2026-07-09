package com.realradio.client;

import com.realradio.client.gui.RadioReceiverScreen;
import com.realradio.client.gui.RadioTransmitterScreen;
import com.realradio.client.sound.ReceiverStaticSoundHandler;
import com.realradio.common.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerScreens);
        NeoForge.EVENT_BUS.addListener(ReceiverStaticSoundHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReceiverStaticSoundHandler::onLogout);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RADIO_TRANSMITTER.get(), RadioTransmitterScreen::new);
        event.register(ModMenus.RADIO_RECEIVER.get(), RadioReceiverScreen::new);
    }
}
