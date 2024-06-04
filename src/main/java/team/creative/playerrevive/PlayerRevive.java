package team.creative.playerrevive;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.creative.creativecore.common.config.holder.CreativeConfigRegistry;
import team.creative.creativecore.common.network.CreativeNetwork;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.cap.Bleeding;
import team.creative.playerrevive.packet.GiveUpPacket;
import team.creative.playerrevive.packet.HelperPacket;
import team.creative.playerrevive.packet.ReviveUpdatePacket;
import team.creative.playerrevive.server.PlayerReviveServer;
import team.creative.playerrevive.server.ReviveEventServer;

import java.util.Collection;

@Mod(PlayerRevive.MODID)
public class PlayerRevive implements ModInitializer {
    
    public static final Logger LOGGER = LogManager.getLogger(PlayerRevive.MODID);
    public static final String MODID = "playerrevive";
    public static PlayerReviveConfig CONFIG;
    public static final CreativeNetwork NETWORK = new CreativeNetwork(1, LOGGER, new ResourceLocation(PlayerRevive.MODID, "main"));
    
    public static final ResourceLocation BLEEDING_NAME = new ResourceLocation(MODID, "bleeding");
    public static final ResourceKey<DamageType> BLED_TO_DEATH = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "bled_to_death"));
    
    public static final SoundEvent DEATH_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "death"));
    public static final SoundEvent REVIVED_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "revived"));
    
    public static final AttachmentType<IBleeding> BLEEDING = AttachmentRegistry.<IBleeding>builder()
        .persistent(Bleeding.CODEC)
        .initializer(Bleeding::new)
        .buildAndRegister(new ResourceLocation(PlayerRevive.MODID, "bleeding"));
    
    public void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(MODID, "death"), DEATH_SOUND);
        Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(MODID, "revived"), REVIVED_SOUND);
    }

    @Override
    public void onInitialize() {
        init();
        register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverStarting(server);
        });
    }
    
    private void init() {
        NETWORK.registerType(ReviveUpdatePacket.class, ReviveUpdatePacket::new);
        NETWORK.registerType(HelperPacket.class, HelperPacket::new);
        NETWORK.registerType(GiveUpPacket.class, GiveUpPacket::new);
        
        CreativeConfigRegistry.ROOT.registerValue(MODID, CONFIG = new PlayerReviveConfig());
        new ReviveEventServer();
    }
    
    private void serverStarting(MinecraftServer server) {
        server.getCommands().getDispatcher().register(Commands.literal("revive").requires(x -> x.hasPermission(2)).then(Commands.argument("players", EntityArgument
                .players()).executes(x -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(x, "players");
                    for (ServerPlayer player : players)
                        if (PlayerReviveServer.getBleeding(player).isBleeding())
                            PlayerReviveServer.revive(player);
                    return 0;
                })));
    }
}
