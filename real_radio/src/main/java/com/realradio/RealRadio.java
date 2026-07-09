package com.realradio;

import com.realradio.client.ClientSetup;
import com.realradio.common.registry.ModBlockEntities;
import com.realradio.common.registry.ModBlocks;
import com.realradio.common.registry.ModCreativeTabs;
import com.realradio.common.registry.ModItems;
import com.realradio.common.registry.ModMenus;
import com.realradio.common.registry.ModSounds;
import com.realradio.integration.plasmovoice.PlasmoVoiceBootstrap;
import com.realradio.network.ModNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RealRadio.MOD_ID)
public final class RealRadio {
    public static final String MOD_ID = "real_radio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RealRadio(IEventBus modBus, ModContainer container) {
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(ModNetwork::registerPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PlasmoVoiceBootstrap::bootstrap);
        LOGGER.info("Real Radio common setup complete");
    }
}
