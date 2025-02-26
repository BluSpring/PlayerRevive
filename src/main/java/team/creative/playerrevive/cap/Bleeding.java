package team.creative.playerrevive.cap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.CombatTrackerClone;
import team.creative.playerrevive.api.IBleeding;
import team.creative.playerrevive.fabric.ForcedPoseEntity;
import team.creative.playerrevive.packet.HelperPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Bleeding implements IBleeding {
    public static final Codec<IBleeding> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("bleeding")
                .stable()
                .forGetter(IBleeding::isBleeding),
            Codec.FLOAT.fieldOf("progress")
                .stable()
                .forGetter(IBleeding::getProgress),
            Codec.INT.fieldOf("timeLeft")
                .stable()
                .forGetter(IBleeding::timeLeft),
            Codec.INT.fieldOf("downedTime")
                .stable()
                .forGetter(IBleeding::downedTime)
        ).apply(instance, instance.stable(Bleeding::new))
    );

    private boolean bleeding;
    private float progress;
    private int timeLeft;
    private int downedTime;
    
    private DamageSource lastSource;
    private CombatTrackerClone trackerClone;
    private boolean itemConsumed = false;
    
    public final List<Player> revivingPlayers = new ArrayList<>();
    
    public Bleeding() {}
    public Bleeding(boolean bleeding, float progress, int timeLeft, int downedTime) {
        this.bleeding = bleeding;
        this.progress = progress;
        this.timeLeft = timeLeft;
        this.downedTime = downedTime;
    }
    
    @Override
    public void tick(Player player) {
        if (player.getPose() != Pose.SWIMMING)
            ((ForcedPoseEntity) player).playerRevive$setForcedPose(Pose.SWIMMING);
        for (Iterator<Player> iterator = revivingPlayers.iterator(); iterator.hasNext();) {
            Player helper = iterator.next();
            if (helper.distanceTo(player) > PlayerRevive.CONFIG.revive.maxDistance) {
                PlayerRevive.NETWORK.sendToClient(new HelperPacket(null, false), (ServerPlayer) helper);
                iterator.remove();
            }
        }
        //player.setPose(Pose.SWIMMING);
        if (revivingPlayers.isEmpty() || !PlayerRevive.CONFIG.revive.haltBleedTime)
            timeLeft--;
        if (revivingPlayers.isEmpty() && PlayerRevive.CONFIG.revive.resetProgress)
            progress = 0;
        
        progress += revivingPlayers.size() * PlayerRevive.CONFIG.revive.progressPerPlayer;
        downedTime++;
        
        if (PlayerRevive.CONFIG.revive.exhaustion > 0)
            for (int i = 0; i < revivingPlayers.size(); i++)
                revivingPlayers.get(i).causeFoodExhaustion(PlayerRevive.CONFIG.revive.exhaustion);
    }
    
    @Override
    public void forceBledOut() {
        bleeding = true;
        timeLeft = 0;
    }
    
    @Override
    public int downedTime() {
        return downedTime;
    }
    
    @Override
    public float getProgress() {
        return progress;
    }
    
    @Override
    public boolean revived() {
        return progress >= PlayerRevive.CONFIG.revive.requiredReviveProgress;
    }
    
    @Override
    public boolean bledOut() {
        return bleeding && timeLeft <= 0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("timeLeft", timeLeft);
        nbt.putFloat("progress", progress);
        nbt.putBoolean("bleeding", bleeding);
        nbt.putBoolean("consumed", itemConsumed);
        return nbt;
    }
    
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        timeLeft = nbt.getInt("timeLeft");
        progress = nbt.getFloat("progress");
        bleeding = nbt.getBoolean("bleeding");
        itemConsumed = nbt.getBoolean("consumed");
    }
    
    @Override
    public boolean isBleeding() {
        return bleeding;
    }
    
    @Override
    public void knockOut(Player player, DamageSource source) {
        this.bleeding = true;
        this.progress = 0;
        this.downedTime = 0;
        this.timeLeft = PlayerRevive.CONFIG.bleeding.bleedTime;
        this.lastSource = source;
        this.trackerClone = new CombatTrackerClone(player.getCombatTracker());
    }
    
    @Override
    public void revive() {
        this.bleeding = false;
        this.progress = 0;
        this.timeLeft = 0;
        this.downedTime = 0;
        this.lastSource = null;
        this.trackerClone = null;
        this.itemConsumed = false;
    }
    
    @Override
    public int timeLeft() {
        return timeLeft;
    }
    
    @Override
    public List<Player> revivingPlayers() {
        return revivingPlayers;
    }
    
    @Override
    public CombatTrackerClone getTrackerClone() {
        return trackerClone;
    }
    
    @Override
    public DamageSource getSource(RegistryAccess access) {
        if (lastSource != null)
            return lastSource;
        return new DamageSource(access.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PlayerRevive.BLED_TO_DEATH));
    }
    
    @Override
    public boolean isItemConsumed() {
        return itemConsumed;
    }
    
    @Override
    public void setItemConsumed() {
        itemConsumed = true;
    }
    
}
