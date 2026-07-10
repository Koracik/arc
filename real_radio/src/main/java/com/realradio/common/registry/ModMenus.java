package com.realradio.common.registry;

import com.realradio.RealRadio;
import com.realradio.common.menu.HandheldRadioMenu;
import com.realradio.common.menu.RadioReceiverMenu;
import com.realradio.common.menu.RadioRelayMenu;
import com.realradio.common.menu.RadioTransmitterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, RealRadio.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RadioTransmitterMenu>> RADIO_TRANSMITTER =
            REGISTER.register("radio_transmitter", () ->
                    IMenuTypeExtension.create(RadioTransmitterMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<RadioReceiverMenu>> RADIO_RECEIVER =
            REGISTER.register("radio_receiver", () ->
                    IMenuTypeExtension.create(RadioReceiverMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<RadioRelayMenu>> RADIO_RELAY =
            REGISTER.register("radio_relay", () ->
                    IMenuTypeExtension.create(RadioRelayMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<HandheldRadioMenu>> HANDHELD_RADIO =
            REGISTER.register("handheld_radio", () ->
                    IMenuTypeExtension.create(HandheldRadioMenu::fromNetwork));

    private ModMenus() {
    }
}
