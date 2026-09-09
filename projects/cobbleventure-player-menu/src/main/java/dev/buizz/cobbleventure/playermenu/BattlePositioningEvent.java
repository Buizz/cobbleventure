package dev.buizz.cobbleventure.playermenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Positions authored arenas before the intro captures and freezes participants. */
public final class BattlePositioningEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final Entity opponent;

    public BattlePositioningEvent(ServerPlayer player, Entity opponent) {
        this.player = player;
        this.opponent = opponent;
    }

    public ServerPlayer player() { return player; }
    public Entity opponent() { return opponent; }
}
