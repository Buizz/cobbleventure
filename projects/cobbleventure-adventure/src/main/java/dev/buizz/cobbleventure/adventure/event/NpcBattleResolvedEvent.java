package dev.buizz.cobbleventure.adventure.event;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Server-authoritative CVES battle outcome, including the exact opponent instance. */
public final class NpcBattleResolvedEvent extends Event {
    private final ServerPlayer player;
    private final UUID npc;
    private final String outcome;

    public NpcBattleResolvedEvent(ServerPlayer player, UUID npc, String outcome) {
        this.player = player;
        this.npc = npc;
        this.outcome = outcome;
    }

    public ServerPlayer player() { return player; }
    public UUID npc() { return npc; }
    public String outcome() { return outcome; }
}
