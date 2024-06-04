package team.creative.playerrevive.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.fabricators_of_create.porting_lib.entity.events.PlayerTickEvents;
import io.github.fabricators_of_create.porting_lib.event.client.InteractEvents;
import io.github.fabricators_of_create.porting_lib.event.client.OverlayRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.mixin.GameRendererAccessor;
import team.creative.playerrevive.mixin.KeyMappingAccessor;
import team.creative.playerrevive.mixin.LocalPlayerAccessor;
import team.creative.playerrevive.mixin.MinecraftAccessor;
import team.creative.playerrevive.packet.GiveUpPacket;
import team.creative.playerrevive.server.PlayerReviveServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(value = Dist.CLIENT)
public class ReviveEventClient {
    
    public static Minecraft mc = Minecraft.getInstance();
    
    public static TensionSound sound;
    
    public static UUID helpTarget;
    public static boolean helpActive = false;
    
    public static void render(GuiGraphics graphics, List<Component> list) {
        int space = 15;
        int width = 0;
        for (int i = 0; i < list.size(); i++) {
            String text = list.get(i).getString();
            width = Math.max(width, mc.font.width(text) + 10);
        }
        
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        for (int i = 0; i < list.size(); i++) {
            String text = list.get(i).getString();
            graphics.drawString(mc.font, text, mc.getWindow().getGuiScaledWidth() / 2 - mc.font.width(text) / 2, mc.getWindow().getGuiScaledHeight() / 2 + ((list
                    .size() / 2) * space - space * (i + 1)), 16579836);
        }
        RenderSystem.enableDepthTest();
    }
    
    public boolean lastShader = false;
    public boolean lastHighTension = false;
    
    private boolean addedEffect = false;
    private int giveUpTimer = 0;

    public ReviveEventClient() {
        PlayerTickEvents.END.register(player -> {
            if (!player.level().isClientSide())
                return;

            playerTick(player);
        });

        InteractEvents.USE.register((mc1, hit, hand) -> {
            if (click())
                return InteractionResult.FAIL;

            return InteractionResult.PASS;
        });

        InteractEvents.PICK.register((mc1, hit) -> {
            return click();
        });

        InteractEvents.ATTACK.register((mc1, hit) -> {
            if (click())
                return InteractionResult.FAIL;

            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTick();
        });

        OverlayRenderCallback.EVENT.register((guiGraphics, partialTicks, window, type) -> {
            tick(guiGraphics);

            return false;
        });
    }

    public void playerTick(Player player) {
        IBleeding revive = PlayerReviveServer.getBleeding(player);
        if (revive.isBleeding())
            player.setPose(Pose.SWIMMING);
    }

    public boolean click() {
        Player player = mc.player;
        if (player != null) {
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            if (revive.isBleeding())
                return true;
        }

        return false;
    }

    public void clientTick() {
        //if (event.phase == Phase.END) {
            Player player = mc.player;
            if (player != null) {
                IBleeding revive = PlayerReviveServer.getBleeding(player);
                
                if (revive.isBleeding())
                    if (mc.options.keyAttack.isDown())
                        if (giveUpTimer > PlayerRevive.CONFIG.bleeding.giveUpSeconds * 20) {
                            PlayerRevive.NETWORK.sendToServer(new GiveUpPacket());
                            giveUpTimer = 0;
                        } else
                            giveUpTimer++;
                    else
                        giveUpTimer = 0;
                else
                    giveUpTimer = 0;
            }
        //}
    }

    public void tick(GuiGraphics guiGraphics) {
        Player player = mc.player;
        if (player != null) {
            IBleeding revive = PlayerReviveServer.getBleeding(player);
            
            if (!revive.isBleeding()) {
                lastHighTension = false;
                if (lastShader) {
                    mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
                    lastShader = false;
                }
                
                if (addedEffect) {
                    player.removeEffect(MobEffects.JUMP);
                    ((LocalPlayerAccessor) player).setHandsBusy(false);
                    addedEffect = false;
                }
                
                if (sound != null) {
                    mc.getSoundManager().stop(sound);
                    sound = null;
                }
                
                if (helpActive && !mc.options.hideGui && mc.screen == null) {
                    Player other = player.level().getPlayerByUUID(helpTarget);
                    if (other != null) {
                        List<Component> list = new ArrayList<>();
                        IBleeding bleeding = PlayerReviveServer.getBleeding(other);
                        list.add(Component.translatable("playerrevive.gui.label.time_left", formatTime(bleeding.timeLeft())));
                        list.add(Component.literal("" + bleeding.getProgress() + "/" + PlayerRevive.CONFIG.revive.requiredReviveProgress));
                        render(guiGraphics, list);
                    }
                }
            } else {
                player.setPose(Pose.SWIMMING);
                ((LocalPlayerAccessor) player).setHandsBusy(true);
                ((MinecraftAccessor) mc).setMissTime(2);
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 0, -10));
                
                player.hurtTime = 0;
                addedEffect = true;
                
                if (revive.timeLeft() < 400) {
                    if (!lastHighTension) {
                        if (!PlayerRevive.CONFIG.disableMusic) {
                            mc.getSoundManager().stop(sound);
                            sound = new TensionSound(new ResourceLocation(PlayerRevive.MODID, "hightension"), PlayerRevive.CONFIG.countdownMusicVolume, 1.0F, false);
                            mc.getSoundManager().play(sound);
                        }
                        lastHighTension = true;
                        
                    }
                } else {
                    if (!lastShader) {
                        if (sound != null)
                            mc.getSoundManager().stop(sound);
                        if (!PlayerRevive.CONFIG.disableMusic) {
                            sound = new TensionSound(new ResourceLocation(PlayerRevive.MODID, "tension"), PlayerRevive.CONFIG.bleedingMusicVolume, 1.0F, true);
                            mc.getSoundManager().play(sound);
                        }
                    }
                }
                
                if (!lastShader) {
                    if (PlayerRevive.CONFIG.bleeding.hasShaderEffect)
                        ((GameRendererAccessor) mc.gameRenderer).callLoadEffect(new ResourceLocation("shaders/post/blobs2.json"));
                    lastShader = true;
                } else if (PlayerRevive.CONFIG.bleeding.hasShaderEffect && (mc.gameRenderer.currentEffect() == null || !mc.gameRenderer.currentEffect().getName().equals(
                    "minecraft:shaders/post/blobs2.json"))) {
                    ((GameRendererAccessor) mc.gameRenderer).callLoadEffect(new ResourceLocation("shaders/post/blobs2.json"));
                }
                
                if (!mc.options.hideGui && (mc.screen == null || mc.screen instanceof ChatScreen)) {
                    List<Component> list = new ArrayList<>();
                    IBleeding bleeding = PlayerReviveServer.getBleeding(player);
                    list.add(Component.translatable("playerrevive.gui.label.time_left", formatTime(bleeding.timeLeft())));
                    list.add(Component.literal("" + bleeding.getProgress() + "/" + PlayerRevive.CONFIG.revive.requiredReviveProgress));
                    list.add(Component.translatable("playerrevive.gui.hold", ((KeyMappingAccessor) mc.options.keyAttack).getKey().getDisplayName(),
                        ((PlayerRevive.CONFIG.bleeding.giveUpSeconds * 20 - giveUpTimer) / 20) + 1));
                    render(guiGraphics, list);
                }
            }
            
        }
    }
    
    public String formatTime(int timeLeft) {
        int lengthOfMinute = 20 * 60;
        int lengthOfHour = lengthOfMinute * 60;
        
        int hours = timeLeft / lengthOfHour;
        timeLeft -= hours * lengthOfHour;
        
        int minutes = timeLeft / lengthOfMinute;
        timeLeft -= minutes * lengthOfMinute;
        
        int seconds = timeLeft / 20;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
}
