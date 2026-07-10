package com.realradio.common.registry;

import com.realradio.RealRadio;
import com.realradio.common.blockentity.RadioReceiverBlockEntity;
import com.realradio.common.blockentity.RadioRelayBlockEntity;
import com.realradio.common.blockentity.RadioTransmitterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RealRadio.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER =
            REGISTER.register("radio_transmitter", () ->
                    BlockEntityType.Builder.of(RadioTransmitterBlockEntity::new, ModBlocks.RADIO_TRANSMITTER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioReceiverBlockEntity>> RADIO_RECEIVER =
            REGISTER.register("radio_receiver", () ->
                    BlockEntityType.Builder.of(RadioReceiverBlockEntity::new, ModBlocks.RADIO_RECEIVER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioRelayBlockEntity>> RADIO_RELAY =
            REGISTER.register("radio_relay", () ->
                    BlockEntityType.Builder.of(RadioRelayBlockEntity::new, ModBlocks.RADIO_RELAY.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
