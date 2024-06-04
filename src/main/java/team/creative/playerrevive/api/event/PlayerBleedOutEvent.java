package team.creative.playerrevive.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import team.creative.playerrevive.api.IBleeding;

/**
 * Fired before a player is killed
 */
public record PlayerBleedOutEvent(Player player, IBleeding bleeding) {
    public static final Event<PlayerBleedOutCallback> EVENT = EventFactory.createArrayBacked(PlayerBleedOutCallback.class, events -> event -> {
        for (PlayerBleedOutCallback callback : events) {
            callback.onBleedOut(event);
        }
    });

    @FunctionalInterface
    public static interface PlayerBleedOutCallback {
        void onBleedOut(PlayerBleedOutEvent event);
    }
}
