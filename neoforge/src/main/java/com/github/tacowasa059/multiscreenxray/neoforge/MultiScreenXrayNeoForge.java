package com.github.tacowasa059.multiscreenxray.neoforge;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod("multiscreenxray")
public final class MultiScreenXrayNeoForge {
    public MultiScreenXrayNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerKeyMappings);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT);
        event.register(MultiScreenXrayKeyMappings.OPEN_SETTINGS);
    }
}
