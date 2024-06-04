package team.creative.playerrevive.fabric;

import net.minecraft.world.entity.Pose;

public interface ForcedPoseEntity {
    Pose playerRevive$getForcedPose();
    void playerRevive$setForcedPose(Pose pose);
}