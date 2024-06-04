package team.creative.playerrevive.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import team.creative.playerrevive.api.IBleeding;

/** Fired before a player is revived. */
public record PlayerRevivedEvent(Player player, IBleeding bleeding) {
    public static final Event<PlayerRevivedCallback> EVENT = EventFactory.createArrayBacked(PlayerRevivedCallback.class, events -> event -> {
        for (PlayerRevivedCallback callback : events) {
            callback.onRevive(event);
        }
    });

    @FunctionalInterface
    public static interface PlayerRevivedCallback {
        void onRevive(PlayerRevivedEvent event);
    }
}
