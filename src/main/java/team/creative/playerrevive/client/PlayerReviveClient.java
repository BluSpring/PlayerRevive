package team.creative.playerrevive.client;

import net.fabricmc.api.ClientModInitializer;
import team.creative.creativecore.client.CreativeCoreClient;
import team.creative.playerrevive.PlayerRevive;

public class PlayerReviveClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CreativeCoreClient.registerClientConfig(PlayerRevive.MODID);
        new ReviveEventClient();
    }
}
