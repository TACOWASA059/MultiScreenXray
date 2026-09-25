package com.github.tacowasa059.multiscreenxray.fabric;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class MultiScreenXrayFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT);
        KeyMappingHelper.registerKeyMapping(MultiScreenXrayKeyMappings.OPEN_SETTINGS);
    }
}
