package com.github.tacowasa059.multiscreenxray.forge;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("multiscreenxray")
public final class MultiScreenXrayForge {
    public MultiScreenXrayForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::registerKeyMappings);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT);
        event.register(MultiScreenXrayKeyMappings.OPEN_SETTINGS);
    }
}
