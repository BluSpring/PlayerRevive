package team.creative.playerrevive.server;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import team.creative.creativecore.common.config.premade.MobEffectConfig;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.CombatTrackerClone;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.api.event.PlayerBleedOutEvent;
import team.creative.playerrevive.api.event.PlayerRevivedEvent;
import team.creative.playerrevive.cap.Bleeding;
import team.creative.playerrevive.fabric.ForcedPoseEntity;
import team.creative.playerrevive.packet.HelperPacket;
import team.creative.playerrevive.packet.ReviveUpdatePacket;

import java.io.IOException;
import java.util.Iterator;

public class PlayerReviveServer {
    
    public static boolean isBleeding(Player player) {
        return getBleeding(player).isBleeding();
    }
    
    public static int timeLeft(Player player) {
        return getBleeding(player).timeLeft();
    }
    
    public static int downedTime(Player player) {
        return getBleeding(player).downedTime();
    }
    
    public static IBleeding getBleeding(Player player) {
        return player.getAttachedOrCreate(PlayerRevive.BLEEDING, Bleeding::new);
    }
    
    public static void sendUpdatePacket(Player player) {
        ReviveUpdatePacket packet = new ReviveUpdatePacket(player);
        for (ServerPlayer serverPlayer : PlayerLookup.tracking(player)) {
            PlayerRevive.NETWORK.sendToClient(packet, serverPlayer);
        }
        PlayerRevive.NETWORK.sendToClient(packet, (ServerPlayer) player);
    }
    
    public static void startBleeding(Player player, DamageSource source) {
        getBleeding(player).knockOut(player, source);
        player.getCustomData().putBoolean("playerrevive:bleeding", true);
        sendUpdatePacket(player);
    }
    
    private static void resetPlayer(Player player, IBleeding revive) {
        for (Player helper : revive.revivingPlayers())
            PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) helper);
        revive.revivingPlayers().clear();
        
        player.getCustomData().remove("playerrevive:bleeding");
        sendUpdatePacket(player);
    }
    
    public static void revive(Player player) {
        IBleeding revive = getBleeding(player);
        revive.revive();
        
        for (MobEffectConfig effect : PlayerRevive.CONFIG.revive.revivedEffects)
            player.addEffect(effect.create());
        
        resetPlayer(player, revive);
        player.setHealth(PlayerRevive.CONFIG.revive.healthAfter);
        
        PlayerRevive.CONFIG.sounds.revived.play(player, SoundSource.PLAYERS);

        PlayerRevivedEvent.EVENT.invoker().onRevive(new PlayerRevivedEvent(player, revive));
        
        sendUpdatePacket(player);
        
        ((ForcedPoseEntity) player).playerRevive$setForcedPose(null);
    }
    
    public static void kill(Player player) {
        IBleeding revive = getBleeding(player);
        PlayerBleedOutEvent.EVENT.invoker().onBleedOut(new PlayerBleedOutEvent(player, revive));
        DamageSource source = revive.getSource(player.level().registryAccess());
        CombatTrackerClone trackerClone = revive.getTrackerClone();
        if (trackerClone != null)
            trackerClone.overwriteTracker(player.getCombatTracker());
        player.setHealth(0.0F);
        revive.forceBledOut();
        player.die(source);
        resetPlayer(player, revive);
        revive.revive(); // Done for compatibility reason for rare scenarios the player will not die
        ((ForcedPoseEntity) player).playerRevive$setForcedPose(null);
        
        PlayerRevive.CONFIG.sounds.death.play(player, SoundSource.PLAYERS);
        
        if (PlayerRevive.CONFIG.banPlayerAfterDeath) {
            try {
                player.getServer().getPlayerList().getBans().add(new UserBanListEntry(player.getGameProfile()));
                player.getServer().getPlayerList().getBans().save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        sendUpdatePacket(player);
    }
    
    public static void removePlayerAsHelper(Player player) {
        for (Iterator<ServerPlayer> iterator = player.getServer().getPlayerList().getPlayers().iterator(); iterator.hasNext();) {
            ServerPlayer member = iterator.next();
            IBleeding revive = getBleeding(member);
            revive.revivingPlayers().remove(player);
        }
        
    }
}
