package com.github.tacowasa059.multiscreenxray.fabric;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public final class MultiScreenXrayFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT);
        KeyBindingHelper.registerKeyBinding(MultiScreenXrayKeyMappings.OPEN_SETTINGS);
    }
}
