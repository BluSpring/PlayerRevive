package team.creative.playerrevive.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.fabric.ForcedPoseEntity;
import team.creative.playerrevive.server.PlayerReviveServer;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements ForcedPoseEntity {
    
    protected PlayerMixin(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }
    
    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/world/entity/player/Player;canBeSeenAsEnemy()Z", cancellable = true)
    public void isBleeding(CallbackInfoReturnable<Boolean> cir) {
        IBleeding bleeding = PlayerReviveServer.getBleeding((Player) (Object) this);
        if (bleeding.isBleeding() && (bleeding.downedTime() <= PlayerRevive.CONFIG.bleeding.initialDamageCooldown || PlayerRevive.CONFIG.bleeding.disableMobDamage))
            cir.setReturnValue(false);
    }
    
    @Override
    public boolean isPushable() {
        if (super.isPushable()) {
            if (PlayerReviveServer.getBleeding((Player) (Object) this).isBleeding() && !PlayerRevive.CONFIG.bleeding.canBePushed)
                return false;
            return true;
        }
        return false;
    }

    @Unique
    private Pose forcedPose;

    @Override
    public Pose playerRevive$getForcedPose() {
        return this.forcedPose;
    }

    @Override
    public void playerRevive$setForcedPose(Pose pose) {
        this.forcedPose = pose;
    }
}
