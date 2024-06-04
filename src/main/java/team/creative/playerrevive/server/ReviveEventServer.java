package team.creative.playerrevive.server;

import io.github.fabricators_of_create.porting_lib.entity.events.LivingDeathEvent;
import io.github.fabricators_of_create.porting_lib.entity.events.PlayerTickEvents;
import io.github.fabricators_of_create.porting_lib.entity.events.living.LivingHurtEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import team.creative.creativecore.common.config.premade.MobEffectConfig;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.packet.HelperPacket;

public class ReviveEventServer {
    
    public static boolean isReviveActive(Entity player) {
        if (player instanceof Player p && p.isCreative() && !PlayerRevive.CONFIG.bleeding.triggerForCreative)
            return false;
        return PlayerRevive.CONFIG.bleedInSingleplayer || player.getServer().isPublished();
    }

    private static final ResourceLocation HIGHEST = new ResourceLocation(PlayerRevive.MODID, "high_priority");

    public ReviveEventServer() {
        PlayerTickEvents.START.register(player -> {
            playerTick(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(HIGHEST, Event.DEFAULT_PHASE);
        ServerPlayConnectionEvents.DISCONNECT.register(HIGHEST, (handler, server) -> {
            playerLeave(handler.getPlayer());
        });


        UseEntityCallback.EVENT.addPhaseOrdering(HIGHEST, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(HIGHEST, (player, level, hand, target, hitResult) -> {
            return playerInteract(player, target);
        });

        LivingHurtEvent.HURT.register(event -> {
            playerDamage(event);
        });

        LivingDeathEvent.DEATH.addPhaseOrdering(HIGHEST, Event.DEFAULT_PHASE);
        LivingDeathEvent.DEATH.register(HIGHEST, event -> {
            playerDied(event);
        });
    }

    public void playerTick(Player player) {
        if (!player.level().isClientSide() && isReviveActive(player)) {
            if (!player.isAlive())
                return;
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            
            if (revive.isBleeding()) {
                revive.tick(player);
                
                if (revive.downedTime() % 5 == 0)
                    PlayerReviveServer.sendUpdatePacket(player);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                
                for (MobEffectConfig effect : PlayerRevive.CONFIG.bleeding.bleedingEffects)
                    player.addEffect(effect.create());
                
                if (PlayerRevive.CONFIG.bleeding.shouldGlow)
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10));
                
                if (revive.revived())
                    PlayerReviveServer.revive(player);
                else if (revive.bledOut())
                    PlayerReviveServer.kill(player);
            }
        }
    }

    public void playerLeave(Player player) {
        IBleeding revive = PlayerReviveServer.getBleeding(player);
        if (revive.isBleeding())
            PlayerReviveServer.kill(player);
        if (!player.level().isClientSide)
            PlayerReviveServer.removePlayerAsHelper(player);
    }

    public InteractionResult playerInteract(Player player, Entity entityTarget) {
        if (entityTarget instanceof Player target && !player.level().isClientSide) {
            Player helper = player;
            IBleeding revive = PlayerReviveServer.getBleeding(target);
            if (revive.isBleeding()) {
                if (PlayerRevive.CONFIG.revive.needReviveItem) {
                    if (PlayerRevive.CONFIG.revive.consumeReviveItem && !revive.isItemConsumed()) {
                        if (PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem())) {
                            if (!helper.isCreative()) {
                                helper.getMainHandItem().shrink(1);
                                helper.getInventory().setChanged();
                            }
                            revive.setItemConsumed();
                        } else {
                            if (!helper.level().isClientSide)
                                helper.sendSystemMessage(Component.translatable("playerrevive.revive.item").append(PlayerRevive.CONFIG.revive.reviveItem.description()));
                            return InteractionResult.FAIL;
                        }
                    } else if (!PlayerRevive.CONFIG.revive.reviveItem.is(helper.getMainHandItem()))
                        return InteractionResult.FAIL;
                }
                
                PlayerReviveServer.removePlayerAsHelper(helper);
                revive.revivingPlayers().add(helper);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(target.getUUID(), true), (ServerPlayer) helper);

                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }

    public void playerDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            if (revive.isBleeding()) {
                if (event.getSource().type() == player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getOrThrow(
                    PlayerRevive.BLED_TO_DEATH) || PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().getMsgId()))
                    return;
                
                if (revive.bledOut())
                    event.setCanceled(true);
                
                if (revive.downedTime() <= PlayerRevive.CONFIG.bleeding.initialDamageCooldown)
                    event.setCanceled(true);
                
                if (event.getSource().getEntity() instanceof Player) {
                    if (PlayerRevive.CONFIG.bleeding.disablePlayerDamage)
                        event.setCanceled(true);
                } else if (event.getSource().getEntity() instanceof LivingEntity) {
                    if (PlayerRevive.CONFIG.bleeding.disableMobDamage)
                        event.setCanceled(true);
                } else if (PlayerRevive.CONFIG.bleeding.disableOtherDamage)
                    event.setCanceled(true);
                
            } else if (PlayerRevive.CONFIG.revive.abortOnDamage)
                PlayerReviveServer.removePlayerAsHelper(player);
        }
    }

    public void playerDied(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && isReviveActive(event.getEntity()) && !event.getEntity().level().isClientSide) {
            if (event.getSource().type() != player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getOrThrow(
                PlayerRevive.BLED_TO_DEATH) && !PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().getMsgId())) {
                IBleeding revive = PlayerReviveServer.getBleeding(player);
                
                if (revive.bledOut() || revive.isBleeding()) {
                    if (revive.isBleeding())
                        PlayerRevive.CONFIG.sounds.death.play(player, SoundSource.PLAYERS);
                    for (Player helper : revive.revivingPlayers())
                        PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) helper);
                    revive.revivingPlayers().clear();
                    return;
                }
                
                PlayerReviveServer.removePlayerAsHelper(player);
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) player);
                
                PlayerReviveServer.startBleeding(player, event.getSource());
                
                if (player.isPassenger())
                    player.stopRiding();
                
                event.setCanceled(true);
                
                if (PlayerRevive.CONFIG.bleeding.affectHunger)
                    player.getFoodData().setFoodLevel(PlayerRevive.CONFIG.bleeding.remainingHunger);
                player.setHealth(PlayerRevive.CONFIG.bleeding.bleedingHealth);
                
                if (PlayerRevive.CONFIG.bleeding.bleedingMessage)
                    if (PlayerRevive.CONFIG.bleeding.bleedingMessageTrackingOnly)
                        player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("playerrevive.chat.bleeding", player.getDisplayName(), player
                                .getCombatTracker().getDeathMessage()), false);
                    else
                        player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("playerrevive.chat.bleeding", player.getDisplayName(), player
                                .getCombatTracker().getDeathMessage()), false);
            }
        }
    }
    
    /*@SubscribeEvent
    public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player)
            event.addCapability(PlayerRevive.BLEEDING_NAME, new ICapabilityProvider() {
                
                private LazyOptional<IBleeding> bleed = LazyOptional.of(Bleeding::new);
                
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return PlayerRevive.BLEEDING.orEmpty(cap, bleed);
                }
            });
    }*/
    
}
