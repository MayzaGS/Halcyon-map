package dev.mayzags.halcyonmap;

import net.dimaskama.mcef.api.MCEFApi;
import net.fabricmc.api.ClientModInitializer;

public class HalcyonMapClient implements ClientModInitializer {

    public static final String MOD_ID = "halcyon_map";

    @Override
    public void onInitializeClient() {
        MapConfig.load();
        MCEFApi.initialize();
    }

}
