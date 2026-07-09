package com.realradio.common.registry;

import com.realradio.RealRadio;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, RealRadio.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_STATIC = REGISTER.register(
            "radio_static",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(RealRadio.MOD_ID, "radio_static"))
    );

    private ModSounds() {
    }
}
