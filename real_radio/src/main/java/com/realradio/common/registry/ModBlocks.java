package com.realradio.common.registry;

import com.realradio.RealRadio;
import com.realradio.common.block.RadioReceiverBlock;
import com.realradio.common.block.RadioTransmitterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(RealRadio.MOD_ID);

    public static final DeferredBlock<RadioTransmitterBlock> RADIO_TRANSMITTER = REGISTER.register(
            "radio_transmitter",
            () -> new RadioTransmitterBlock(baseProps(MapColor.METAL))
    );

    public static final DeferredBlock<RadioReceiverBlock> RADIO_RECEIVER = REGISTER.register(
            "radio_receiver",
            () -> new RadioReceiverBlock(baseProps(MapColor.WOOD))
    );

    private static BlockBehaviour.Properties baseProps(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    private ModBlocks() {
    }
}
