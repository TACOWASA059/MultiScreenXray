package com.github.tacowasa059.multiscreenxray.forge;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("multiscreenxray")
public final class MultiScreenXrayForge {
    public MultiScreenXrayForge() {
        RegisterKeyMappingsEvent.BUS.addListener(this::registerKeyMappings);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT);
        event.register(MultiScreenXrayKeyMappings.OPEN_SETTINGS);
    }
}
