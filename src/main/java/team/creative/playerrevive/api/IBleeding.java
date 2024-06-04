package team.creative.playerrevive.api;

import io.github.fabricators_of_create.porting_lib.core.util.INBTSerializable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface IBleeding extends INBTSerializable<CompoundTag> {
    
    public void tick(Player player);
    
    public float getProgress();
    
    public boolean isBleeding();
    
    public boolean bledOut();
    
    public void forceBledOut();
    
    public void knockOut(Player player, DamageSource source);
    
    public boolean revived();
    
    public void revive();
    
    public int timeLeft();
    
    public int downedTime();
    
    public List<Player> revivingPlayers();
    
    public DamageSource getSource(RegistryAccess access);
    
    public CombatTrackerClone getTrackerClone();
    
    public boolean isItemConsumed();
    
    public void setItemConsumed();
    
}
