package dev.buizz.cobbleventure.playermenu;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Projects authored league victory flags into persistent trainer-card achievements. */
final class AuthoredLeagueProgress {
    private static final Map<String, PlayerConditions.Condition> CHALLENGES = new LinkedHashMap<>();

    private AuthoredLeagueProgress() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(AuthoredLeagueProgress::load);
        NeoForge.EVENT_BUS.addListener(AuthoredLeagueProgress::tick);
        NeoForge.EVENT_BUS.addListener(AuthoredLeagueProgress::stop);
    }

    private static void load(ServerStartedEvent event) {
        CHALLENGES.clear();
        var resource = event.getServer().getResourceManager().getResource(ResourceLocation.parse(
            "cobbleventure_player_menu:league/league-progression.json"));
        if (resource.isEmpty()) return;
        try (var reader = resource.get().openAsReader()) {
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries")) {
                var entry = element.getAsJsonObject();
                if (entry.get("role").getAsString().equals("gym_leader")) continue;
                String id = entry.get("id").getAsString();
                var condition = new com.google.gson.JsonObject();
                condition.addProperty("type", "flag");
                condition.addProperty("key", id.replace(":league/", ":flag/league/") + "/defeated");
                condition.addProperty("value", true);
                CHALLENGES.put(id, PlayerConditions.parse(condition));
            }
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load authored league progression", error);
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0 || CHALLENGES.isEmpty()) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            var completed = new LinkedHashSet<String>();
            CHALLENGES.forEach((id, condition) -> { if (condition.matches(player)) completed.add(id); });
            BadgeProgressNetwork.completeAuthoredChallenges(player, completed);
        }
    }

    private static void stop(ServerStoppedEvent event) { CHALLENGES.clear(); }
}
