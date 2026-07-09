package com.realradio.common.registry;

import com.realradio.RealRadio;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealRadio.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.real_radio"))
                    .icon(() -> new ItemStack(ModItems.RADIO_RECEIVER.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.RADIO_TRANSMITTER.get());
                        output.accept(ModItems.RADIO_RECEIVER.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }
}
