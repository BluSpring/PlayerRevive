package team.creative.playerrevive.server;

import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.DamageBledToDeath;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.cap.Bleeding;
import team.creative.playerrevive.packet.HelperPacket;

public class ReviveEventServer {
    
    public static boolean isReviveActive(Entity player) {
        return player.getServer().isPublished();
    }
    
    @SubscribeEvent
    public void playerTick(PlayerTickEvent event) {
        if (event.phase == Phase.START && event.side == LogicalSide.SERVER && isReviveActive(event.player)) {
            Player player = event.player;
            if (!player.isAlive())
                return;
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            
            if (revive.isBleeding()) {
                revive.tick(player);
                
                if (revive.downedTime() % 5 == 0)
                    PlayerReviveServer.sendUpdatePacket(player);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                player.setHealth(PlayerRevive.CONFIG.bleeding.remainingHealth);
                player.getAbilities().invulnerable = true;
                player.setInvulnerable(true);
                
                if (PlayerRevive.CONFIG.bleeding.hasBleedingMobEffect)
                    player.addEffect(PlayerRevive.CONFIG.bleeding.bleedingMobEffect.create());
                
                if (revive.revived())
                    PlayerReviveServer.revive(player);
                else if (revive.bledOut())
                    PlayerReviveServer.kill(player);
            }
        }
    }
    
    @SubscribeEvent
    public void playerLeave(PlayerLoggedOutEvent event) {
        IBleeding revive = PlayerReviveServer.getBleeding(event.getPlayer());
        if (revive.isBleeding())
            PlayerReviveServer.kill(event.getPlayer());
        if (!event.getPlayer().level.isClientSide)
            PlayerReviveServer.removePlayerAsHelper(event.getPlayer());
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void playerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Player && !event.getEntityLiving().level.isClientSide) {
            Player target = (Player) event.getTarget();
            Player helper = event.getPlayer();
            IBleeding revive = PlayerReviveServer.getBleeding(target);
            if (revive.isBleeding()) {
                event.setCanceled(true);
                if (PlayerRevive.CONFIG.revive.needReviveItem) {
                    if (PlayerRevive.CONFIG.revive.consumeReviveItem && !revive.isItemConsumed()) {
                        if (!PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem())) {
                            if (!helper.isCreative())
                                helper.getMainHandItem().shrink(1);
                            revive.setItemConsumed();
                        } else {
                            helper.sendMessage(new TranslatableComponent("playerrevive.revive.item").append(PlayerRevive.CONFIG.revive.reviveItem.description()), Util.NIL_UUID);
                            return;
                        }
                    } else if (!PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem()))
                        return;
                }
                
                PlayerReviveServer.removePlayerAsHelper(helper);
                revive.revivingPlayers().add(helper);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(target.getUUID(), true), (ServerPlayer) helper);
            }
        }
    }
    
    @SubscribeEvent
    public void playerDamage(LivingHurtEvent event) {
        if (event.getEntityLiving() instanceof Player) {
            Player player = (Player) event.getEntityLiving();
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            if (revive
                    .isBleeding() && ((event.getSource() != DamageBledToDeath.BLED_TO_DEATH && !PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().msgId)) || revive
                            .bledOut()))
                event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerDied(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof Player player && isReviveActive(event.getEntityLiving()) && !event.getEntityLiving().level.isClientSide) {
            if (event.getSource() != DamageBledToDeath.BLED_TO_DEATH && !PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().msgId)) {
                IBleeding revive = PlayerReviveServer.getBleeding(player);
                
                if (revive.bledOut())
                    return;
                
                PlayerReviveServer.removePlayerAsHelper(player);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) player);
                
                PlayerReviveServer.startBleeding(player, event.getSource());
                player.getAbilities().invulnerable = true;
                player.setInvulnerable(true);
                
                if (player.isPassenger())
                    player.stopRiding();
                
                event.setCanceled(true);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                player.setHealth(PlayerRevive.CONFIG.bleeding.remainingHealth);
                
                if (PlayerRevive.CONFIG.bleeding.bleedingMessage)
                    if (PlayerRevive.CONFIG.bleeding.bleedingMessageTrackingOnly)
                        player.getServer().getPlayerList()
                                .broadcastMessage(new TranslatableComponent("playerrevive.chat.bleeding", player.getDisplayName()), ChatType.SYSTEM, player.getUUID());
                    else
                        player.getServer().getPlayerList()
                                .broadcastMessage(new TranslatableComponent("playerrevive.chat.bleeding", player.getDisplayName()), ChatType.SYSTEM, Util.NIL_UUID);
            } else if (PlayerRevive.CONFIG.revive.abortOnDamage)
                PlayerReviveServer.removePlayerAsHelper(player);
        }
    }
    
    @SubscribeEvent
    public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player)
            event.addCapability(PlayerRevive.BLEEDING_NAME, new ICapabilityProvider() {
                
                private LazyOptional<IBleeding> bleed = LazyOptional.of(Bleeding::new);
                
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return PlayerRevive.BLEEDING.orEmpty(cap, bleed);
                }
            });
    }
    
}
