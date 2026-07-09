package com.realradio.common.registry;

import com.realradio.RealRadio;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(RealRadio.MOD_ID);

    public static final DeferredItem<BlockItem> RADIO_TRANSMITTER = REGISTER.register(
            "radio_transmitter",
            () -> new BlockItem(ModBlocks.RADIO_TRANSMITTER.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> RADIO_RECEIVER = REGISTER.register(
            "radio_receiver",
            () -> new BlockItem(ModBlocks.RADIO_RECEIVER.get(), new Item.Properties())
    );

    private ModItems() {
    }
}
