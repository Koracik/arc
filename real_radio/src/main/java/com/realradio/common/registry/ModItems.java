package com.realradio.common.registry;

import com.realradio.RealRadio;
import com.realradio.common.item.CoverageMapperItem;
import com.realradio.common.item.HandheldRadioItem;
import com.realradio.common.item.RadioTapeItem;
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

    public static final DeferredItem<BlockItem> RADIO_RELAY = REGISTER.register(
            "radio_relay",
            () -> new BlockItem(ModBlocks.RADIO_RELAY.get(), new Item.Properties())
    );

    public static final DeferredItem<RadioTapeItem> RADIO_TAPE = REGISTER.register(
            "radio_tape",
            () -> new RadioTapeItem(new Item.Properties())
    );

    public static final DeferredItem<HandheldRadioItem> HANDHELD_RADIO = REGISTER.register(
            "handheld_radio",
            () -> new HandheldRadioItem(new Item.Properties())
    );

    public static final DeferredItem<CoverageMapperItem> COVERAGE_MAPPER = REGISTER.register(
            "coverage_mapper",
            () -> new CoverageMapperItem(new Item.Properties())
    );

    private ModItems() {
    }
}
